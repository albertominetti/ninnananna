package com.alberto.ninnananna

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Mini server HTTP locale: serve i file in `filesDir/lullabies` sulla LAN,
 * così il ricevitore Chromecast (che NON può leggere lo storage del telefono)
 * può riprodurli da un URL `http://<IP-del-telefono>:<porta>/<fileName>`.
 *
 * - porta fissa 8975 (auto-fallback su una porta libera se occupata);
 * - supporta l'header `Range` con risposta 206 (fondamentale per Cast);
 * - [ensureStarted] è idempotente; ogni errore è catturato per non crashare
 *   in assenza di rete.
 */
object LocalMediaServer {

    private const val TAG = "LocalMediaServer"
    private const val PREFERRED_PORT = 8975
    private const val DIR_NAME = "lullabies"

    @Volatile
    private var server: LullabyHttpServer? = null

    private class LullabyHttpServer(
        port: Int,
        private val rootDir: File
    ) : NanoHTTPD("0.0.0.0", port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
            )
            // Normalizza il path: solo il nome file, niente traversal ("..").
            val name = uri.trimStart('/').substringAfterLast('/')
            if (name.isEmpty() || name.contains("..")) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
                )
            }
            val file = File(rootDir, name)
            if (!file.isFile || !file.canRead()) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
                )
            }
            return try {
                serveFileWithRange(session, file)
            } catch (e: IOException) {
                Log.w(TAG, "Errore servendo $name", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error"
                )
            }
        }

        /** Serve il file gestendo l'header Range (206 Partial Content). */
        private fun serveFileWithRange(session: IHTTPSession, file: File): Response {
            val length = file.length()
            val range = session.headers["range"]?.trim()
            val contentType = contentTypeFor(file.name)

            if (range == null) {
                return newFixedLengthResponse(
                    Response.Status.OK, contentType, FileInputStream(file), length
                )
            }

            // Formati attesi: "bytes=start-end" | "bytes=start-" | "bytes=-suffix"
            val match = Regex("""bytes=(\d*)-(\d*)""").find(range)
            if (match == null) {
                return newFixedLengthResponse(
                    Response.Status.OK, contentType, FileInputStream(file), length
                )
            }
            val (startStr, endStr) = match.destructured
            var start: Long
            var end: Long
            if (startStr.isEmpty()) {
                // Range negativo: ultimi N byte
                val suffix = endStr.toLongOrNull() ?: 0L
                start = (length - suffix).coerceAtLeast(0L)
                end = length - 1
            } else {
                start = startStr.toLongOrNull() ?: 0L
                end = if (endStr.isEmpty()) {
                    length - 1
                } else {
                    endStr.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
                }
            }
            if (start > end || start >= length) {
                return newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    contentType,
                    ""
                ).apply {
                    addHeader("Content-Range", "bytes */$length")
                    addHeader("Accept-Ranges", "bytes")
                }
            }
            val stream = FileInputStream(file).apply { skip(start) }
            val partialLength = end - start + 1
            return newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, contentType, stream, partialLength
            ).apply {
                addHeader("Content-Range", "bytes $start-$end/$length")
                addHeader("Accept-Ranges", "bytes")
            }
        }
    }

    /** Content-Type corretto in base all'estensione (bundled .mp3, download .m4a). */
    fun contentTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    /** Avvia il server se non è già attivo. Idempotente e sicuro in assenza di rete. */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (server?.isAlive == true) return
        val rootDir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!rootDir.isDirectory) {
            Log.w(TAG, "Directory inesistente: ${rootDir.absolutePath}")
            return
        }
        val started = try {
            val s = LullabyHttpServer(PREFERRED_PORT, rootDir)
            s.start()
            server = s
            true
        } catch (e: Exception) {
            // Porta occupata: prova una porta libera (auto).
            try {
                val s = LullabyHttpServer(0, rootDir)
                s.start()
                server = s
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Impossibile avviare il server HTTP locale", e2)
                false
            }
        }
        if (started) Log.i(TAG, "Server HTTP locale avviato sulla porta ${server?.listeningPort}")
    }

    /** URL LAN del file: `http://<IP-del-telefono>:<porta>/<fileName>`. */
    fun localUrl(context: Context, fileName: String): String? {
        return try {
            ensureStarted(context)
            val port = server?.listeningPort ?: return null
            val ip = lanIpv4() ?: run {
                Log.w(TAG, "Nessun indirizzo LAN trovato")
                return null
            }
            "http://$ip:$port/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Errore costruendo l'URL locale", e)
            null
        }
    }

    /** Primo IPv4 non-loopback, non 127.0.0.1. Mai "localhost". */
    private fun lanIpv4(): String? {
        return try {
            var found: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    found = addr.hostAddress
                    break
                }
                if (found != null) break
            }
            found
        } catch (e: Exception) {
            Log.w(TAG, "Errore nel recupero dell'IP LAN", e)
            null
        }
    }

    /** Ferma il server (best-effort). */
    @Synchronized
    fun stop() {
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Errore fermando il server", e)
        }
        server = null
    }
}
