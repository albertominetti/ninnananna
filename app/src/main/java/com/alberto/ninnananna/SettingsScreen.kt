package com.alberto.ninnananna

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Settings screen:
 * - Light / Dark / Amoled theme (drop-down);
 * - keep screen on (FLAG_KEEP_SCREEN_ON on the MainActivity);
 * - app language (drop-down, default = system);
 * - full reset of everything that was downloaded;
 * - app version info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val themeMode by SettingsStore.themeMode(context).collectAsState(initial = ThemeMode.DARK)
    val keepScreenOn by SettingsStore.keepScreenOn(context).collectAsState(initial = false)
    val language by SettingsStore.language(context).collectAsState(initial = null)

    var lullabies by remember { mutableStateOf(emptyList<Lullaby>()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }

    // Number of audios downloaded by the user (the preinstalled ones are excluded).
    val downloadedCount = lullabies.count { !it.isBundled }

    LaunchedEffect(Unit) {
        lullabies = DownloadRepository.listLullabies(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_section)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
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
            SettingDropdown(
                label = stringResource(R.string.theme_section),
                selectedKey = themeMode.name,
                options = listOf(
                    ThemeMode.LIGHT.name to stringResource(R.string.theme_light),
                    ThemeMode.DARK.name to stringResource(R.string.theme_dark),
                    ThemeMode.AMOLED.name to stringResource(R.string.theme_amoled)
                ),
                onSelect = { key ->
                    scope.launch { SettingsStore.setThemeMode(context, ThemeMode.valueOf(key)) }
                }
            )

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- Keep screen on ----------
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.keep_screen_on_desc),
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

            // ---------- Language ----------
            Spacer(Modifier.height(16.dp))
            SettingDropdown(
                label = stringResource(R.string.language_section),
                selectedKey = language ?: "",
                options = listOf("" to stringResource(R.string.language_system_default)) +
                    AppLanguages.SUPPORTED.map { (tag, nativeName) -> tag to nativeName },
                onSelect = { key ->
                    val tag = key.ifEmpty { null }
                    scope.launch {
                        SettingsStore.setLanguage(context, tag)
                        AppLanguages.setLocale(tag)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- Data / reset ----------
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.data_section), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.downloaded_count,
                    downloadedCount,
                    lullabies.count { it.isBundled }
                ),
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
                Text(stringResource(R.string.delete_all_downloaded))
            }

            resetFeedback?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider()

            // ---------- About / version ----------
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.version, resolveVersionName(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = {
                Text(
                    stringResource(R.string.reset_message, downloadedCount)
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
                            context.getString(R.string.reset_deleted, deleted)
                        } else {
                            context.getString(R.string.reset_none)
                        }
                    }
                }) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun resolveVersionName(context: Context): String =
    remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

/**
 * Settings control: a read-only text field that opens a drop-down menu.
 * Options are (key, label) pairs; [onSelect] receives the chosen key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    label: String,
    selectedKey: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}