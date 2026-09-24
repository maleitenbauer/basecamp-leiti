package com.markus.basecamp.logbook.shopping

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Lifestyle > Shopping List. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/logbook/shopping")
class ShoppingController(
    private val service: ShoppingService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/lists")
    fun listAll() = service.listAll(userId)

    @GetMapping("/known-items")
    fun knownItems() = service.knownItems(userId)

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    fun createList(@Valid @RequestBody request: CreateShoppingListRequest) = service.createList(userId, request)

    @PatchMapping("/lists/{id}")
    fun updateList(@PathVariable id: Long, @Valid @RequestBody request: UpdateShoppingListRequest) =
        service.updateList(userId, id, request)

    @DeleteMapping("/lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteList(@PathVariable id: Long) = service.deleteList(userId, id)

    @PostMapping("/lists/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addItem(@PathVariable id: Long, @Valid @RequestBody request: AddShoppingItemRequest) =
        service.addItem(userId, id, request)

    @PostMapping("/lists/{id}/move-unchecked")
    fun moveUnchecked(@PathVariable id: Long, @Valid @RequestBody request: MoveItemsRequest) =
        service.moveUnchecked(userId, id, request)

    @PostMapping("/lists/{id}/save-as-template")
    @ResponseStatus(HttpStatus.CREATED)
    fun saveListAsTemplate(@PathVariable id: Long, @Valid @RequestBody request: CreateTemplateRequest) =
        service.saveListAsTemplate(userId, id, request)

    @PatchMapping("/items/{id}")
    fun updateItem(@PathVariable id: Long, @Valid @RequestBody request: UpdateShoppingItemRequest) =
        service.updateItem(userId, id, request)

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(@PathVariable id: Long) = service.deleteItem(userId, id)

    @PostMapping("/items/{id}/move")
    fun moveItem(@PathVariable id: Long, @Valid @RequestBody request: MoveItemsRequest) =
        service.moveItem(userId, id, request)

    // ---- recurring templates ----

    @GetMapping("/templates")
    fun listTemplates() = service.listTemplates(userId)

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTemplate(@Valid @RequestBody request: CreateTemplateRequest) = service.createTemplate(userId, request)

    @PatchMapping("/templates/{id}")
    fun updateTemplate(@PathVariable id: Long, @Valid @RequestBody request: UpdateTemplateRequest) =
        service.updateTemplate(userId, id, request)

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTemplate(@PathVariable id: Long) = service.deleteTemplate(userId, id)

    @PostMapping("/templates/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addTemplateItem(@PathVariable id: Long, @Valid @RequestBody request: AddTemplateItemRequest) =
        service.addTemplateItem(userId, id, request)

    @DeleteMapping("/template-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeTemplateItem(@PathVariable id: Long) = service.removeTemplateItem(userId, id)

    @PostMapping("/templates/{id}/use")
    @ResponseStatus(HttpStatus.CREATED)
    fun useTemplate(@PathVariable id: Long, @Valid @RequestBody request: UseTemplateRequest) =
        service.useTemplate(userId, id, request)
}
