package app.kaup.core.data.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two clocks this app reads, behind an interface so time can be controlled
 * in a test.
 *
 * The policy classes in `:shared-kmp` already take the current time as a
 * parameter and are well covered because of it. The Android-side glue did not:
 * `OverrideAuthorizer` and `PinAuthenticator` called `SystemClock` and
 * `System.currentTimeMillis` directly, which made every lockout, backoff and
 * grant-expiry path untestable, and those are the paths most worth testing.
 * See #174.
 *
 * The distinction between the two clocks is a security property, not a
 * convenience:
 *
 * - [uptimeMillis] is monotonic since boot and cannot be moved by the user.
 *   Everything that resists brute force is measured with it, so that winding
 *   the device clock forward does not clear a lockout. It resets on reboot,
 *   which the throttle policies handle explicitly.
 * - [epochMillis] is wall clock, comparable across devices, and therefore the
 *   right thing for an audit row's timestamp even though an operator can move
 *   it. See the note on `OverrideAuthorizer.verifyGrant`.
 *
 * Using the wrong one is a real bug, which is why they are separate methods
 * rather than one `now()`.
 */
interface TimeProvider {

    /** Milliseconds since boot. Monotonic, user-proof, resets on reboot. */
    fun uptimeMillis(): Long

    /** Wall clock milliseconds since the epoch. Comparable, user-settable. */
    fun epochMillis(): Long
}

/** The real clocks. */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    override fun uptimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun epochMillis(): Long = System.currentTimeMillis()
}
