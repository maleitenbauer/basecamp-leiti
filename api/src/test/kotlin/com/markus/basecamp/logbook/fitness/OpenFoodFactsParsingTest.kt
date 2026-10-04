package com.markus.basecamp.logbook.fitness

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The Open Food Facts response shapes are copied from real responses of search.openfoodfacts.org and /api/v2/product. */
class OpenFoodFactsParsingTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `search hits are parsed, with brands as an array`() {
        val body = mapper.readTree(
            """
            {"hits": [
              {"code": "8710624358174", "brands": ["Skyr"], "product_name": "Skyr naturel",
               "nutriments": {"energy-kcal_100g": 62, "energy-kj_100g": 260, "proteins_100g": 11}}
            ]}
            """.trimIndent(),
        )

        val hit = parseSearchHits(body).single()

        assertEquals("8710624358174", hit.code)
        assertEquals("Skyr naturel", hit.name)
        assertEquals("Skyr", hit.brand)
        assertEquals(62.0, hit.kcalPer100g)
    }

    @Test
    fun `a product with only kJ is converted, and ones with no name or no energy are skipped`() {
        val body = mapper.readTree(
            """
            {"hits": [
              {"code": "1", "product_name": "Only kJ", "nutriments": {"energy-kj_100g": 418.4}},
              {"code": "2", "product_name": "", "nutriments": {"energy-kcal_100g": 100}},
              {"code": "3", "product_name": "No energy", "nutriments": {"proteins_100g": 5}},
              {"code": "4", "product_name": "Absurd", "nutriments": {"energy-kcal_100g": 99999}}
            ]}
            """.trimIndent(),
        )

        val hits = parseSearchHits(body)

        assertEquals(listOf("Only kJ"), hits.map { it.name })
        assertEquals(100.0, hits.single().kcalPer100g)
    }

    @Test
    fun `a barcode product takes the first of its comma-separated brands`() {
        val product = mapper.readTree(
            """{"code": "3017620422003", "product_name": "Nutella", "brands": "Nutella, Ferrero",
                "nutriments": {"energy-kcal_100g": 539}}""",
        )

        val parsed = parseProduct(product)!!

        assertEquals("Nutella", parsed.brand)
        assertEquals(539.0, parsed.kcalPer100g)
    }

    @Test
    fun `an empty or missing response yields nothing`() {
        assertEquals(emptyList<OnlineFoodResponse>(), parseSearchHits(null))
        assertEquals(emptyList<OnlineFoodResponse>(), parseSearchHits(mapper.readTree("{}")))
        assertNull(parseProduct(mapper.readTree("""{"code": "9"}""")))
    }
}
