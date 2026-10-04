package com.markus.basecamp.logbook.fitness

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import kotlin.math.round

private const val FIELDS = "code,product_name,brands,nutriments"
private const val KJ_PER_KCAL = 4.184

private fun restClient(baseUrl: String): RestClient {
    val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
    factory.setReadTimeout(Duration.ofSeconds(10))
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build()
}

/**
 * Open Food Facts (open data, ODbL): text search through search.openfoodfacts.org and barcode lookup through the
 * main API. No key needed, but they ask API users to identify themselves with a User-Agent.
 */
@Component
class OpenFoodFactsClient(
    @Value("\${basecamp.openfoodfacts.search-url}") searchUrl: String,
    @Value("\${basecamp.openfoodfacts.product-url}") productUrl: String,
    @Value("\${basecamp.openfoodfacts.user-agent}") private val userAgent: String,
) {
    private val searchClient = restClient(searchUrl)
    private val productClient = restClient(productUrl)

    fun search(query: String): List<OnlineFoodResponse> {
        val body = searchClient.get()
            .uri { it.path("/search").queryParam("q", query).queryParam("page_size", 15).queryParam("fields", FIELDS).build() }
            .header("User-Agent", userAgent)
            .retrieve()
            .body(JsonNode::class.java)
        return parseSearchHits(body)
    }

    /** The product, or null when the barcode is unknown or has no usable calorie value. */
    fun byBarcode(code: String): OnlineFoodResponse? =
        try {
            val body = productClient.get()
                .uri { it.path("/api/v2/product/{code}.json").queryParam("fields", FIELDS).build(code) }
                .header("User-Agent", userAgent)
                .retrieve()
                .body(JsonNode::class.java)
            body?.get("product")?.let { parseProduct(it) }
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }
}

/** Pure, so it can be tested without the network. Skips products with no name or no calorie value. */
internal fun parseSearchHits(body: JsonNode?): List<OnlineFoodResponse> =
    body?.get("hits")?.mapNotNull { parseProduct(it) } ?: emptyList()

internal fun parseProduct(product: JsonNode): OnlineFoodResponse? {
    val name = product.get("product_name")?.asText()?.trim().orEmpty()
    if (name.isEmpty()) return null
    val kcal = kcalPer100g(product.get("nutriments")) ?: return null
    return OnlineFoodResponse(
        code = product.get("code")?.asText()?.takeIf { it.isNotBlank() },
        name = name.take(200),
        brand = brandOf(product.get("brands"))?.take(200),
        kcalPer100g = kcal,
    )
}

/** Brands come as an array from the search service and as a comma-separated string from the product API. */
private fun brandOf(node: JsonNode?): String? {
    if (node == null || node.isNull) return null
    val text = if (node.isArray) node.firstOrNull()?.asText() else node.asText()
    return text?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** Prefers the stated kcal; falls back to converting kJ for products that only list that. */
private fun kcalPer100g(nutriments: JsonNode?): Double? {
    val kcal = nutriments?.get("energy-kcal_100g")?.takeIf { it.isNumber }?.asDouble()
    val value = kcal ?: nutriments?.get("energy-kj_100g")?.takeIf { it.isNumber }?.asDouble()?.div(KJ_PER_KCAL)
    return value?.takeIf { it in 0.0..1000.0 }?.let { round(it * 10) / 10 }
}
