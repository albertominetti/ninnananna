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
 * Orchestra lo streaming verso un dispositivo Google Cast / Chromecast:
 * - espone [connected], [deviceName] e [volumePercent] osservabili dalla UI;
 * - [streaming] = brano attualmente in streaming sul dispositivo (null = nessuno);
 * - [castLoopEnabled] = repeat-one per lo streaming;
 * - [castCurrent] avvia il server HTTP locale e carica il brano corrente
 *   sul ricevitore distante (Default Media Receiver), mettendo in pausa
 *   lo speaker locale per evitare doppio audio e mostrando la notifica
 *   persistente "In streaming su <device>";
 * - quando connesso, la barra del volume controlla il volume del dispositivo
 *   Cast tramite [setVolume] (il Cast notifica i cambi via [volumeListener]).
 */
object CastManager {

    private const val TAG = "CastManager"

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _volumePercent = MutableStateFlow(100)
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    /** Brano attualmente in streaming sul dispositivo Cast (null = nessuno). */
    private val _streaming = MutableStateFlow<Lullaby?>(null)
    val streaming: StateFlow<Lullaby?> = _streaming.asStateFlow()

    /** Repeat-one attivo per lo streaming Cast. */
    private val _castLoopEnabled = MutableStateFlow(false)
    val castLoopEnabled: StateFlow<Boolean> = _castLoopEnabled.asStateFlow()

    @Volatile
    private var initialized = false

    private var appContext: Context? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null
    private var lastMediaInfo: MediaInfo? = null

    /** Sincronizza la % mostrata dalla UI col volume reale del dispositivo Cast. */
    private val volumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            syncCastVolume()
        }
    }

    /** Gestione fine riproduzione: repeat-one oppure pulizia + notifica via. */
    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val s = activeSession()?.remoteMediaClient?.mediaStatus ?: return
            if (s.playerState != MediaStatus.PLAYER_STATE_IDLE) return
            when (s.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> {
                    val last = lastMediaInfo
                    if (_castLoopEnabled.value && last != null) {
                        // Repeat-one: ricarica lo stesso brano.
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
            Log.i(TAG, "Sessione Cast avviata")
            onSessionActive(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.i(TAG, "Sessione Cast ripresa")
            onSessionActive(session)
        }

        override fun onSessionEnding(session: CastSession) {
            // Nient'altro: la pulizia completa avviene in onSessionEnded.
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.i(TAG, "Sessione Cast terminata (error=$error)")
            cleanup()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Avvio sessione Cast fallito (error=$error)")
            cleanup()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Ripresa sessione Cast fallita (error=$error)")
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
        Log.i(TAG, "Connesso a: ${_deviceName.value}")
        syncCastVolume()
        // Se c'è già un brano corrente, caricalo subito sul dispositivo.
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

    /** Ferma lo stato di streaming lato app (senza toccare il device remoto). */
    private fun clearStreaming(hideNotification: Boolean) {
        val had = _streaming.value != null
        _streaming.value = null
        lastMediaInfo = null
        if (had && hideNotification) {
            appContext?.let { PlaybackNotification.hide(it) }
        }
    }

    /** Converte il volume del Cast (0.0..1.0) nella percentuale 0..100. */
    private fun syncCastVolume() {
        try {
            val session = activeSession() ?: return
            _volumePercent.value = ((session.volume * 100f).toInt()).coerceIn(0, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Errore leggendo il volume Cast", e)
        }
    }

    /** Inizializza il listener di sessione. Idempotente; da chiamare in MainActivity.onCreate. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        try {
            sessionManager = CastContext.getSharedInstance(appContext!!).sessionManager
            sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            Log.i(TAG, "CastManager inizializzato")
        } catch (e: Exception) {
            // Senza Google Play Services o in emulatore non deve crashare.
            Log.w(TAG, "Google Cast non disponibile: ${e.message}")
        }
    }

    /** Sessione Cast attiva, se presente. */
    fun activeSession(): CastSession? = try {
        sessionManager?.currentCastSession
    } catch (e: Exception) {
        Log.w(TAG, "Errore leggendo la sessione Cast", e)
        null
    }

    /**
     * Imposta il volume del dispositivo Cast (0..100 → 0.0..1.0).
     * Il valore viene applicato subito; il listener di volume tiene poi
     * sincronizzata la UI con eventuali cambi lato dispositivo.
     */
    fun setVolume(percent: Int) {
        try {
            val session = activeSession() ?: return
            val clamped = percent.coerceIn(0, 100)
            session.setVolume(clamped / 100.0)
            _volumePercent.value = clamped
        } catch (e: Exception) {
            Log.w(TAG, "Errore impostando il volume Cast", e)
        }
    }

    /**
     * Carica un brano sul dispositivo Cast: avvia il server HTTP locale,
     * costruisce una [MediaInfo] dall'URL e usa remoteMediaClient.load.
     * Ferma lo speaker locale (evita doppio audio) e mostra la notifica
     * persistente "In streaming su <device>".
     *
     * @return true se il caricamento è stato avviato, false altrimenti
     *         (il chiamante può così ripiegare sulla riproduzione locale).
     */
    fun castCurrent(context: Context, lullabyInput: Lullaby? = PlayerManager.current.value): Boolean {
        val lullaby = lullabyInput ?: run {
            Log.w(TAG, "castCurrent: nessun brano corrente")
            return false
        }
        val castSession = activeSession() ?: run {
            Log.w(TAG, "castCurrent: nessuna sessione Cast attiva")
            return false
        }
        val url = LocalMediaServer.localUrl(context, lullaby.fileName) ?: run {
            Log.e(TAG, "castCurrent: impossibile costruire l'URL locale (rete assente?)")
            return false
        }
        Log.i(TAG, "Streaming ${lullaby.fileName} da $url")

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
                // Evita doppio audio: ferma lo speaker locale (nasconde la
                // notifica "In riproduzione"; la ri-mostriamo subito dopo).
                PlayerManager.stop()
                _streaming.value = lullaby
                val dev = _deviceName.value
                PlaybackNotification.show(
                    context.applicationContext,
                    formatDisplayName(lullaby.title),
                    if (dev != null) "In streaming su $dev" else "In streaming"
                )
                pending.setResultCallback(
                    ResultCallback<RemoteMediaClient.MediaChannelResult> { result ->
                        if (!result.status.isSuccess) {
                            Log.w(TAG, "Media load non riuscito: ${result.status.statusCode}")
                        }
                    }
                )
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il cast del brano", e)
            false
        }
    }

    /** Repeat-one per lo streaming Cast (ricarica il brano a fine riproduzione). */
    @Synchronized
    fun toggleCastLoop() {
        _castLoopEnabled.value = !_castLoopEnabled.value
    }

    /**
     * Stop dello streaming sul dispositivo Cast: ferma il media remoto,
     * pulisce lo stato e nasconde la notifica.
     */
    fun stopStreaming() {
        try {
            val client = activeSession()?.remoteMediaClient
            if (client != null && client.hasMediaSession()) {
                client.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore nello stop dello streaming", e)
        }
        clearStreaming(hideNotification = true)
    }
}
