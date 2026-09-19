package com.alberto.ninnananna

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Singleton ExoPlayer: un solo player alla volta in tutta l'app.
 * Espone flussi StateFlow osservabili dalla UI (mini-bar, pulsanti Play/Stop).
 */
object PlayerManager {

    private var player: ExoPlayer? = null

    private val _current = MutableStateFlow<Lullaby?>(null)
    val current: StateFlow<Lullaby?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _loopEnabled = MutableStateFlow(false)
    val loopEnabled: StateFlow<Boolean> = _loopEnabled.asStateFlow()

    private val _volumePercent = MutableStateFlow(100)
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    @Synchronized
    fun play(context: Context, lullaby: Lullaby) {
        val p = player ?: createPlayer(context)
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(lullaby.filePath))))
        // Loop "repeat-one" se abilitato (persiste tra i brani).
        p.repeatMode = if (_loopEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.prepare()
        p.playWhenReady = true
        _current.value = lullaby
        _playing.value = true
    }

    @Synchronized
    fun toggleLoop() {
        val p = player ?: return
        val enabled = p.repeatMode != Player.REPEAT_MODE_ONE
        p.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _loopEnabled.value = enabled
    }

    /**
     * Imposta il volume del player (0..100). Funziona anche prima che la
     * riproduzione inizi: il valore viene applicato al primo player creato.
     */
    @Synchronized
    fun setVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volumePercent.value = clamped
        player?.volume = clamped / 100f
    }

    @Synchronized
    fun stop() {
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        _current.value = null
        _playing.value = false
    }

    @Synchronized
    fun release() {
        player?.release()
        player = null
        _current.value = null
        _playing.value = false
        _loopEnabled.value = false
    }

    private fun createPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playing.value = false
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playing.value = isPlaying
                }
            })
            // Applica il volume impostato (default 100%).
            volume = _volumePercent.value / 100f
        }.also { player = it }
    }
}