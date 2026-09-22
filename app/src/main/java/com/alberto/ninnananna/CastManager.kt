package com.alberto.ninnananna

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates streaming to a Google Cast / Chromecast device:
 * - exposes [connected], [deviceName] and [volumePercent] observable from the UI;
 * - [streaming] = track currently streaming on the device (null = none);
 * - [castLoopEnabled] = repeat-one for streaming;
 * - [castCurrent] starts the local HTTP server and loads the current track
 *   on the remote receiver (Default Media Receiver), pausing the local
 *   speaker to avoid double audio and showing the persistent "Streaming to
 *   <device>" notification;
 * - when connected, the volume bar controls the Cast device volume through
 *   [setVolume] (the Cast notifies changes via [volumeListener]).
 */
object CastManager {

    private const val TAG = "CastManager"

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _volumePercent = MutableStateFlow(100)
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    /** Track currently streaming on the Cast device (null = none). */
    private val _streaming = MutableStateFlow<Lullaby?>(null)
    val streaming: StateFlow<Lullaby?> = _streaming.asStateFlow()

    /** Repeat-one active for Cast streaming. */
    private val _castLoopEnabled = MutableStateFlow(false)
    val castLoopEnabled: StateFlow<Boolean> = _castLoopEnabled.asStateFlow()

    @Volatile
    private var initialized = false

    private var appContext: Context? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null
    private var lastMediaInfo: MediaInfo? = null

