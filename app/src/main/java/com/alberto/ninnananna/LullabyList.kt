package com.alberto.ninnananna

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Icona "Stop" custom (un quadrato pieno), non presente nel set core di material-icons
private val StopIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Stop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 6f)
            horizontalLineTo(18f)
            verticalLineTo(18f)
            horizontalLineTo(6f)
            close()
        }
    }.build()
}

/**
 * Schermata principale: lista audio scaricati/preinstallati, download in
 * background via WorkManager (notifica foreground), pulsante "Add from YT"
 * e FAB che aprono il bottom sheet per incollare un link YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LullabyList(onOpenSettings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var urlText by rememberSaveable { mutableStateOf("") }
    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }

    // Bottom sheet "Add from YT"
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sheetUrl by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    var renameTarget by remember { mutableStateOf<Lullaby?>(null) }
    var renameText by remember { mutableStateOf("") }

    val current by PlayerManager.current.collectAsState()
    val playing by PlayerManager.playing.collectAsState()

    val refresh: () -> Unit = {
        scope.launch {
            lullabies = DownloadRepository.listLullabies(context)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // ---------- Download in background (WorkManager) ----------
    val workManager = remember { WorkManager.getInstance(context) }
    val downloadWorkInfos by workManager
        .getWorkInfosByTagFlow(DownloadLullabyWorker.TAG_DOWNLOAD)
        .collectAsState(initial = emptyList())
    val activeDownloads = downloadWorkInfos.filterNot { it.state.isFinished }

    // Quando un download termina: aggiorna la lista e segnala eventuali errori.
    var seenFinished by remember { mutableStateOf(setOf<UUID>()) }
    LaunchedEffect(downloadWorkInfos) {
        val finishedNow = downloadWorkInfos.filter { it.state.isFinished }
        val newOnes = finishedNow.filter { it.id !in seenFinished }
        if (newOnes.isNotEmpty()) {
            refresh()
            val failed = newOnes.firstOrNull { it.state == WorkInfo.State.FAILED }
            if (failed != null) {
                val msg = failed.outputData.getString(DownloadLullabyWorker.KEY_ERROR)
                    ?: "Download fallito."
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
        seenFinished = seenFinished + finishedNow.map { it.id }
    }

    // Permesso notifiche (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Accoda il download in background (WorkManager) e dà un feedback via snackbar.
    val enqueueDownload: (String) -> Unit = { raw ->
        val url = raw.trim()
        if (url.isNotBlank()) {
            requestNotificationPermissionIfNeeded()
            DownloadLullabyWorker.enqueue(context, url)
            urlText = ""
            sheetUrl = ""
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Download avviato in background: avanzamento nella notifica."
                )
            }
        }
    }

    // Intent esterni (Condividi -> NinnaNanna / link): avvia subito il download.
    val incoming by MainActivityEvents.pendingYoutubeUrl.collectAsState()
    var lastAutoUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(incoming) {
        val url = incoming?.trim()
        if (!url.isNullOrBlank() && url != lastAutoUrl) {
            lastAutoUrl = url
            MainActivityEvents.pendingYoutubeUrl.value = null
            enqueueDownload(url)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NinnaNanna") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add from YT")
            }
        },
        bottomBar = {
            current?.let { lullaby ->
                MiniPlayerBar(
                    lullaby = lullaby,
                    playing = playing,
                    onStop = { PlayerManager.stop() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Incolla un link YouTube: il download avviene in background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            TextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL YouTube") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { enqueueDownload(urlText) })
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { enqueueDownload(urlText) },
                enabled = urlText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scarica audio")
            }
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Add from YT")
            }

            Spacer(Modifier.height(16.dp))

            // Download attivi in cima alla lista (download in secondo piano)
            if (activeDownloads.isNotEmpty()) {
                Text(
                    text = "Download in corso (${activeDownloads.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeDownloads.sortedBy { it.id }.forEach { info ->
                        ActiveDownloadCard(info)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = "Audio (${lullabies.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))

            if (lullabies.isEmpty() && activeDownloads.isEmpty()) {
                Text(
                    text = "Nessun audio. Aggiungi una ninnananna da YouTube " +
                        "oppure le preinstallate (Brahms, white noise, battito " +
                        "uterino) vengono copiate al primo avvio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lullabies, key = { it.id }) { lullaby ->
                    LullabyRow(
                        lullaby = lullaby,
                        isCurrent = current?.id == lullaby.id,
                        isPlaying = playing && current?.id == lullaby.id,
                        onPlay = { PlayerManager.play(context, lullaby) },
                        onStop = { PlayerManager.stop() },
                        onRename = {
                            renameTarget = lullaby
                            renameText = lullaby.title
                        },
                        onDelete = {
                            scope.launch {
                                if (current?.filePath == lullaby.filePath) {
                                    PlayerManager.stop()
                                }
                                DownloadRepository.delete(context, lullaby)
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }

    // Bottom sheet "Add from YT" (FAB e pulsante)
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Aggiungi da YouTube",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Incolla un link YouTube: l'audio viene scaricato in " +
                        "background con una notifica di avanzamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = sheetUrl,
                    onValueChange = { sheetUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL YouTube") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        enqueueDownload(sheetUrl)
                        showAddSheet = false
                    })
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        enqueueDownload(sheetUrl)
                        showAddSheet = false
                    },
                    enabled = sheetUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scarica in background")
                }
            }
        }
    }

    // Dialog di rinomina
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rinomina audio") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nuovo nome") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim().ifBlank { null }
                    renameTarget = null
                    if (newName != null) {
                        scope.launch {
                            val ok = DownloadRepository.rename(context, target, newName)
                            if (ok) {
                                if (PlayerManager.current.value?.filePath == target.filePath) {
                                    PlayerManager.stop()
                                }
                                refresh()
                            } else {
                                snackbarHostState.showSnackbar(
                                    "Rinomina non riuscita: nome già esistente o non valido."
                                )
                            }
                        }
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Annulla") }
            }
        )
    }
}

/**
 * Card mostrata in cima alla lista per ogni download attivo:
 * avanzamento live dalla WorkInfo.
 */
@Composable
private fun ActiveDownloadCard(info: WorkInfo) {
    val progress = info.progress.getFloat(DownloadLullabyWorker.KEY_PROGRESS, 0f)
    val text = when (info.state) {
        WorkInfo.State.ENQUEUED -> "In coda: in attesa della connessione…"
        WorkInfo.State.RUNNING -> "Download in corso… ${(progress * 100).toInt()}%"
        else -> "Preparazione…"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LullabyRow(
    lullaby: Lullaby,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (isPlaying) onStop() else onPlay() }) {
                Icon(
                    imageVector = if (isPlaying) StopIcon else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lullaby.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDuration(lullaby.durationMs)}  •  " +
                        "${formatSize(lullaby.sizeBytes)}  •  ${formatDate(lullaby.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rinomina")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina")
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    lullaby: Lullaby,
    playing: Boolean,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (playing) StopIcon else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "In riproduzione",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lullaby.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "–"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "–"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))