package app.kaup.core.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Marks the surrounding screen as secure while it is on screen: no screenshot,
 * no screen recording, and a blank frame in the recent apps list.
 *
 * Call it at the top of any screen that puts a credential in front of the
 * camera. That is the HOTP provisioning screen, which displays the shared
 * secret as a QR code that a phone across the room can read; the override code
 * screen, whose whole content is a live one-time password; and the lock screen,
 * where the PIN pad reveals which keys are being pressed.
 *
 * The flag is a window property rather than a view property, so it is set and
 * cleared around composition rather than declared once in the manifest. The
 * consequence is worth knowing: **the flag is cleared when this leaves the
 * composition**, so two secure screens stacked on top of each other will
 * unprotect the window when the upper one is dismissed. That is fine for the
 * current flows, which never nest, and would need a counter if they ever do.
 *
 * Does nothing in a Compose preview, where there is no Activity to configure.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current

    DisposableEffect(context, inPreview) {
        val window = if (inPreview) null else context.findActivity()?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/**
 * Walks up the ContextWrapper chain to the Activity.
 *
 * A Composable's context is frequently a wrapper rather than the Activity
 * itself, so a direct cast works until something wraps it and then fails at
 * runtime on exactly the screens that must not fail open.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
