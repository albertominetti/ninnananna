package com.alberto.ninnananna

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * App-wide language support built on AppCompatDelegate.setApplicationLocales.
 *
 * Locale tags supported by the app (one `res/values-XX/strings.xml` per tag).
 * The native display name is shown in the Settings picker.
 */
object AppLanguages {

    /** Supported locales with their native display names. */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        "en" to "English",
        "it" to "Italiano",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "pt" to "Português",
        "ru" to "Русский",
        "ro" to "Română",
        "pl" to "Polski",
        "bg" to "Български",
        "nl" to "Nederlands",
        "el" to "Ελληνικά",
        "sv" to "Svenska",
        "cs" to "Čeština",
        "da" to "Dansk",
        "fi" to "Suomi",
        "hu" to "Magyar",
        "sk" to "Slovenčina",
        "hr" to "Hrvatski",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "sl" to "Slovenščina",
        "et" to "Eesti",
        "nb" to "Norsk",
        "uk" to "Українська",
        "sr" to "Српски",
        "ca" to "Català",
        "eu" to "Euskara",
        "gl" to "Galego",
        "tr" to "Türkçe",
        "ga" to "Gaeilge",
        "is" to "Íslenska",
        "mt" to "Malti",
        "sq" to "Shqip",
        "mk" to "Македонски",
        "be" to "Беларуская",
        "bs" to "Bosanski",
        "cy" to "Cymraeg",
        "lb" to "Lëtzebuergesch",
        "zh" to "中文 (简体)",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "ta" to "தமிழ்"
    )

    fun isSupported(tag: String): Boolean = SUPPORTED.any { it.first == tag }

    /**
     * Reads the stored preference and applies it to the app.
     * - If a valid stored tag exists, it is applied.
     * - If the stored tag is invalid, English is used.
     * - If no stored preference ([null] = follow system): when the system
     *   language is not among [SUPPORTED], English is enforced as default.
     */
    fun applyStoredOrDefault(context: Context) {
        val tag = runBlocking { SettingsStore.language(context).first() }
        if (tag != null) {
            if (isSupported(tag)) setLocale(tag) else setLocale("en")
            return
        }
        // No stored preference -> check system locales
        val locales = context.resources.configuration.locales
        for (i in 0 until locales.size()) {
            val locale = locales.get(i)
            val fullTag = locale.toLanguageTag()
            val lang = locale.language
            if (isSupported(fullTag) || isSupported(lang)) {
                setLocale(null) // follow system
                return
            }
        }
        // System language not supported -> default to English
        setLocale("en")
    }

    /** Applies a locale tag immediately (null = follow the system language). */
    fun setLocale(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        )
    }
}