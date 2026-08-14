package app.kaup.core.network.notifications

import app.kaup.shared.domain.notification.NotificationBackend
import app.kaup.shared.models.notification.NotificationEvent
import app.kaup.shared.models.notification.NotificationType
import kotlinx.datetime.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps an application event to the notification backend in force.
 *
 * The router used to carry its own `AppEvent` enum, a fourth of the five
 * events ADR-011 defines and a duplicate of `NotificationType`. It now speaks
 * the shared vocabulary, so a caller cannot raise an event the backends have
 * never heard of.
 */
@Singleton
class NotificationRouter @Inject constructor(
    private val backend: NotificationBackend
) {
    fun route(
        type: NotificationType,
        message: String,
        targetTime: Instant? = null,
        id: String = UUID.randomUUID().toString()
    ) {
        backend.scheduleLocalAlert(
            NotificationEvent(
                id = id,
                type = type,
                title = titleFor(type),
                message = message,
                targetTime = targetTime
            )
        )
    }

    fun cancel(eventId: String) = backend.cancelAlert(eventId)

    // TODO(#189): move to strings.xml. These are user-facing and cannot be
    // localised while they are Kotlin literals in a core module.
    private fun titleFor(type: NotificationType): String = when (type) {
        NotificationType.LOW_STOCK -> "Low stock"
        NotificationType.SYNC_FAILURE -> "Sync failed"
        NotificationType.SHIFT_OPEN -> "Shift reminder"
        NotificationType.BACKUP_REMINDER -> "Backup due"
        NotificationType.MANAGER_OVERRIDE -> "Approval requested"
    }
}
