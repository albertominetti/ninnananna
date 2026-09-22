package com.alberto.ninnananna

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.cast.framework.CastButtonFactory
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

// Icona "Ripeti uno" custom (Material "repeat_one")
private val RepeatOneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOne",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineTo(14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            verticalLineTo(13f)
            horizontalLineTo(17f)
            close()
            moveTo(13f, 15f)
            verticalLineTo(9f)
            horizontalLineTo(12f)
            lineTo(10f, 10f)
            verticalLineTo(11f)
            horizontalLineTo(11.5f)
            verticalLineTo(15f)
            close()
        }
    }.build()
}

// Icona "Luna crescente" custom (Material "bedtime"), per il sleep timer
private val SleepIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sleep",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.34f, 2.02f)
            curveTo(6.59f, 1.82f, 2f, 6.42f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(15.71f, 22f, 18.93f, 19.98f, 20.66f, 16.98f)
            curveTo(13.15f, 16.73f, 8.57f, 8.55f, 12.34f, 2.02f)
            close()
        }
    }.build()
}

// Icona "Altoparlante" custom (Material "volume_up"), per la barra volume
private val VolumeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f)
            verticalLineTo(15f)
            horizontalLineTo(7f)
            lineTo(12f, 20f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            close()
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            verticalLineTo(16.03f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
            moveTo(14f, 3.23f)
            verticalLineTo(5.29f)
            curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
            curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
            verticalLineTo(20.77f)
            curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
            curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
            close()
        }
    }.build()
}

/**
 * Schermata principale: lista audio scaricati/preinstallati, download in
 * background via WorkManager (notifica foreground). Il FAB in basso a
 * destra "Add from YT" apre il bottom sheet per incollare un link YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LullabyList(onOpenSettings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }

    // Bottom sheet "Add from YT"
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sheetUrl by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    var renameTarget by remember { mutableStateOf<Lullaby?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Sleep timer (UI nel player)
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }

    val current by PlayerManager.current.collectAsState()
    val playing by PlayerManager.playing.collectAsState()
    val loopEnabled by PlayerManager.loopEnabled.collectAsState()
    val sleepRemaining by SleepTimerManager.remainingMillis.collectAsState()

    val refresh: () -> Unit = {
        scope.launch {
            lullabies = DownloadRepository.listLullabies(context)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Volume di sistema (AudioManager STREAM_MUSIC): registra l'observer dei
    // cambi volume (tasti fisici) e sincronizza lo Slider della VolumeBar.
    DisposableEffect(context) {
        VolumeManager.register(context)
        VolumeManager.refresh(context)
        onDispose { VolumeManager.unregister(context) }
    }

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
            Column {
                // Barra volume sempre visibile, sopra la mini-bar del player.
                VolumeBar()
                current?.let { lullaby ->
                    MiniPlayerBar(
                        lullaby = lullaby,
                        playing = playing,
                        loopEnabled = loopEnabled,
                        sleepRemaining = sleepRemaining,
                        onStop = { PlayerManager.stop() },
                        onToggleLoop = { PlayerManager.toggleLoop() },
                        onOpenSleepTimer = { showSleepTimerDialog = true }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
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
                val bundled = lullabies.filter { it.isBundled }
                val downloaded = lullabies.filterNot { it.isBundled }

                if (bundled.isNotEmpty()) {
                    item(key = "header-predownloaded") {
                        SectionHeader("Pre downloaded", count = bundled.size)
                    }
                    items(bundled, key = { it.id }) { lullaby ->
                        LullabyRow(
                            lullaby = lullaby,
                            isCurrent = current?.id == lullaby.id,
                            isPlaying = playing && current?.id == lullaby.id,
                            onPlay = {
                                requestNotificationPermissionIfNeeded()
                                if (CastManager.connected.value) {
                                    // Sessione Cast attiva: streamma sul dispositivo
                                    // invece di riprodurre sullo speaker del telefono.
                                    CastManager.castCurrent(context, lullaby)
                                } else {
                                    PlayerManager.play(context, lullaby)
                                }
                            },
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

                if (downloaded.isNotEmpty()) {
                    item(key = "header-downloaded") {
                        SectionHeader("Scaricate", count = downloaded.size)
                    }
                    items(downloaded, key = { it.id }) { lullaby ->
                        LullabyRow(
                            lullaby = lullaby,
                            isCurrent = current?.id == lullaby.id,
                            isPlaying = playing && current?.id == lullaby.id,
                            onPlay = {
                                requestNotificationPermissionIfNeeded()
                                if (CastManager.connected.value) {
                                    // Sessione Cast attiva: streamma sul dispositivo
                                    // invece di riprodurre sullo speaker del telefono.
                                    CastManager.castCurrent(context, lullaby)
                                } else {
                                    PlayerManager.play(context, lullaby)
                                }
                            },
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
    }

    // Bottom sheet "Add from YT" (aperto dal FAB)
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

    // Dialog sleep timer
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            active = sleepRemaining,
            onSelect = { duration ->
                if (duration == null) {
                    SleepTimerManager.cancel()
                } else {
                    SleepTimerManager.start(context, duration)
                }
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
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

/**
 * Barra volume sempre visibile in fondo alla schermata principale, sopra la
 * mini-bar del player. Slider 0..100 sincronizzato col **volume di sistema**
 * (AudioManager STREAM_MUSIC): mostra il valore corrente e lo modifica via
 * [VolumeManager]; un observer aggiorna lo Slider se l'utente usa i tasti
 * fisici del volume.
 */
