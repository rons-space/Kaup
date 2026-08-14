package app.kaup.core.data.converters

import androidx.room.TypeConverter
import app.kaup.shared.models.SyncStatus

/**
 * Persists [SyncStatus] as its enum name.
 *
 * Every syncable row carries this column, so a value the current build does not
 * recognise (a row written by a newer version, or a corrupted string) must not
 * crash the read. It degrades to [SyncStatus.PENDING], which is the safe
 * default: the record is offered to the sync engine again rather than being
 * silently treated as already delivered.
 */
class SyncStatusConverter {
    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus =
        SyncStatus.entries.firstOrNull { it.name == value } ?: SyncStatus.PENDING

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name
}
