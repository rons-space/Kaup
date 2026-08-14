package app.kaup.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kaup.shared.domain.auth.OverrideScope
import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.models.SyncStatus

/**
 * One row per manager override that was granted, required by ADR-005 and
 * ADR-021.
 *
 * This is the only record that a restricted action happened with someone else's
 * authority. If it is missing, a void or a price override is indistinguishable
 * from ordinary work by whoever was signed in.
 *
 * There is deliberately **no foreign key to `users`**, unlike every other table
 * here. An audit record has to outlive the account it names, and the `CASCADE`
 * used elsewhere would mean deleting a manager erases the evidence of what they
 * approved, which is precisely what someone covering their tracks would do. The
 * id columns are indexed instead, and a lookup that finds no user renders as a
 * deleted account rather than dropping the row.
 *
 * Timestamps here are wall clock, whereas lockout deadlines are uptime. The two
 * are not interchangeable: a lockout must not be shortened by winding the
 * device clock, and an audit entry has to be comparable with rows from other
 * devices after sync.
 */
@Entity(
    tableName = "override_log",
    indices = [
        Index("approvedByUserId"),
        Index("requestedByUserId"),
        Index("transactionId"),
        Index("locationId"),
        Index("syncStatus")
    ]
)
data class OverrideLogEntity(
    @PrimaryKey val id: String,

    /** The manager whose HOTP code was validated. */
    val approvedByUserId: String,

    /** The signed-in staff member the approval was granted to. */
    val requestedByUserId: String,

    /**
     * What was authorised.
     *
     * Nullable only on the way out. Every write sets it, but a row written by a
     * build that knew a permission this one does not must read back as
     * unrecognised rather than being rewritten as some arbitrary permission,
     * which is what a non-null column with a fallback would do to an audit
     * record. See [app.kaup.core.data.converters.PermissionConverter].
     */
    val permission: Permission?,

    /**
     * The transaction the approval was bound to, for
     * [OverrideScope.SPECIFIC_ACTION]. Null for an elevation token, which by
     * definition is not yet attached to one.
     */
    val transactionId: String? = null,

    val scope: OverrideScope,

    /**
     * The HOTP counter the accepted code was generated from. Recorded because
     * it is what makes replay visible: two grants naming the same counter is
     * evidence of a bug or an attack, and no legitimate flow can produce it.
     */
    val counterUsed: Long,

    /** Wall clock, milliseconds since the epoch. See the note above. */
    val grantedAtEpochMillis: Long,

    val locationId: String,

    val syncStatus: SyncStatus = SyncStatus.PENDING
)