@Composable
private fun VolumeBar() {
    val context = LocalContext.current.applicationContext
    val volume by VolumeManager.percent.collectAsState()
    val castConnected by CastManager.connected.collectAsState()
    val castVolume by CastManager.volumePercent.collectAsState()
    val displayVolume = if (castConnected) castVolume else volume

    // Il tema dell'app è Theme.NinnaNanna (parent Material.NoActionBar, non
    // AppCompat): il MediaRouteButton di androidx.mediarouter è un
    // AppCompatButton, quindi gli forniamo un Context con tema MaterialComponents.
    val buttonContext = LocalContext.current
    val mediaRouteContext = remember(buttonContext) {
        ContextThemeWrapper(
            buttonContext,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        )
    }

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = VolumeIcon,
                contentDescription = "Volume",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = displayVolume.toFloat(),
                onValueChange = { value ->
                    if (castConnected) {
                        // Volume del dispositivo Cast (0..100 → 0.0..1.0).
                        CastManager.setVolume(value.toInt())
                    } else {
                        // Comportamento attuale: volume di sistema dello smartphone.
                        VolumeManager.setPercent(context, value.toInt())
                    }
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$displayVolume%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp)
            )
            Spacer(Modifier.width(4.dp))
            // Pulsante Cast ufficiale: al tap apre il selettore dispositivi;
            // gestisce connessione/disconnessione e mostra lo stato attivo.
            AndroidView(
                factory = {
                    MediaRouteButton(mediaRouteContext).also { btn ->
                        CastButtonFactory.setUpMediaRouteButton(mediaRouteContext, btn)
                    }
                },
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/** Intestazione di sezione della lista (es. "Pre downloaded", "Scaricate"). */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
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
                    text = formatDisplayName(lullaby.title),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Per le preinstallate ("Pre downloaded") non mostrare la data:
                // solo durata • dimensione. Le scaricate mantengono anche la data.
                val infoText = if (lullaby.isBundled) {
                    "${formatDuration(lullaby.durationMs)}  •  " +
                        formatSize(lullaby.sizeBytes)
                } else {
                    "${formatDuration(lullaby.durationMs)}  •  " +
                        "${formatSize(lullaby.sizeBytes)}  •  ${formatDate(lullaby.lastModified)}"
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Le preinstallate (bundled) non sono né rinominabili né eliminabili.
            if (!lullaby.isBundled) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rinomina")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina")
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    lullaby: Lullaby,
    playing: Boolean,
    loopEnabled: Boolean,
    sleepRemaining: Long?,
    onStop: () -> Unit,
    onToggleLoop: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        text = formatDisplayName(lullaby.title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Toggle loop "repeat-one"
                IconButton(onClick = onToggleLoop) {
                    Icon(
                        imageVector = RepeatOneIcon,
                        contentDescription = if (loopEnabled) "Ripeti uno: attivo" else "Ripeti uno: spento",
                        tint = if (loopEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Sleep timer
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        imageVector = SleepIcon,
                        contentDescription = "Sleep timer",
                        tint = if (sleepRemaining != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                OutlinedButton(onClick = onStop) {
                    Text("Stop")
                }
            }

            // Riga timer attivo: mostra il tempo rimanente
            if (sleepRemaining != null) {
                Text(
                    text = "Sleep timer: ${formatRemaining(sleepRemaining)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * Dialog di scelta del sleep timer: 15 min, 30 min, 1h, 2h, 3h, 4h oppure Off.
 * Allo scadere l'audio viene fermato e il keep-screen-on rimosso.
 */
@Composable
private fun SleepTimerDialog(
    active: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        15L * 60 * 1000 to "15 minuti",
        30L * 60 * 1000 to "30 minuti",
        60L * 60 * 1000 to "1 ora",
        120L * 60 * 1000 to "2 ore",
        180L * 60 * 1000 to "3 ore",
        240L * 60 * 1000 to "4 ore"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (active != null) "Sleep timer attivo" else "Sleep timer")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (ms, label) ->
                    TextButton(
                        onClick = { onSelect(ms) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Off", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        }
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "–"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0L) return "0:00"
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