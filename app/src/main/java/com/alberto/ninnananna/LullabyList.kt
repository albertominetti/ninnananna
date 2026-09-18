package com.alberto.ninnananna

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Schermata principale: URL YouTube + download, lista audio locali,
 * mini-player bar in basso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LullabyList(onOpenSettings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var urlText by rememberSaveable { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }

    var renameTarget by remember { mutableStateOf<Lullaby?>(null) }
    var renameText by remember { mutableStateOf("") }

    val current by PlayerManager.current.collectAsState()
    val playing by PlayerManager.playing.collectAsState()

    val refresh: () -> Unit = {
        scope.launch {
            lullabies = DownloadRepository.listLullabies(context)
        }
    }

    val startDownload: (String) -> Unit = { url ->
        if (!downloading && url.isNotBlank()) {
            downloading = true
            progress = 0f
            error = null
            scope.launch {
                try {
                    val lullaby = DownloadRepository.download(context, url.trim()) { p ->
                        progress = p
                    }
                    if (lullaby != null) {
                        urlText = ""
                        refresh()
                    }
                } catch (e: Exception) {
                    error = e.message ?: "Errore durante il download."
                } finally {
                    downloading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Intent ricevuti dall'esterno: pre-compila il campo e avvia il download
    val incoming by MainActivityEvents.pendingYoutubeUrl.collectAsState()
    LaunchedEffect(incoming) {
        val url = incoming?.trim()
        if (!url.isNullOrBlank()) {
            urlText = url
            MainActivityEvents.pendingYoutubeUrl.value = null
            startDownload(url)
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
                text = "Incolla un link YouTube per scaricare l'audio della ninnananna",
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
                enabled = !downloading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { startDownload(urlText) })
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { startDownload(urlText) },
                enabled = !downloading && urlText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scarica audio")
            }

            if (downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Download in corso… ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Audio scaricati (${lullabies.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))

            if (lullabies.isEmpty() && !downloading) {
                Text(
                    text = "Nessun audio scaricato. I file vengono salvati nello storage interno dell'app.",
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
                                error = "Rinomina non riuscita: nome già esistente o non valido."
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