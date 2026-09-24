package com.markus.basecamp.logbook.shopping

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ShoppingItemResponse(
    val id: Long,
    val listId: Long,
    val name: String,
    val quantity: Int?,
    val unit: String?,
    val checked: Boolean,
)

data class ShoppingListResponse(
    val id: Long,
    val name: String,
    val finalizedAt: Instant?,
    val items: List<ShoppingItemResponse>,
    val updatedAt: Instant,
)

data class ShoppingListsResponse(val active: List<ShoppingListResponse>, val finalized: List<ShoppingListResponse>)

data class KnownItemResponse(val name: String, val useCount: Int)

data class CreateShoppingListRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
)

/** [finalized] = true closes the list, false reopens it; omit to leave finalized state unchanged. */
data class UpdateShoppingListRequest(
    @field:Size(min = 1, max = 200) val name: String? = null,
    val finalized: Boolean? = null,
)

data class AddShoppingItemRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Min(1) @field:Max(9999) val quantity: Int? = null,
    @field:Size(max = 30) val unit: String? = null,
)

/** Partial update. Send [clearQuantity]/[clearUnit] = true to remove that field. */
data class UpdateShoppingItemRequest(
    @field:Size(min = 1, max = 200) val name: String? = null,
    @field:Min(1) @field:Max(9999) val quantity: Int? = null,
    val clearQuantity: Boolean? = null,
    @field:Size(max = 30) val unit: String? = null,
    val clearUnit: Boolean? = null,
    val checked: Boolean? = null,
)

/** Exactly one of [targetListId] or [newListName] must be set. */
data class MoveItemsRequest(
    val targetListId: Long? = null,
    @field:Size(max = 200) val newListName: String? = null,
)

// ---- recurring templates ----

data class TemplateItemResponse(val id: Long, val name: String, val quantity: Int?, val unit: String?)

data class TemplateResponse(val id: Long, val name: String, val items: List<TemplateItemResponse>, val updatedAt: Instant)

data class CreateTemplateRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
)

data class UpdateTemplateRequest(
    @field:Size(min = 1, max = 200) val name: String,
)

data class AddTemplateItemRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Min(1) @field:Max(9999) val quantity: Int? = null,
    @field:Size(max = 30) val unit: String? = null,
)

/** If [name] is blank/omitted, the new list is named after the template. */
data class UseTemplateRequest(
    @field:Size(max = 200) val name: String? = null,
)
