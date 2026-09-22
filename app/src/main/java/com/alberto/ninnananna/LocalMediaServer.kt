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
 * Mini local HTTP server: serves the files in `filesDir/lullabies` on the LAN,
 * so the Chromecast receiver (which CANNOT read the phone's storage) can play
 * them from a URL `http://<phone-IP>:<port>/<fileName>`.
 *
 * - fixed port 8975 (auto-fallback to a free port if busy);
 * - supports the `Range` header with a 206 response (essential for Cast);
 * - [ensureStarted] is idempotent; every error is caught so it does not crash
 *   without a network.
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
            // Normalizes the path: only the file name, no traversal ("..").
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
                Log.w(TAG, "Error serving $name", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error"
                )
            }
        }

        /** Serves the file handling the Range header (206 Partial Content). */
        private fun serveFileWithRange(session: IHTTPSession, file: File): Response {
            val length = file.length()
            val range = session.headers["range"]?.trim()
            val contentType = contentTypeFor(file.name)

            if (range == null) {
                return newFixedLengthResponse(
                    Response.Status.OK, contentType, FileInputStream(file), length
                )
            }

            // Expected formats: "bytes=start-end" | "bytes=start-" | "bytes=-suffix"
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
                // Negative range: last N bytes
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

    /** Correct Content-Type based on the extension (bundled .mp3, downloaded .m4a). */
    fun contentTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    /** Starts the server if not already active. Idempotent and safe without a network. */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (server?.isAlive == true) return
        val rootDir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!rootDir.isDirectory) {
            Log.w(TAG, "Nonexistent directory: ${rootDir.absolutePath}")
            return
        }
        val started = try {
            val s = LullabyHttpServer(PREFERRED_PORT, rootDir)
            s.start()
            server = s
            true
        } catch (e: Exception) {
            // Port busy: try a free port (auto).
            try {
                val s = LullabyHttpServer(0, rootDir)
                s.start()
                server = s
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Unable to start the local HTTP server", e2)
                false
            }
        }
        if (started) Log.i(TAG, "Local HTTP server started on port ${server?.listeningPort}")
    }

    /** LAN URL of the file: `http://<phone-IP>:<port>/<fileName>`. */
    fun localUrl(context: Context, fileName: String): String? {
        return try {
            ensureStarted(context)
            val port = server?.listeningPort ?: return null
            val ip = lanIpv4() ?: run {
                Log.w(TAG, "No LAN address found")
                return null
            }
            "http://$ip:$port/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Error building the local URL", e)
            null
        }
    }

    /** First non-loopback IPv4, not 127.0.0.1. Never "localhost". */
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
            Log.w(TAG, "Error retrieving the LAN IP", e)
            null
        }
    }

    /** Stops the server (best-effort). */
    @Synchronized
    fun stop() {
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping the server", e)
        }
        server = null
    }
}
