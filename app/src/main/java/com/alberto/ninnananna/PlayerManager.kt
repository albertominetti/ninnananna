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
 * Singleton ExoPlayer: only one player at a time across the whole app.
 * Exposes StateFlow streams observable from the UI (mini bar, Play/Stop buttons).
 */
object PlayerManager {

    private var player: ExoPlayer? = null
    private var appContext: Context? = null

    private val _current = MutableStateFlow<Lullaby?>(null)
    val current: StateFlow<Lullaby?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _loopEnabled = MutableStateFlow(false)
    val loopEnabled: StateFlow<Boolean> = _loopEnabled.asStateFlow()

    @Synchronized
    fun play(context: Context, lullaby: Lullaby) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val p = player ?: createPlayer(appCtx)
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(lullaby.filePath))))
        // "Repeat one" loop if enabled (persists between tracks).
        p.repeatMode = if (_loopEnabled.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.prepare()
        p.playWhenReady = true
        _current.value = lullaby
        _playing.value = true
        // Persistent "Now playing" notification (ongoing, with a Stop action).
        val title = formatDisplayName(appCtx, lullaby.title)
        PlaybackNotification.show(appCtx, title, appCtx.getString(R.string.now_playing))
    }

    @Synchronized
    fun toggleLoop() {
        val p = player ?: return
        val enabled = p.repeatMode != Player.REPEAT_MODE_ONE
        p.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _loopEnabled.value = enabled
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
        // Don't hide when Cast is streaming – the Cast notification must stay
        if (CastManager.streaming.value == null) {
            PlaybackNotification.hide(appContext ?: return)
        }
    }

    @Synchronized
    fun release() {
        player?.release()
        player = null
        _current.value = null
        _playing.value = false
        _loopEnabled.value = false
        if (CastManager.streaming.value == null) {
            PlaybackNotification.hide(appContext ?: return)
        }
    }

    private fun createPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playing.value = false
                        // Natural end – only hide if not casting (Cast has its own notification)
                        if (CastManager.streaming.value == null) {
                            PlaybackNotification.hide(appContext ?: return)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playing.value = isPlaying
                    // If there is no current track anymore, remove notification
                    // unless Cast is streaming (its notification must stay).
                    if (!isPlaying && _current.value == null && CastManager.streaming.value == null) {
                        PlaybackNotification.hide(appContext ?: return)
                    }
                }
            })
        }.also { player = it }
    }
}