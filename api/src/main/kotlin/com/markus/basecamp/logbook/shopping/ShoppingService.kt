package com.markus.basecamp.logbook.shopping

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** Every method takes the calling user's id and only ever touches that user's lists, items, templates and known items. */
@Service
@Transactional
class ShoppingService(
    private val lists: ShoppingListRepository,
    private val items: ShoppingItemRepository,
    private val known: KnownShoppingItemRepository,
    private val templates: ShoppingTemplateRepository,
    private val templateItems: ShoppingTemplateItemRepository,
) {

    @Transactional(readOnly = true)
    fun listAll(userId: Long) = ShoppingListsResponse(
        active = lists.findAllByUserIdAndFinalizedAtIsNullOrderByUpdatedAtDesc(userId).map { it.toResponse() },
        finalized = lists.findTop20ByUserIdAndFinalizedAtIsNotNullOrderByFinalizedAtDesc(userId).map { it.toResponse() },
    )

    @Transactional(readOnly = true)
    fun knownItems(userId: Long): List<KnownItemResponse> =
        known.findAllByUserIdOrderByUseCountDescLastUsedAtDesc(userId).map { KnownItemResponse(it.name, it.useCount) }

    fun createList(userId: Long, request: CreateShoppingListRequest): ShoppingListResponse =
        lists.save(ShoppingList(userId, request.name.trim())).toResponse()

    fun updateList(userId: Long, id: Long, request: UpdateShoppingListRequest): ShoppingListResponse {
        val list = findList(userId, id)
        request.name?.let { list.name = it.trim() }
        request.finalized?.let { list.finalizedAt = if (it) Instant.now() else null }
        list.updatedAt = Instant.now()
        return list.toResponse()
    }

    fun deleteList(userId: Long, id: Long) {
        lists.delete(findList(userId, id))
    }

    /** Adds an item, merging into an existing unchecked item of the same name (quantities add up) instead of duplicating it. */
    fun addItem(userId: Long, listId: Long, request: AddShoppingItemRequest): ShoppingItemResponse {
        val list = findList(userId, listId)
        val name = request.name.trim()
        remember(userId, name)

        val existing = items.findFirstByListIdAndCheckedFalseAndNameIgnoreCase(listId, name)
        val item = if (existing != null) {
            existing.quantity = (existing.quantity ?: 1) + (request.quantity ?: 1)
            request.unit?.let { existing.unit = it.trim() }
            existing
        } else {
            val nextOrder = (items.findAllByListIdOrderByCheckedAscSortOrderAscIdAsc(listId).maxOfOrNull { it.sortOrder } ?: 0) + 1
            items.save(
                ShoppingItem(
                    userId = userId,
                    listId = listId,
                    name = name,
                    quantity = request.quantity,
                    unit = request.unit?.trim()?.ifEmpty { null },
                    sortOrder = nextOrder,
                ),
            )
        }
        touch(list)
        return item.toResponse()
    }

    fun updateItem(userId: Long, id: Long, request: UpdateShoppingItemRequest): ShoppingItemResponse {
        val item = findItem(userId, id)
        request.name?.let { item.name = it.trim() }
        if (request.clearQuantity == true) item.quantity = null else request.quantity?.let { item.quantity = it }
        if (request.clearUnit == true) item.unit = null else request.unit?.let { item.unit = it.trim() }
        request.checked?.let {
            if (it != item.checked) {
                item.checked = it
                item.checkedAt = if (it) Instant.now() else null
            }
        }
        touch(findList(userId, item.listId))
        return item.toResponse()
    }

    fun deleteItem(userId: Long, id: Long) {
        val item = findItem(userId, id)
        items.delete(item)
        touch(findList(userId, item.listId))
    }

    /** Moves one item to an existing open list or a brand-new one (for "wasn't at this store"). */
    fun moveItem(userId: Long, itemId: Long, request: MoveItemsRequest): ShoppingItemResponse {
        val item = findItem(userId, itemId)
        val target = resolveTarget(userId, request)
        item.listId = target.id!!
        touch(target)
        return item.toResponse()
    }

    /** Moves every unchecked item off a list at once, e.g. "the rest wasn't at this store". */
    fun moveUnchecked(userId: Long, listId: Long, request: MoveItemsRequest): List<ShoppingItemResponse> {
        val source = findList(userId, listId)
        val target = resolveTarget(userId, request)
        if (target.id == source.id) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a different list")

        val moved = items.findAllByListIdOrderByCheckedAscSortOrderAscIdAsc(listId).filter { !it.checked }
        moved.forEach { it.listId = target.id!! }
        touch(source)
        touch(target)
        return moved.map { it.toResponse() }
    }

    private fun resolveTarget(userId: Long, request: MoveItemsRequest): ShoppingList {
        val newName = request.newListName?.trim()
        return when {
            !newName.isNullOrEmpty() -> lists.save(ShoppingList(userId, newName))
            request.targetListId != null -> findList(userId, request.targetListId)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a list to move to, or name a new one")
        }
    }

    // ---- recurring templates ----

    @Transactional(readOnly = true)
    fun listTemplates(userId: Long): List<TemplateResponse> =
        templates.findAllByUserIdOrderByUpdatedAtDesc(userId).map { it.toResponse() }

    fun createTemplate(userId: Long, request: CreateTemplateRequest): TemplateResponse =
        templates.save(ShoppingTemplate(userId, request.name.trim())).toResponse()

    fun updateTemplate(userId: Long, id: Long, request: UpdateTemplateRequest): TemplateResponse {
        val template = findTemplate(userId, id)
        template.name = request.name.trim()
        template.updatedAt = Instant.now()
        return template.toResponse()
    }

    fun deleteTemplate(userId: Long, id: Long) {
        templates.delete(findTemplate(userId, id))
    }

    /** A snapshot of a list's current items (any checked state) saved as a new, independent template. */
    fun saveListAsTemplate(userId: Long, listId: Long, request: CreateTemplateRequest): TemplateResponse {
        val list = findList(userId, listId)
        val template = templates.save(ShoppingTemplate(userId, request.name.trim()))
        items.findAllByListIdOrderByCheckedAscSortOrderAscIdAsc(list.id!!).forEachIndexed { index, item ->
            templateItems.save(ShoppingTemplateItem(userId, template.id!!, item.name, item.quantity, item.unit, index))
        }
        return template.toResponse()
    }

    fun addTemplateItem(userId: Long, templateId: Long, request: AddTemplateItemRequest): TemplateItemResponse {
        val template = findTemplate(userId, templateId)
        val name = request.name.trim()

        val existing = templateItems.findFirstByTemplateIdAndNameIgnoreCase(template.id!!, name)
        val item = if (existing != null) {
            existing.quantity = (existing.quantity ?: 1) + (request.quantity ?: 1)
            request.unit?.let { existing.unit = it.trim() }
            existing
        } else {
            val nextOrder = (templateItems.findAllByTemplateIdOrderBySortOrderAscIdAsc(template.id!!).maxOfOrNull { it.sortOrder } ?: 0) + 1
            templateItems.save(
                ShoppingTemplateItem(
                    userId = userId,
                    templateId = template.id!!,
                    name = name,
                    quantity = request.quantity,
                    unit = request.unit?.trim()?.ifEmpty { null },
                    sortOrder = nextOrder,
                ),
            )
        }
        template.updatedAt = Instant.now()
        return item.toResponse()
    }

    fun removeTemplateItem(userId: Long, itemId: Long) {
        val item = templateItems.findByIdAndUserId(itemId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Template item not found")
        templateItems.delete(item)
        findTemplate(userId, item.templateId).updatedAt = Instant.now()
    }

    /** Creates a fresh, active shopping list from a template's items (unchecked), and counts them as used again. */
    fun useTemplate(userId: Long, templateId: Long, request: UseTemplateRequest): ShoppingListResponse {
        val template = findTemplate(userId, templateId)
        val templateEntries = templateItems.findAllByTemplateIdOrderBySortOrderAscIdAsc(template.id!!)

        val list = lists.save(ShoppingList(userId, request.name?.trim()?.ifEmpty { null } ?: template.name))
        templateEntries.forEachIndexed { index, entry ->
            remember(userId, entry.name)
            items.save(ShoppingItem(userId, list.id!!, entry.name, entry.quantity, entry.unit, index))
        }
        return list.toResponse()
    }

    /** Upserts the autocomplete pool; called whenever an item is added to any list. */
    private fun remember(userId: Long, name: String) {
        val row = known.findByUserIdAndNameIgnoreCase(userId, name) ?: known.save(KnownShoppingItem(userId, name))
        row.useCount += 1
        row.lastUsedAt = Instant.now()
    }

    private fun touch(list: ShoppingList) {
        list.updatedAt = Instant.now()
    }

    private fun findList(userId: Long, id: Long): ShoppingList =
        lists.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list not found")

    private fun findItem(userId: Long, id: Long): ShoppingItem =
        items.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found")

    private fun findTemplate(userId: Long, id: Long): ShoppingTemplate =
        templates.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found")

    private fun ShoppingList.toResponse() = ShoppingListResponse(
        id = id!!,
        name = name,
        finalizedAt = finalizedAt,
        items = items.findAllByListIdOrderByCheckedAscSortOrderAscIdAsc(id!!).map { it.toResponse() },
        updatedAt = updatedAt,
    )

    private fun ShoppingItem.toResponse() = ShoppingItemResponse(id!!, listId, name, quantity, unit, checked)

    private fun ShoppingTemplate.toResponse() = TemplateResponse(
        id = id!!,
        name = name,
        items = templateItems.findAllByTemplateIdOrderBySortOrderAscIdAsc(id!!).map { it.toResponse() },
        updatedAt = updatedAt,
    )

    private fun ShoppingTemplateItem.toResponse() = TemplateItemResponse(id!!, name, quantity, unit)
}
