package app.kaup.core.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.domain.models.auth.Role
import app.kaup.shared.models.SyncStatus

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationId"), Index("syncStatus")]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: Role,
    // The staff member's home location. Non-null per ADR-016: a user always
    // belongs somewhere, and the default location is seeded before any other
    // row can be written.
    val locationId: String = LocationEntity.DEFAULT_ID,
    // PBKDF2 hash of the PIN, hex encoded. Never the PIN itself.
    val pinHash: String,
    // Per-user random salt, hex encoded, and the iteration count in force when
    // this credential was written. Storing the count per row means the cost can
    // be raised later without invalidating existing PINs.
    val pinSalt: String,
    val pinIterations: Int,
    // Brute-force state. Attempts persist across process death and reboot so
    // that killing the app is not a way to reset the counter.
    val failedPinAttempts: Int = 0,
    val pinLockoutUntilUptimeMillis: Long = 0L,
    val permissionsOverride: Set<Permission>? = null,
    val hotpSecretEncrypted: String? = null,
    val hotpCounter: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
