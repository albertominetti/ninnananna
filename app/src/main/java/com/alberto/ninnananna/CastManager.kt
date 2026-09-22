package com.alberto.ninnananna

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
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
 * - [castCurrent] avvia il server HTTP locale e carica il brano corrente
 *   sul ricevitore distante (Default Media Receiver), mettendo in pausa
 *   lo speaker locale per evitare doppio audio;
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

    @Volatile
    private var initialized = false

    private var appContext: Context? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null

    /** Sincronizza la % mostrata dalla UI col volume reale del dispositivo Cast. */
    private val volumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            syncCastVolume()
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
        _connected.value = true
        _deviceName.value = session.castDevice?.friendlyName ?: session.castDevice?.modelName
        Log.i(TAG, "Connesso a: ${_deviceName.value}")
        syncCastVolume()
        // Se c'è già un brano corrente, caricarlo subito sul dispositivo.
        val current = PlayerManager.current.value ?: return
        castCurrent(appContext ?: return, current)
    }

    private fun cleanup() {
        val s = currentSession
        if (s != null) {
            runCatching { s.removeCastListener(volumeListener) }
        }
        currentSession = null
        _connected.value = false
        _deviceName.value = null
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
     * Carica il brano corrente sul dispositivo Cast: avvia il server HTTP
     * locale, costruisce una [MediaInfo] dall'URL e usa remoteMediaClient.load.
     * Ferma lo speaker locale per evitare doppio audio.
     */
    fun castCurrent(context: Context, lullabyInput: Lullaby? = PlayerManager.current.value) {
        val lullaby = lullabyInput ?: run {
            Log.w(TAG, "castCurrent: nessun brano corrente")
            return
        }
        val castSession = activeSession() ?: run {
            Log.w(TAG, "castCurrent: nessuna sessione Cast attiva")
            return
        }
        val url = LocalMediaServer.localUrl(context, lullaby.fileName) ?: run {
            Log.e(TAG, "castCurrent: impossibile costruire l'URL locale (rete assente?)")
            return
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
        try {
            val pending = castSession.remoteMediaClient?.load(mediaInfo)
            if (pending != null) {
                pending.setResultCallback(
                    ResultCallback<RemoteMediaClient.MediaChannelResult> { result ->
                        val status = result.status
                        if (!status.isSuccess) {
                            Log.w(TAG, "Media load non riuscito: ${status.statusCode}")
                        }
                    }
                )
            }
            // Evita doppio audio: ferma il player locale (la notifica viene nascosta).
            PlayerManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il cast del brano", e)
        }
    }

    /**
     * Stop dello streaming sul dispositivo Cast (best-effort). La UI usa di
     * solito il MediaRouteButton, che gestisce la disconnessione da solo.
     */
    fun stopStreaming() {
        try {
            activeSession()?.remoteMediaClient?.apply {
                if (hasMediaSession()) stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore nello stop dello streaming", e)
        }
    }
}