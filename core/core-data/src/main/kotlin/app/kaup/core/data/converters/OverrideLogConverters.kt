package app.kaup.core.data.converters

import androidx.room.TypeConverter
import app.kaup.shared.domain.auth.OverrideScope
import app.kaup.shared.domain.models.auth.Permission

/**
 * Persists the permission an override authorised, as its enum name.
 *
 * Unlike [RoleConverter] and [SyncStatusConverter], an unrecognised value is
 * **not** degraded to a default. Those two guard columns that must stay
 * readable for the app to function at all, and picking the least privileged
 * fallback is safe. Here the column records what actually happened, and
 * rewriting an unreadable audit entry as some arbitrary permission would be
 * fabricating history. Null is returned instead, and a caller displaying the
 * log shows the row as unrecognised.
 */
class PermissionConverter {
    @TypeConverter
    fun toPermission(value: String?): Permission? =
        Permission.entries.firstOrNull { it.name == value }

    @TypeConverter
    fun fromPermission(value: Permission?): String? = value?.name
}

/**
 * Persists [OverrideScope] as its enum name.
 *
 * Degrades to [OverrideScope.SPECIFIC_ACTION] because that is the narrower of
 * the two, so a row that cannot be read is never reported as having granted
 * broader authority than it did.
 */
class OverrideScopeConverter {
    @TypeConverter
    fun toOverrideScope(value: String?): OverrideScope =
        OverrideScope.entries.firstOrNull { it.name == value } ?: OverrideScope.SPECIFIC_ACTION

    @TypeConverter
    fun fromOverrideScope(value: OverrideScope): String = value.name
}
