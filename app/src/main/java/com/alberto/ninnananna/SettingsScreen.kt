package com.alberto.ninnananna

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Settings screen:
 * - Light / Dark / Amoled theme (pure black);
 * - keep screen on (FLAG_KEEP_SCREEN_ON on the MainActivity);
 * - full reset of everything that was downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val themeMode by SettingsStore.themeMode(context).collectAsState(initial = ThemeMode.DARK)
    val keepScreenOn by SettingsStore.keepScreenOn(context).collectAsState(initial = false)

    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }

    // Number of audios downloaded by the user (the 3 preinstalled ones are excluded).
    val downloadedCount = lullabies.count { !it.isBundled }

    LaunchedEffect(Unit) {
        lullabies = DownloadRepository.listLullabies(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---------- Theme ----------
            Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Column(Modifier.selectableGroup()) {
                ThemeModeOption(
                    label = "Light",
                    description = "Light colors",
                    selected = themeMode == ThemeMode.LIGHT,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.LIGHT) }
                    }
                )
                ThemeModeOption(
                    label = "Dark",
                    description = "Dark colors",
                    selected = themeMode == ThemeMode.DARK,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.DARK) }
                    }
                )
                ThemeModeOption(
                    label = "Amoled",
                    description = "Pure black: ideal at night",
                    selected = themeMode == ThemeMode.AMOLED,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.AMOLED) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- Keep screen on ----------
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Keep screen on", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Prevents the screen timeout during playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsStore.setKeepScreenOn(context, enabled) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- Reset ----------
            Spacer(Modifier.height(16.dp))
            Text(text = "Data", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$downloadedCount downloaded audios" +
                    " (the ${lullabies.count { it.isBundled }} preinstalled " +
                    "ones are always kept)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showResetDialog = true },
                enabled = downloadedCount > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete everything downloaded")
            }

            resetFeedback?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Full reset") },
            text = {
                Text(
                    "Do you want to delete the $downloadedCount downloaded audios? " +
                        "The preinstalled ones (Brahms, white noise, heartbeat) " +
                        "are not touched. This operation is irreversible."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        // resetAll deletes only the NON-bundled files.
                        val deleted = DownloadRepository.resetAll(context)
                        if (deleted > 0) {
                            PlayerManager.stop()
                        }
                        lullabies = DownloadRepository.listLullabies(context)
                        resetFeedback = if (deleted > 0) {
                            "Deleted $deleted downloaded audios " +
                                "(preinstalled ones kept)."
                        } else {
                            "No downloaded audios to delete."
                        }
                    }
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.height(0.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}