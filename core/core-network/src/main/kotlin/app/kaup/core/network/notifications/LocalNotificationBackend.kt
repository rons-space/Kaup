package app.kaup.core.network.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.kaup.shared.domain.notification.NotificationBackend
import app.kaup.shared.models.notification.NotificationEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Tier 0 notification backend: everything happens on this device.
 *
 * This implementation was previously duplicated, once here against a
 * two-method interface that fired a notification immediately, and once in
 * `:shared-kmp/androidMain` against the interface ADR-011 actually specifies.
 * The richer implementation won and moved here, because `:core-network` owns
 * the notification backends (docs/modules.md) and because `:shared-kmp` must
 * not contain `android.*` imports.
 */
@Singleton
class LocalNotificationBackend @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationBackend {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    override fun scheduleLocalAlert(event: NotificationEvent) {
        val targetTime = event.targetTime
        if (targetTime == null) {
            notificationManager.notify(event.id.hashCode(), buildNotification(event))
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime.toEpochMilliseconds(),
                alarmIntent(event.id, event.title, event.message)
            )
        }
    }

    override fun cancelAlert(eventId: String) {
        notificationManager.cancel(eventId.hashCode())
        alarmManager.cancel(alarmIntent(eventId, title = null, message = null))
    }

    /** Tier 0 has no server, so nothing here can reach a second device. */
    override fun isRemoteCapable(): Boolean = false

    private fun buildNotification(event: NotificationEvent): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setContentTitle(event.title)
            .setContentText(event.message)
            // TODO(#189): replace with the app icon once :core-ui ships res/.
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
    }

    private fun alarmIntent(eventId: String, title: String?, message: String?): PendingIntent {
        val intent = Intent(ACTION_NOTIFICATION).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ID, eventId)
            title?.let { putExtra(EXTRA_TITLE, it) }
            message?.let { putExtra(EXTRA_MESSAGE, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_NOTIFICATION = "app.kaup.ACTION_NOTIFICATION"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        private const val CHANNEL_ID = "kaup_alerts"
        private const val CHANNEL_NAME = "Kaup Alerts"
    }
}
