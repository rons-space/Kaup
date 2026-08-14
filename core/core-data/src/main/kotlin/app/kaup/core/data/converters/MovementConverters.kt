package app.kaup.core.data.converters

import androidx.room.TypeConverter
import app.kaup.shared.models.MovementDirection
import app.kaup.shared.models.MovementType

/**
 * Persists the stock movement enums by name.
 *
 * The movement log is append-only and is replayed to compute stock, so an
 * unreadable row would silently change every stock figure derived from it.
 * Unknown values therefore fall back to [MovementType.ADJUSTMENT], which is the
 * type that carries no implied business meaning, and the direction falls back
 * to [MovementDirection.OUT] so a bad row can never invent inventory.
 */
class MovementTypeConverter {
    @TypeConverter
    fun toMovementType(value: String?): MovementType =
        MovementType.entries.firstOrNull { it.name == value } ?: MovementType.ADJUSTMENT

    @TypeConverter
    fun fromMovementType(type: MovementType): String = type.name
}

class MovementDirectionConverter {
    @TypeConverter
    fun toMovementDirection(value: String?): MovementDirection =
        MovementDirection.entries.firstOrNull { it.name == value } ?: MovementDirection.OUT

    @TypeConverter
    fun fromMovementDirection(direction: MovementDirection): String = direction.name
}
