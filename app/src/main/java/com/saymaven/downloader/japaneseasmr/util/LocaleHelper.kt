package com.saymaven.downloader.japaneseasmr.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class LocalizedContext(base: Context, private val configContext: Context) : ContextWrapper(base) {
    override fun getResources(): Resources = configContext.resources
}

object LocaleHelper {

    fun getLocaleForLanguage(langCode: String): Locale? {
        return when (langCode.lowercase()) {
            "en" -> Locale.ENGLISH
            "in", "id" -> Locale.forLanguageTag("id")
            "ja" -> Locale.JAPANESE
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> null // null means system default
        }
    }

    fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale ?: Locale.getDefault()
        }
    }

    fun applyLocale(context: Context, langCode: String): Context {
        val targetLocale = getLocaleForLanguage(langCode) ?: getSystemLocale()
        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        }
        config.setLayoutDirection(targetLocale)

        try {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } catch (_: Exception) {}

        try {
            @Suppress("DEPRECATION")
            context.applicationContext?.resources?.updateConfiguration(config, context.applicationContext.resources.displayMetrics)
        } catch (_: Exception) {}

        val configContext = context.createConfigurationContext(config)
        return LocalizedContext(context, configContext)
    }

    fun getLocalizedConfiguration(context: Context, langCode: String): Configuration {
        val targetLocale = getLocaleForLanguage(langCode) ?: getSystemLocale()
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        }
        config.setLayoutDirection(targetLocale)
        return config
    }
}