    /** Syncs the % shown by the UI with the real Cast device volume. */
    private val volumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            syncCastVolume()
        }
    }

    /** End-of-playback handling: repeat-one or cleanup + notification removal. */
    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val s = activeSession()?.remoteMediaClient?.mediaStatus ?: return
            if (s.playerState != MediaStatus.PLAYER_STATE_IDLE) return
            when (s.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> {
                    val last = lastMediaInfo
                    if (_castLoopEnabled.value && last != null) {
                        // Repeat-one: reload the same track.
                        runCatching { activeSession()?.remoteMediaClient?.load(last) }
                    } else {
                        clearStreaming(hideNotification = true)
                    }
                }
                MediaStatus.IDLE_REASON_CANCELED,
                MediaStatus.IDLE_REASON_ERROR,
                MediaStatus.IDLE_REASON_INTERRUPTED -> clearStreaming(hideNotification = true)
            }
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.i(TAG, "Cast session started")
            onSessionActive(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.i(TAG, "Cast session resumed")
            onSessionActive(session)
        }

        override fun onSessionEnding(session: CastSession) {
            // Nothing else: full cleanup happens in onSessionEnded.
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.i(TAG, "Cast session ended (error=$error)")
            cleanup()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Cast session start failed (error=$error)")
            cleanup()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Cast session resume failed (error=$error)")
            cleanup()
        }

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    private fun onSessionActive(session: CastSession) {
        currentSession = session
        runCatching { session.addCastListener(volumeListener) }
        runCatching { session.remoteMediaClient?.registerCallback(mediaCallback) }
        _connected.value = true
        _deviceName.value = session.castDevice?.friendlyName ?: session.castDevice?.modelName
        Log.i(TAG, "Connected to: ${_deviceName.value}")
        syncCastVolume()
        // If there is already a current track, load it on the device right away.
        val current = PlayerManager.current.value ?: return
        castCurrent(appContext ?: return, current)
    }

    private fun cleanup() {
        val s = currentSession
        if (s != null) {
            runCatching { s.removeCastListener(volumeListener) }
            runCatching { s.remoteMediaClient?.unregisterCallback(mediaCallback) }
        }
        currentSession = null
        _connected.value = false
        _deviceName.value = null
        clearStreaming(hideNotification = true)
    }

    /** Stops the streaming state on the app side (without touching the remote device). */
    private fun clearStreaming(hideNotification: Boolean) {
        val had = _streaming.value != null
        _streaming.value = null
        lastMediaInfo = null
        if (had && hideNotification) {
            appContext?.let { PlaybackNotification.hide(it) }
        }
    }

    /** Converts the Cast volume (0.0..1.0) into the 0..100 percentage. */
    private fun syncCastVolume() {
        try {
            val session = activeSession() ?: return
            _volumePercent.value = ((session.volume * 100f).toInt()).coerceIn(0, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading the Cast volume", e)
        }
    }

    /** Initializes the session listener. Idempotent; call from MainActivity.onCreate. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        try {
            sessionManager = CastContext.getSharedInstance(appContext!!).sessionManager
            sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            Log.i(TAG, "CastManager initialized")
        } catch (e: Exception) {
            // Must not crash without Google Play Services or in the emulator.
            Log.w(TAG, "Google Cast not available: ${e.message}")
        }
    }

    /** Active Cast session, if any. */
    fun activeSession(): CastSession? = try {
        sessionManager?.currentCastSession
    } catch (e: Exception) {
        Log.w(TAG, "Error reading the Cast session", e)
        null
    }

    /**
     * Sets the Cast device volume (0..100 → 0.0..1.0).
     * The value is applied immediately; the volume listener then keeps the
     * UI synced with any device-side changes.
     */
    fun setVolume(percent: Int) {
        try {
            val session = activeSession() ?: return
            val clamped = percent.coerceIn(0, 100)
            session.setVolume(clamped / 100.0)
            _volumePercent.value = clamped
        } catch (e: Exception) {
            Log.w(TAG, "Error setting the Cast volume", e)
        }
    }

    /**
     * Loads a track on the Cast device: starts the local HTTP server,
     * builds a [MediaInfo] from the URL and uses remoteMediaClient.load.
     * Stops the local speaker (avoids double audio) and shows the persistent
     * "Streaming to <device>" notification.
     *
     * @return true if the load was started, false otherwise
     *         (the caller can then fall back to local playback).
     */
    fun castCurrent(context: Context, lullabyInput: Lullaby? = PlayerManager.current.value): Boolean {
        val lullaby = lullabyInput ?: run {
            Log.w(TAG, "castCurrent: no current track")
            return false
        }
        val castSession = activeSession() ?: run {
            Log.w(TAG, "castCurrent: no active Cast session")
            return false
        }
        val url = LocalMediaServer.localUrl(context, lullaby.fileName) ?: run {
            Log.e(TAG, "castCurrent: unable to build the local URL (no network?)")
            return false
        }
        Log.i(TAG, "Streaming ${lullaby.fileName} from $url")

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, formatDisplayName(lullaby.title))
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setContentType(LocalMediaServer.contentTypeFor(lullaby.fileName))
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()
        return try {
            val client = castSession.remoteMediaClient
            val pending = client?.load(mediaInfo)
            if (client == null || pending == null) {
                false
            } else {
                lastMediaInfo = mediaInfo
                // Avoids double audio: stops the local speaker (hides the
                // "Now playing" notification; we re-show it right after).
                PlayerManager.stop()
                _streaming.value = lullaby
                val dev = _deviceName.value
                PlaybackNotification.show(
                    context.applicationContext,
                    formatDisplayName(lullaby.title),
                    if (dev != null) "Streaming to $dev" else "Streaming"
                )
                pending.setResultCallback(
                    ResultCallback<RemoteMediaClient.MediaChannelResult> { result ->
                        if (!result.status.isSuccess) {
                            Log.w(TAG, "Media load failed: ${result.status.statusCode}")
                        }
                    }
                )
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during track cast", e)
            false
        }
    }

    /** Repeat-one for Cast streaming (reloads the track at the end of playback). */
    @Synchronized
    fun toggleCastLoop() {
        _castLoopEnabled.value = !_castLoopEnabled.value
    }

    /**
     * Stops the streaming on the Cast device: stops the remote media,
     * cleans the state and hides the notification.
     */
    fun stopStreaming() {
        try {
            val client = activeSession()?.remoteMediaClient
            if (client != null && client.hasMediaSession()) {
                client.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping the streaming", e)
        }
        clearStreaming(hideNotification = true)
    }
}
