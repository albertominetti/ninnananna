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
 * Un audio scaricato, salvato in filesDir/lullabies.
 */
data class Lullaby(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    /** true per le ninnenanne preinstallate nell'APK (non cancellabili/rinominabili). */
    val isBundled: Boolean = false
) {
    val file: File get() = File(filePath)
    val fileName: String get() = file.name
}

/**
 * Repository di download:
 * - risolve il miglior stream audio progressivo con NewPipeExtractor;
 * - scarica il file con OkHttp in filesDir/lullabies;
 * - elenca / rinomina / elimina gli audio locali.
 *
 * Tutto on-device: nessun backend, nessuna dipendenza esterna.
 */
object DownloadRepository {

    private const val USER_AGENT = "Ninnananna/1.0 (Android; audio downloader)"
    private const val DIR_NAME = "lullabies"
    private const val EXTENSION = ".m4a"

    /**
     * Ninnenanne preinstallate nell'APK (res/raw) e copiate in
     * filesDir/lullabies al primo avvio (e a ogni avvio, se mancanti):
     * - Brahms "Wiegenlied" op.49 n.4 (registrazione 1915, pubblico dominio);
     * - rumore bianco (5 min) per mascherare i rumori;
     * - "cuore arricchito" (5 min): battito + rumore bianco della pancia + whoosh;
     * - "ecografia vera" (~6 min): registrazione doppler ripulita e ammorbidita;
     * - "cuore marrone" (5 min): rumore marrone + battito lento e dolce.
     */
    private val BUNDLED_LULLABIES = listOf(
        "brahms_lullaby.mp3" to R.raw.brahms_lullaby,
        "white_noise.mp3" to R.raw.white_noise,
        "cuore_arricchito.mp3" to R.raw.cuore_arricchito,
        "ecografia_vera.mp3" to R.raw.ecografia_vera,
        "cuore_marrone.mp3" to R.raw.cuore_marrone
    )

    /** Suoni preinstallati in versioni precedenti e non più inclusi: vengono
     *  rimossi automaticamente (erano protetti, quindi mai modificati). */
    private val OBSOLETE_BUNDLED_FILE_NAMES = setOf("womb_heartbeat.mp3")

    /** Nomi dei file preinstallati (per identificare gli item bundled). */
    private val BUNDLED_FILE_NAMES = BUNDLED_LULLABIES.map { it.first }.toSet()

    /** true se il file è una delle ninnenanne preinstallate (protette). */
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
        // Il Downloader dell'estrattore usa lo stesso OkHttpClient del download
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
     * Allinea i suoni preinstallati nella cartella filesDir/lullabies:
     * - rimuove quelli non più inclusi (es. il vecchio battito uterino);
     * - copia quelli mancanti (primo avvio, oppure aggiornamento con nuovi suoni).
     *
     * @return true se qualcosa è stato copiato o rimosso.
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
        // Allinea i suoni preinstallati: copia i mancanti, rimuove i vecchi.
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
     * Risolve il miglior stream audio con NewPipeExtractor e lo scarica in filesDir/lullabies.
     * [onProgress] riceve un valore 0..1 (dispatcher di rete, thread-safe per lo stato Compose).
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
                ?: throw DownloadException("Nessuno stream audio disponibile per questo video.")

            val dir = lullabiesDir(context)
            val target = uniqueFile(dir, sanitizeFileName(title))
            val streamUrl = stream.url
                ?: throw DownloadException("Stream senza URL del file audio.")
            downloadStream(streamUrl, target, onProgress)

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
            throw DownloadException("Errore di rete durante il download: ${e.message}", e)
        } catch (e: ExtractionException) {
            throw DownloadException("Impossibile analizzare il video: ${e.message}", e)
        } catch (e: Exception) {
            throw DownloadException("Errore durante il download: ${e.message}", e)
        }
    }

    /**
     * Sceglie il "miglior stream audio progressivo": preferisce gli audio
     * progressivi HTTP (itag YouTube 139/140/141, M4A/AAC), altrimenti il
     * flusso audio con bitrate medio più alto.
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
                throw DownloadException("Risposta HTTP ${response.code} durante il download.")
            }
            val body = response.body ?: throw DownloadException("Risposta senza contenuto.")
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
                throw DownloadException("Download incompleto ($downloaded/${total} byte).")
            }
        }
    }

    // ---------------- Gestione file ----------------

    suspend fun rename(context: Context, lullaby: Lullaby, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            // Le preinstallate (bundled) non si rinominano mai.
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
            // Le preinstallate (bundled) non si eliminano mai.
            if (lullaby.isBundled) return@withContext false
            File(lullaby.filePath).delete()
        }

    /**
     * Elimina tutti i file scaricati (le preinstallate bundled sono sempre
     * conservate); ritorna il numero di file eliminati.
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
 * Formatta il nome di un audio per la **sola visualizzazione** (i file su
 * disco non vengono rinominati):
 * - rimuove l'estensione .mp3/.m4a;
 * - sostituisce `_` e `-` con spazi;
 * - trim e rimozione di spazi multipli;
 * - Title Case semplice (prima lettera di ogni parola maiuscola, il resto
 *   invariato: gli acronimi eventuali non vengono toccati).
 */
/** Nomi in italiano dei suoni preinstallati (solo per la visualizzazione). */
private val BUNDLED_DISPLAY_NAMES_IT = mapOf(
    "brahms_lullaby" to "Ninna nanna di Brahms",
    "white_noise" to "Rumore bianco",
    "cuore_arricchito" to "Cuore arricchito",
    "ecografia_vera" to "Ecografia vera",
    "cuore_marrone" to "Cuore marrone"
)

fun formatDisplayName(fileName: String): String {
    var name = fileName.trim()
    for (ext in DISPLAY_EXTENSIONS) {
        if (name.endsWith(ext, ignoreCase = true)) {
            name = name.dropLast(ext.length)
            break
        }
    }
    // Nomi in italiano per i suoni preinstallati.
    BUNDLED_DISPLAY_NAMES_IT[name.lowercase()]?.let { return it }
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