package com.alberto.ninnananna

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Bus per gli Intent in ingresso (ACTION_SEND / ACTION_VIEW): la UI
 * (LullabyList) osserva [pendingYoutubeUrl] per pre-compilare il campo
 * e avviare il download.
 */
object MainActivityEvents {
    val pendingYoutubeUrl = MutableStateFlow<String?>(null)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeKeepScreenOn()
        handleIntent(intent)
        setContent { NinnanannaApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Rilascia il player quando l'activity viene chiusa
        PlayerManager.release()
    }

    private fun handleIntent(intent: Intent?) {
        val url = parseYoutubeUrl(intent) ?: return
        MainActivityEvents.pendingYoutubeUrl.value = url
    }

    private fun observeKeepScreenOn() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsStore.keepScreenOn(applicationContext).collect { enabled ->
                    if (enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    companion object {
        private val YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
        )

        /**
         * Estrae un link YouTube da un Intent SEND (text/plain) o VIEW.
         */
        fun parseYoutubeUrl(intent: Intent?): String? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW ->
                    intent.data?.toString()?.takeIf { isYoutubeUrl(it) }

                Intent.ACTION_SEND -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                    findYoutubeUrl(text)
                }

                else -> null
            }
        }

        private fun isYoutubeUrl(url: String): Boolean {
            return runCatching {
                val uri = Uri.parse(url)
                (uri.scheme == "https" || uri.scheme == "http") &&
                    uri.host?.lowercase() in YOUTUBE_HOSTS
            }.getOrDefault(false)
        }

        private fun findYoutubeUrl(text: String): String? {
            val urls = Regex("""https?://[^\s<>"']+""").findAll(text)
            return urls.map { it.value }.firstOrNull { isYoutubeUrl(it) }
        }
    }
}