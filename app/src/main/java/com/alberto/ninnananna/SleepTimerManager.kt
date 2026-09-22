package com.alberto.ninnananna

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Global sleep timer: when it expires it stops the playback and removes the
 * "keep screen on" flag (set in [SettingsStore]).
 *
 * The available timeouts are chosen by the UI (15 min, 30 min, 1h, 2h, 3h,
 * 4h or off). The countdown survives the closing of the screen as long as
 * the app process is alive.
 */
object SleepTimerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _remainingMillis = MutableStateFlow<Long?>(null)

    /** Remaining milliseconds, or null when the timer is off. */
    val remainingMillis: StateFlow<Long?> = _remainingMillis.asStateFlow()

    private var job: Job? = null

    /**
     * Starts (or restarts) the timer with the given duration.
     * [context] is used only on expiry to remove keep-screen-on.
     */
    @Synchronized
    fun start(context: Context, durationMillis: Long): Boolean {
        if (durationMillis <= 0) return false
        cancel()
        val deadline = System.currentTimeMillis() + durationMillis
        _remainingMillis.value = durationMillis
        job = scope.launch {
            while (isActive) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                _remainingMillis.value = remaining
                delay(1000)
            }
            // On expiry: stop the audio and remove the "keep screen on".
            PlayerManager.stop()
            CastManager.stopStreaming()
            SettingsStore.setKeepScreenOn(context, false)
            _remainingMillis.value = null
        }
        return true
    }

    /** Turns off the timer without any action (playback continues). */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
        _remainingMillis.value = null
    }
}