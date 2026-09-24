package com.markus.basecamp.logbook.shopping

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "shopping_list", schema = "logbook")
class ShoppingList(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var finalizedAt: Instant? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "shopping_item", schema = "logbook")
class ShoppingItem(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var listId: Long,

    @Column(nullable = false, length = 200)
    var name: String,

    var quantity: Int? = null,

    @Column(length = 30)
    var unit: String? = null,

    @Column(nullable = false)
    var sortOrder: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var checked: Boolean = false

    var checkedAt: Instant? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

/** The per-user autocomplete pool: one row per distinct item name the user has ever added, anywhere. */
@Entity
@Table(name = "shopping_known_item", schema = "logbook")
class KnownShoppingItem(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var useCount: Int = 1

    @Column(nullable = false)
    var lastUsedAt: Instant = Instant.now()
}

@Entity
@Table(name = "shopping_template", schema = "logbook")
class ShoppingTemplate(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "shopping_template_item", schema = "logbook")
class ShoppingTemplateItem(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var templateId: Long,

    @Column(nullable = false, length = 200)
    var name: String,

    var quantity: Int? = null,

    @Column(length = 30)
    var unit: String? = null,

    @Column(nullable = false)
    var sortOrder: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

interface ShoppingListRepository : JpaRepository<ShoppingList, Long> {
    fun findAllByUserIdAndFinalizedAtIsNullOrderByUpdatedAtDesc(userId: Long): List<ShoppingList>
    fun findTop20ByUserIdAndFinalizedAtIsNotNullOrderByFinalizedAtDesc(userId: Long): List<ShoppingList>
    fun findByIdAndUserId(id: Long, userId: Long): ShoppingList?
}

interface ShoppingItemRepository : JpaRepository<ShoppingItem, Long> {
    fun findAllByListIdOrderByCheckedAscSortOrderAscIdAsc(listId: Long): List<ShoppingItem>
    fun findByIdAndUserId(id: Long, userId: Long): ShoppingItem?

    /** Case-insensitive match against an existing unchecked item in the same list, for merge-on-add. */
    fun findFirstByListIdAndCheckedFalseAndNameIgnoreCase(listId: Long, name: String): ShoppingItem?
}

interface KnownShoppingItemRepository : JpaRepository<KnownShoppingItem, Long> {
    fun findAllByUserIdOrderByUseCountDescLastUsedAtDesc(userId: Long): List<KnownShoppingItem>
    fun findByUserIdAndNameIgnoreCase(userId: Long, name: String): KnownShoppingItem?
}

interface ShoppingTemplateRepository : JpaRepository<ShoppingTemplate, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<ShoppingTemplate>
    fun findByIdAndUserId(id: Long, userId: Long): ShoppingTemplate?
}

interface ShoppingTemplateItemRepository : JpaRepository<ShoppingTemplateItem, Long> {
    fun findAllByTemplateIdOrderBySortOrderAscIdAsc(templateId: Long): List<ShoppingTemplateItem>
    fun findByIdAndUserId(id: Long, userId: Long): ShoppingTemplateItem?
    fun findFirstByTemplateIdAndNameIgnoreCase(templateId: Long, name: String): ShoppingTemplateItem?
}
