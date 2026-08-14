package app.kaup.core.data.converters

import androidx.room.TypeConverter
import app.kaup.shared.domain.models.auth.Role

class RoleConverter {
    /**
     * An unrecognised role degrades to the least privileged role rather than
     * throwing. `enumValueOf` used to be called directly here, so a role
     * written by a newer build, or a corrupted string, crashed every read of
     * the users table, which means the lock screen could not be drawn and the
     * app was unusable with no way back in.
     */
    @TypeConverter
    fun toRole(value: String?): Role =
        Role.entries.firstOrNull { it.name == value } ?: Role.CASHIER

    @TypeConverter
    fun fromRole(value: Role): String = value.name
}
