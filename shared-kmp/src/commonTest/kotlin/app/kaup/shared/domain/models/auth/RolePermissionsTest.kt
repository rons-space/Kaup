package app.kaup.shared.domain.models.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the LIVE RBAC source of truth: [getDefaultPermissions].
 *
 * SessionManager derives a user's permission set from this extension, so these
 * tests pin the privilege boundaries between the four built-in roles. The
 * legacy `RoleDefaults` object and its parallel Role/Permission enums have been
 * deleted, so this is now the only definition in the codebase.
 */
class RolePermissionsTest {

    @Test
    fun `owner has every permission`() {
        assertEquals(Permission.entries.toSet(), Role.OWNER.getDefaultPermissions())
    }

    @Test
    fun `manager has all permissions except user management`() {
        val manager = Role.MANAGER.getDefaultPermissions()
        val userPerms = setOf(
            Permission.USERS_VIEW,
            Permission.USERS_ADD,
            Permission.USERS_EDIT,
            Permission.USERS_DELETE,
        )
        // Everything the owner has, minus the four USERS_* permissions.
        assertEquals(Permission.entries.toSet() - userPerms, manager)
    }

    @Test
    fun `manager cannot manage users`() {
        val manager = Role.MANAGER.getDefaultPermissions()
        assertFalse(manager.contains(Permission.USERS_ADD))
        assertFalse(manager.contains(Permission.USERS_EDIT))
        assertFalse(manager.contains(Permission.USERS_DELETE))
        assertFalse(manager.contains(Permission.USERS_VIEW))
    }

    @Test
    fun `manager retains non-user administrative permissions`() {
        val manager = Role.MANAGER.getDefaultPermissions()
        assertTrue(manager.contains(Permission.SETTINGS_TAX))
        assertTrue(manager.contains(Permission.REPORTS_VIEW_FINANCIAL))
        assertTrue(manager.contains(Permission.POS_VOID_TRANSACTION))
    }

    @Test
    fun `cashier has exactly the front-line sale permissions`() {
        val expected = setOf(
            Permission.POS_CHECKOUT,
            Permission.POS_OPEN_SHIFT,
            Permission.POS_CLOSE_SHIFT,
            Permission.POS_APPLY_DISCOUNT,
            Permission.CUSTOMERS_VIEW,
            Permission.CUSTOMERS_ADD,
        )
        assertEquals(expected, Role.CASHIER.getDefaultPermissions())
    }

    @Test
    fun `cashier cannot void, refund, or override price without approval`() {
        val cashier = Role.CASHIER.getDefaultPermissions()
        assertFalse(cashier.contains(Permission.POS_VOID_TRANSACTION))
        assertFalse(cashier.contains(Permission.POS_ISSUE_REFUND))
        assertFalse(cashier.contains(Permission.POS_OVERRIDE_PRICE))
        assertFalse(cashier.contains(Permission.INVENTORY_DELETE_ITEM))
    }

    @Test
    fun `crew has exactly the restaurant floor permissions`() {
        val expected = setOf(
            Permission.POS_TABLE_MANAGEMENT,
            Permission.POS_CHECKOUT,
            Permission.POS_APPLY_DISCOUNT,
        )
        assertEquals(expected, Role.CREW.getDefaultPermissions())
    }

    @Test
    fun `every role resolves to a non-empty permission set`() {
        for (role in Role.entries) {
            assertTrue(
                role.getDefaultPermissions().isNotEmpty(),
                "Role $role should have at least one default permission",
            )
        }
    }

    @Test
    fun `only the owner may delete users`() {
        for (role in Role.entries) {
            val canDeleteUsers = role.getDefaultPermissions().contains(Permission.USERS_DELETE)
            assertEquals(role == Role.OWNER, canDeleteUsers, "USERS_DELETE for $role")
        }
    }
}
