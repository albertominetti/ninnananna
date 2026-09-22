package com.alberto.ninnananna

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A downloaded audio, saved in filesDir/lullabies.
 */
data class Lullaby(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    /** true for the lullabies preinstalled in the APK (cannot be deleted/renamed). */
    val isBundled: Boolean = false
) {
    val file: File get() = File(filePath)
    val fileName: String get() = file.name
}

/**
 * Download repository:
 * - resolves the best progressive audio stream with NewPipeExtractor;
 * - downloads the file with OkHttp into filesDir/lullabies;
 * - lists / renames / deletes local audios.
 *
 * Everything on-device: no backend, no external dependency.
 */
object DownloadRepository {

    private const val USER_AGENT = "Ninnananna/1.0 (Android; audio downloader)"
    private const val DIR_NAME = "lullabies"
    private const val EXTENSION = ".m4a"

    /**
     * Lullabies preinstalled in the APK (res/raw) and copied into
     * filesDir/lullabies on first launch (and on every launch, if missing):
     * - Brahms' "Wiegenlied" op.49 n.4 (1915 recording, public domain);
     * - white noise (5 min) to mask other noises;
     * - "enriched heart" (5 min): heartbeat + belly white noise + whoosh;
     * - "real ultrasound" (~6 min): cleaned-up and softened doppler recording;
     * - "brown heart" (5 min): brown noise + slow, gentle heartbeat.
     */
    private val BUNDLED_LULLABIES = listOf(
        "brahms_lullaby.mp3" to R.raw.brahms_lullaby,
        "white_noise.mp3" to R.raw.white_noise,
        "cuore_arricchito.mp3" to R.raw.cuore_arricchito,
        "ecografia_vera.mp3" to R.raw.ecografia_vera,
        "cuore_marrone.mp3" to R.raw.cuore_marrone
    )

    /** Sounds preinstalled in previous versions and no longer included: they are
     *  removed automatically (they were protected, so never modified). */
    private val OBSOLETE_BUNDLED_FILE_NAMES = setOf("womb_heartbeat.mp3")

    /** Names of the preinstalled files (to identify bundled items). */
    private val BUNDLED_FILE_NAMES = BUNDLED_LULLABIES.map { it.first }.toSet()

