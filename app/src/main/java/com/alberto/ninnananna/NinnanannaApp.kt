package com.alberto.ninnananna

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Only one DataStore for the whole app
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Access to preferences via DataStore Preferences.
 */
object SettingsStore {

    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_LANGUAGE = stringPreferencesKey("language")

    fun themeMode(context: Context): Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK
        }

    fun keepScreenOn(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_KEEP_SCREEN_ON] ?: false }

    /** Locale tag chosen by the user, or null to follow the system language. */
    fun language(context: Context): Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_LANGUAGE] }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setKeepScreenOn(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLanguage(context: Context, tag: String?) {
        context.settingsDataStore.edit {
            if (tag == null) it.remove(KEY_LANGUAGE) else it[KEY_LANGUAGE] = tag
        }
    }
}

/**
 * Compose root: theme + navigation between Home (LullabyList) and Settings.
 */
@Composable
fun NinnanannaApp() {
    val context = LocalContext.current.applicationContext
    val themeMode by SettingsStore.themeMode(context).collectAsState(initial = ThemeMode.DARK)
    val navController = rememberNavController()

    NinnanannaTheme(mode = themeMode) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                LullabyList(onOpenSettings = { navController.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}