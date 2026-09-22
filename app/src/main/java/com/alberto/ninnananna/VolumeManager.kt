package com.alberto.ninnananna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Volume bar linked to the **real volume of the smartphone** (AudioManager,
 * STREAM_MUSIC stream), not to ExoPlayer's internal volume.
 *
 * - [percent] is the system volume normalized to 0..100;
 * - [setPercent] calls AudioManager.setStreamVolume;
 * - a BroadcastReceiver on AudioManager.VOLUME_CHANGED_ACTION keeps the UI in
 *   sync when the user uses the physical volume keys.
 */
object VolumeManager {

    // "android.media.VOLUME_CHANGED_ACTION" / EXTRA_VOLUME_STREAM_TYPE constants:
    // they are @hide in the SDK, so we use the framework literal strings.
    private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    private val _percent = MutableStateFlow(100)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun currentIndex(context: Context): Int =
        audioManager(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    /** Converts a device volume index into the 0..100 percentage. */
    fun percentFromIndex(context: Context, index: Int): Int {
        val am = audioManager(context) ?: return 100
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 100
        return ((index.toFloat() / max) * 100f).roundToInt().coerceIn(0, 100)
    }

    /** Updates the value shown by the UI by reading the system volume. */
    fun refresh(context: Context) {
        _percent.value = percentFromIndex(context, currentIndex(context))
    }

    /**
     * Sets the system volume (0..100) through AudioManager.
     * The value is applied immediately; in addition the system will emit
     * VOLUME_CHANGED_ACTION which will update [percent] (through the observer).
     */
    fun setPercent(context: Context, percent: Int) {
        val am = audioManager(context) ?: return
        val clamped = percent.coerceIn(0, 100)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
        val index = ((clamped / 100f) * (max - min)).roundToInt() + min
        _percent.value = clamped
        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    /**
     * Registers the volume-change observer (physical keys).
     * Idempotent: only one receiver per process.
     */
    fun register(context: Context) {
        if (receiver != null) return
        val appContext = context.applicationContext
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stream = intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (stream == AudioManager.STREAM_MUSIC || stream == -1) {
                    refresh(appContext)
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver!!,
                IntentFilter(ACTION_VOLUME_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun unregister(context: Context) {
        val r = receiver ?: return
        receiver = null
        runCatching { context.applicationContext.unregisterReceiver(r) }
    }
}