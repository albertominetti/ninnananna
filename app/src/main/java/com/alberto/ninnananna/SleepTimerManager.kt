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
 * Sleep timer globale: allo scadere ferma la riproduzione e rimuove il
 * flag "mantieni schermo attivo" (impostato in [SettingsStore]).
 *
 * I timeout disponibili sono scelti dalla UI (15 min, 30 min, 1h, 2h, 3h,
 * 4h oppure off). Il conto alla rovescia sopravvive alla chiusura della
 * schermata finché il processo dell'app è vivo.
 */
object SleepTimerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _remainingMillis = MutableStateFlow<Long?>(null)

    /** Millisecondi rimanenti, oppure null quando il timer è spento. */
    val remainingMillis: StateFlow<Long?> = _remainingMillis.asStateFlow()

    private var job: Job? = null

    /**
     * Avvia (o riavvia) il timer con la durata indicata.
     * [context] serve solo allo scadere per rimuovere il keep-screen-on.
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
            // Allo scadere: ferma l'audio e togli il "mantieni schermo attivo".
            PlayerManager.stop()
            CastManager.stopStreaming()
            SettingsStore.setKeepScreenOn(context, false)
            _remainingMillis.value = null
        }
        return true
    }

    /** Spegne il timer senza azioni (la riproduzione continua). */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
        _remainingMillis.value = null
    }
}