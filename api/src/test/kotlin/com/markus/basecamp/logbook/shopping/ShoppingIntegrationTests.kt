package com.markus.basecamp.logbook.shopping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Runs the whole app against a real Postgres, so the V6 migration and Hibernate schema validation are exercised too.
 * Skipped automatically where Docker is unavailable (e.g. local Windows).
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class ShoppingIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var shopping: ShoppingService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun adminId(): Long =
        jdbc.queryForObject("select id from core.app_user where username = 'admin'", Long::class.java)!!

    @Test
    fun `lists can be created, renamed, finalized, reopened and deleted`() {
        val userId = adminId()
        val list = shopping.createList(userId, CreateShoppingListRequest("  Rewe  "))
        assertEquals("Rewe", list.name)
        assertNull(list.finalizedAt)
        assertTrue(shopping.listAll(userId).active.any { it.id == list.id })

        val renamed = shopping.updateList(userId, list.id, UpdateShoppingListRequest(name = "Rewe Wochenmarkt"))
        assertEquals("Rewe Wochenmarkt", renamed.name)

        val finalized = shopping.updateList(userId, list.id, UpdateShoppingListRequest(finalized = true))
        assertTrue(finalized.finalizedAt != null)
        assertTrue(shopping.listAll(userId).finalized.any { it.id == list.id })
        assertFalse(shopping.listAll(userId).active.any { it.id == list.id })

        val reopened = shopping.updateList(userId, list.id, UpdateShoppingListRequest(finalized = false))
        assertNull(reopened.finalizedAt)

        shopping.deleteList(userId, list.id)
        assertThrows<ResponseStatusException> { shopping.updateList(userId, list.id, UpdateShoppingListRequest(name = "x")) }
    }

    @Test
    fun `adding the same unchecked item twice merges quantities instead of duplicating`() {
        val userId = adminId()
        val list = shopping.createList(userId, CreateShoppingListRequest("Merge test"))

        shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Milk"))
        val second = shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "milk", quantity = 2, unit = "L"))

        val items = shopping.listAll(userId).active.first { it.id == list.id }.items
        assertEquals(1, items.size, "same name (any case) merges into one row")
        assertEquals(second.id, items.single().id)
        assertEquals(3, items.single().quantity) // 1 (implied) + 2
        assertEquals("L", items.single().unit)

        // a checked item with the same name does not absorb a new addition
        shopping.updateItem(userId, items.single().id, UpdateShoppingItemRequest(checked = true))
        shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Milk"))
        assertEquals(2, shopping.listAll(userId).active.first { it.id == list.id }.items.size)
    }

    @Test
    fun `adding items feeds the autocomplete pool, ranked by use`() {
        val userId = adminId()
        val list = shopping.createList(userId, CreateShoppingListRequest("Known items test"))

        shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Bananas"))
        shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Oat milk"))
        shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "bananas")) // different case, same known item

        val known = shopping.knownItems(userId)
        val bananas = known.first { it.name.equals("Bananas", ignoreCase = true) }
        assertEquals(2, bananas.useCount)
        assertTrue(known.indexOf(bananas) < known.indexOfFirst { it.name == "Oat milk" }, "more-used item ranks first")
    }

    @Test
    fun `quantity can be set, cleared and checked off`() {
        val userId = adminId()
        val list = shopping.createList(userId, CreateShoppingListRequest("Detail test"))
        val item = shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Apples", quantity = 4, unit = "pcs"))
        assertEquals(4, item.quantity)
        assertEquals("pcs", item.unit)

        val cleared = shopping.updateItem(userId, item.id, UpdateShoppingItemRequest(clearQuantity = true, clearUnit = true))
        assertNull(cleared.quantity)
        assertNull(cleared.unit)

        val checked = shopping.updateItem(userId, item.id, UpdateShoppingItemRequest(checked = true))
        assertTrue(checked.checked)
    }

    @Test
    fun `an item can move to an existing list or a brand new one`() {
        val userId = adminId()
        val listA = shopping.createList(userId, CreateShoppingListRequest("Store A"))
        val listB = shopping.createList(userId, CreateShoppingListRequest("Store B"))
        val item = shopping.addItem(userId, listA.id, AddShoppingItemRequest(name = "Chili flakes"))

        val moved = shopping.moveItem(userId, item.id, MoveItemsRequest(targetListId = listB.id))
        assertEquals(listB.id, moved.listId)
        assertTrue(shopping.listAll(userId).active.first { it.id == listA.id }.items.isEmpty())

        val movedAgain = shopping.moveItem(userId, item.id, MoveItemsRequest(newListName = "Store C"))
        assertTrue(shopping.listAll(userId).active.any { it.name == "Store C" && it.items.any { i -> i.id == item.id } })
        assertEquals(movedAgain.listId, shopping.listAll(userId).active.first { it.name == "Store C" }.id)
    }

    @Test
    fun `moving unchecked items leaves checked items behind`() {
        val userId = adminId()
        val source = shopping.createList(userId, CreateShoppingListRequest("Partial store"))
        val target = shopping.createList(userId, CreateShoppingListRequest("Next store"))
        val found = shopping.addItem(userId, source.id, AddShoppingItemRequest(name = "Found it"))
        shopping.addItem(userId, source.id, AddShoppingItemRequest(name = "Missing it"))
        shopping.updateItem(userId, found.id, UpdateShoppingItemRequest(checked = true))

        val moved = shopping.moveUnchecked(userId, source.id, MoveItemsRequest(targetListId = target.id))
        assertEquals(listOf("Missing it"), moved.map { it.name })

        val remaining = shopping.listAll(userId).active.first { it.id == source.id }.items
        assertEquals(listOf("Found it"), remaining.map { it.name })
    }

    @Test
    fun `other users cannot see or touch someone else's shopping lists`() {
        val userId = adminId()
        val list = shopping.createList(userId, CreateShoppingListRequest("Private"))
        val item = shopping.addItem(userId, list.id, AddShoppingItemRequest(name = "Secret snacks"))
        val stranger = jdbc.queryForObject(
            "insert into core.app_user (username, password_hash, role) values ('shopping-stranger', 'x', 'USER') returning id",
            Long::class.java,
        )!!

        assertTrue(shopping.listAll(stranger).active.isEmpty())
        assertTrue(shopping.knownItems(stranger).isEmpty())
        assertThrows<ResponseStatusException> { shopping.updateList(stranger, list.id, UpdateShoppingListRequest(name = "x")) }
        assertThrows<ResponseStatusException> { shopping.updateItem(stranger, item.id, UpdateShoppingItemRequest(checked = true)) }
        assertThrows<ResponseStatusException> { shopping.deleteList(stranger, list.id) }
    }

    @Test
    fun `a list can be saved as a template and the template used to start a fresh list`() {
        val userId = adminId()
        val source = shopping.createList(userId, CreateShoppingListRequest("Weekly groceries"))
        val bread = shopping.addItem(userId, source.id, AddShoppingItemRequest(name = "Bread"))
        shopping.addItem(userId, source.id, AddShoppingItemRequest(name = "Eggs", quantity = 6))
        shopping.updateItem(userId, bread.id, UpdateShoppingItemRequest(checked = true)) // checked state should not matter

        val template = shopping.saveListAsTemplate(userId, source.id, CreateTemplateRequest("Weekly staples"))
        assertEquals(setOf("Bread", "Eggs"), template.items.map { it.name }.toSet())
        assertEquals(6, template.items.first { it.name == "Eggs" }.quantity)
        assertTrue(shopping.listTemplates(userId).any { it.id == template.id })

        val renamed = shopping.updateTemplate(userId, template.id, UpdateTemplateRequest("Weekly staples v2"))
        assertEquals("Weekly staples v2", renamed.name)

        val newList = shopping.useTemplate(userId, template.id, UseTemplateRequest())
        assertEquals("Weekly staples v2", newList.name, "defaults to the template's name")
        assertEquals(2, newList.items.size)
        assertTrue(newList.items.all { !it.checked }, "items start unchecked in the new list")
        // using a template also feeds the autocomplete pool, same as adding items by hand
        assertTrue(shopping.knownItems(userId).any { it.name == "Eggs" })

        val namedList = shopping.useTemplate(userId, template.id, UseTemplateRequest(name = "Rewe run"))
        assertEquals("Rewe run", namedList.name)

        shopping.deleteTemplate(userId, template.id)
        assertFalse(shopping.listTemplates(userId).any { it.id == template.id })
    }

    @Test
    fun `template items merge on duplicate names and can be removed`() {
        val userId = adminId()
        val template = shopping.createTemplate(userId, CreateTemplateRequest("Basics"))

        shopping.addTemplateItem(userId, template.id, AddTemplateItemRequest(name = "Milk"))
        val merged = shopping.addTemplateItem(userId, template.id, AddTemplateItemRequest(name = "milk", quantity = 2))
        val afterMerge = shopping.listTemplates(userId).first { it.id == template.id }
        assertEquals(1, afterMerge.items.size)
        assertEquals(3, afterMerge.items.single().quantity)

        shopping.removeTemplateItem(userId, merged.id)
        assertTrue(shopping.listTemplates(userId).first { it.id == template.id }.items.isEmpty())
    }

    @Test
    fun `other users cannot see or touch someone else's templates`() {
        val userId = adminId()
        val template = shopping.createTemplate(userId, CreateTemplateRequest("Private template"))
        val item = shopping.addTemplateItem(userId, template.id, AddTemplateItemRequest(name = "Secret sauce"))
        val stranger = jdbc.queryForObject(
            "insert into core.app_user (username, password_hash, role) values ('template-stranger', 'x', 'USER') returning id",
            Long::class.java,
        )!!

        assertTrue(shopping.listTemplates(stranger).isEmpty())
        assertThrows<ResponseStatusException> { shopping.updateTemplate(stranger, template.id, UpdateTemplateRequest("x")) }
        assertThrows<ResponseStatusException> { shopping.useTemplate(stranger, template.id, UseTemplateRequest()) }
        assertThrows<ResponseStatusException> { shopping.removeTemplateItem(stranger, item.id) }
        assertThrows<ResponseStatusException> { shopping.deleteTemplate(stranger, template.id) }
    }
}