    /** true if the file is one of the preinstalled (protected) lullabies. */
    fun isBundledFile(file: File): Boolean = file.name in BUNDLED_FILE_NAMES

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        // The extractor's Downloader uses the same OkHttpClient used for the download
        NewPipe.init(object : Downloader() {
            override fun execute(
                request: org.schabi.newpipe.extractor.downloader.Request
            ): org.schabi.newpipe.extractor.downloader.Response {
                return executeOnOkHttp(request)
            }
        })
        initialized = true
    }

    private fun executeOnOkHttp(
        request: org.schabi.newpipe.extractor.downloader.Request
    ): org.schabi.newpipe.extractor.downloader.Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.header(name, value) }
        }

        val method = request.httpMethod()
        val body = request.dataToSend()
        when {
            body != null -> builder.method(method, body.toRequestBody(null))
            method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true) ->
                builder.method(method, null)
            else -> builder.method(method, ByteArray(0).toRequestBody(null))
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val headers: Map<String, List<String>> = response.headers.toMultimap()
            return org.schabi.newpipe.extractor.downloader.Response(
                response.code,
                response.message,
                headers,
                responseBody,
                response.request.url.toString()
            )
        }
    }

    fun lullabiesDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Aligns the preinstalled sounds in the filesDir/lullabies folder:
     * - removes those no longer included (e.g. the old womb heartbeat);
     * - copies the missing ones (first launch, or update with new sounds).
     *
     * @return true if something was copied or removed.
     */
    suspend fun ensureBundledLullabies(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dir = lullabiesDir(context)
        var changed = false

        for (oldName in OBSOLETE_BUNDLED_FILE_NAMES) {
            val stale = File(dir, oldName)
            if (stale.exists() && stale.delete()) changed = true
        }

        for ((fileName, resId) in BUNDLED_LULLABIES) {
            val target = File(dir, fileName)
            if (target.exists()) continue
            try {
                context.resources.openRawResource(resId).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                changed = true
            } catch (e: Exception) {
                target.delete()
            }
        }
        changed
    }

    // ---------------- Elenco ----------------

    suspend fun listLullabies(context: Context): List<Lullaby> = withContext(Dispatchers.IO) {
        val dir = lullabiesDir(context)
        // Align the preinstalled sounds: copy the missing ones, remove the old ones.
        ensureBundledLullabies(context)
        dir.listFiles { f -> f.isFile }
            ?.sortedBy { it.name.lowercase() }
            ?.mapNotNull { file ->
                Lullaby(
                    id = file.absolutePath,
                    title = file.nameWithoutExtension.ifBlank { file.name },
                    filePath = file.absolutePath,
                    durationMs = readDurationMs(file),
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isBundled = isBundledFile(file)
                )
            }
            ?: emptyList()
    }

    private fun readDurationMs(file: File): Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        retriever.release()
        ms
    } catch (e: Exception) {
        0L
    }

    // ---------------- Download ----------------

    /**
     * Resolves the best audio stream with NewPipeExtractor and downloads it into filesDir/lullabies.
     * [onProgress] receives a 0..1 value (network dispatcher, thread-safe for Compose state).
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit = {}
    ): Lullaby? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val extractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val title = runCatching { extractor.name }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "audio"
            val durationSec = runCatching { extractor.length }.getOrDefault(0L)

            val stream = resolveBestAudioStream(extractor)
                ?: throw DownloadException(
                    context.getString(R.string.err_no_stream)
                )

            val dir = lullabiesDir(context)
            val target = uniqueFile(dir, sanitizeFileName(title))
            val streamUrl = stream.url
                ?: throw DownloadException(context.getString(R.string.err_no_url))
            downloadStream(context, streamUrl, target, onProgress)

            Lullaby(
                id = target.absolutePath,
                title = target.nameWithoutExtension,
                filePath = target.absolutePath,
                durationMs = durationSec * 1000,
                sizeBytes = target.length(),
                lastModified = target.lastModified()
            )
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException(
                context.getString(R.string.err_network, e.message ?: ""), e
            )
        } catch (e: ExtractionException) {
            throw DownloadException(
                context.getString(R.string.err_parse, e.message ?: ""), e
            )
        } catch (e: Exception) {
            throw DownloadException(
                context.getString(R.string.err_download, e.message ?: ""), e
            )
        }
    }

    /**
     * Chooses the "best progressive audio stream": prefers progressive HTTP
     * audios (YouTube itag 139/140/141, M4A/AAC), otherwise the highest
     * average-bitrate audio stream.
     */
    private fun resolveBestAudioStream(extractor: StreamExtractor): Stream? {
        val audioStreams: List<AudioStream> =
            runCatching { extractor.audioStreams }.getOrDefault(emptyList())
        if (audioStreams.isEmpty()) return null

        val progressive = audioStreams.filter { stream ->
            stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP ||
                stream.id in setOf("139", "140", "141")
        }
        val pool = progressive.ifEmpty { audioStreams }
        return pool.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
    }

    private fun sanitizeFileName(title: String): String {
        val cleaned = title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { "audio" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name + EXTENSION)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${name}_$i$EXTENSION")
            i++
        }
        return candidate
    }

    private fun downloadStream(
        context: Context,
        streamUrl: String,
        target: File,
        onProgress: (Float) -> Unit
    ) {
        val request = okhttp3.Request.Builder()
            .url(streamUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    context.getString(R.string.err_http, response.code)
                )
            }
            val body = response.body
                ?: throw DownloadException(context.getString(R.string.err_no_body))
            val total = body.contentLength()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L

            target.outputStream().buffered().use { out ->
                body.byteStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(downloaded.toFloat() / total.toFloat())
                        }
                    }
                }
            }
            if (total > 0 && downloaded < total) {
                throw DownloadException(
                    context.getString(R.string.err_incomplete, downloaded, total)
                )
            }
        }
    }

    // ---------------- File management ----------------

    suspend fun rename(context: Context, lullaby: Lullaby, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            // The preinstalled (bundled) ones are never renamed.
            if (lullaby.isBundled) return@withContext false
            val name = newTitle.trim().ifBlank { return@withContext false }
            val old = File(lullaby.filePath)
            if (!old.exists()) return@withContext false
            val candidate = File(old.parentFile, name + EXTENSION)
            if (candidate.exists() && candidate.absolutePath != old.absolutePath) {
                return@withContext false
            }
            old.renameTo(candidate)
        }

    suspend fun delete(context: Context, lullaby: Lullaby): Boolean =
        withContext(Dispatchers.IO) {
            // The preinstalled (bundled) ones are never deleted.
            if (lullaby.isBundled) return@withContext false
            File(lullaby.filePath).delete()
        }

    /**
     * Deletes all downloaded files (the preinstalled bundled ones are always
     * kept); returns the number of deleted files.
     */
    suspend fun resetAll(context: Context): Int = withContext(Dispatchers.IO) {
        val dir = lullabiesDir(context)
        val files = dir.listFiles { f -> f.isFile } ?: emptyArray()
        var deleted = 0
        files.forEach { file ->
            if (!isBundledFile(file) && file.delete()) deleted++
        }
        deleted
    }
}

/**
 * Formats the name of an audio for **display only** (the files on disk are
 * not renamed):
 * - removes the .mp3/.m4a extension;
 * - replaces `_` and `-` with spaces;
 * - trims and removes multiple spaces;
 * - simple Title Case (first letter of each word uppercase, the rest
 *   unchanged: any acronyms are left untouched).
 */
/** Translated names of the preinstalled sounds (display only). */
private val BUNDLED_DISPLAY_NAMES: Map<String, Int> = mapOf(
    "brahms_lullaby" to R.string.bundled_brahms,
    "white_noise" to R.string.bundled_white_noise,
    "cuore_arricchito" to R.string.bundled_enriched_heart,
    "ecografia_vera" to R.string.bundled_real_ultrasound,
    "cuore_marrone" to R.string.bundled_brown_heart
)

fun formatDisplayName(context: Context, fileName: String): String {
    var name = fileName.trim()
    for (ext in DISPLAY_EXTENSIONS) {
        if (name.endsWith(ext, ignoreCase = true)) {
            name = name.dropLast(ext.length)
            break
        }
    }
    // Translated names for the preinstalled sounds.
    BUNDLED_DISPLAY_NAMES[name.lowercase()]?.let { resId ->
        return context.getString(resId)
    }
    val words = name
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    return words.joinToString(" ") { word ->
        word.replaceFirstChar { first ->
            if (first.isLowerCase()) first.uppercase() else first.toString()
        }
    }
}

private val DISPLAY_EXTENSIONS = listOf(".mp3", ".m4a")