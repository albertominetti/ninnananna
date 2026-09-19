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
 * Barra volume collegata al **volume reale dello smartphone** (AudioManager,
 * stream STREAM_MUSIC), non al volume interno dell'ExoPlayer.
 *
 * - [percent] è il volume di sistema normalizzato a 0..100;
 * - [setPercent] chiama AudioManager.setStreamVolume;
 * - un BroadcastReceiver su AudioManager.VOLUME_CHANGED_ACTION tiene la UI in
 *   sync quando l'utente usa i tasti fisici del volume.
 */
object VolumeManager {

    // Costanti "android.media.VOLUME_CHANGED_ACTION" / EXTRA_VOLUME_STREAM_TYPE:
    // sono @hide nel SDK, quindi usiamo le stringhe letterali del framework.
    private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    private val _percent = MutableStateFlow(100)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun currentIndex(context: Context): Int =
        audioManager(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    /** Converte un indice di volume del device nella percentuale 0..100. */
    fun percentFromIndex(context: Context, index: Int): Int {
        val am = audioManager(context) ?: return 100
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 100
        return ((index.toFloat() / max) * 100f).roundToInt().coerceIn(0, 100)
    }

    /** Aggiorna il valore mostrato dalla UI leggendo il volume di sistema. */
    fun refresh(context: Context) {
        _percent.value = percentFromIndex(context, currentIndex(context))
    }

    /**
     * Imposta il volume di sistema (0..100) tramite AudioManager.
     * Il valore viene applicato subito; in più il sistema emetterà
     * VOLUME_CHANGED_ACTION che aggiornerà [percent] (tramite l'observer).
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
     * Registra l'osservatore dei cambi di volume (tasti fisici).
     * Idempotente: un solo receiver per processo.
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