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
 * Schermata Impostazioni:
 * - tema Chiaro / Scuro / Amoled (nero puro);
 * - mantieni schermo attivo (FLAG_KEEP_SCREEN_ON sulla MainActivity);
 * - reset completo di tutto ciò che è stato scaricato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val themeMode by SettingsStore.themeMode(context).collectAsState(initial = ThemeMode.AMOLED)
    val keepScreenOn by SettingsStore.keepScreenOn(context).collectAsState(initial = false)

    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lullabies = DownloadRepository.listLullabies(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
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
            // ---------- Tema ----------
            Text(text = "Tema", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Column(Modifier.selectableGroup()) {
                ThemeModeOption(
                    label = "Chiaro",
                    description = "Colori chiari",
                    selected = themeMode == ThemeMode.LIGHT,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.LIGHT) }
                    }
                )
                ThemeModeOption(
                    label = "Scuro",
                    description = "Colori scuri",
                    selected = themeMode == ThemeMode.DARK,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.DARK) }
                    }
                )
                ThemeModeOption(
                    label = "Amoled",
                    description = "Nero puro: ideale di notte",
                    selected = themeMode == ThemeMode.AMOLED,
                    onSelect = {
                        scope.launch { SettingsStore.setThemeMode(context, ThemeMode.AMOLED) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- Mantieni schermo attivo ----------
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Mantieni schermo attivo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Impedisce il timeout dello schermo durante la riproduzione",
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
            Text(text = "Dati", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${lullabies.size} audio scaricati (storage interno dell'app)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Elimina tutto ciò che è scaricato")
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
            title = { Text("Reset completo") },
            text = {
                Text(
                    "Vuoi eliminare tutti i file audio scaricati? " +
                        "L'operazione non è reversibile."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        val deleted = DownloadRepository.resetAll(context)
                        if (deleted > 0) {
                            PlayerManager.stop()
                        }
                        lullabies = DownloadRepository.listLullabies(context)
                        resetFeedback = if (deleted > 0) {
                            "Eliminati $deleted file."
                        } else {
                            "Nessun file da eliminare."
                        }
                    }
                }) { Text("Elimina tutto") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Annulla") }
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