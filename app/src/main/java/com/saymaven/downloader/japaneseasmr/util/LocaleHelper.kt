package com.saymaven.downloader.japaneseasmr.util

import android.app.LocaleManager
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

    fun applyLocale(context: Context, langCode: String): Context {
        val targetLocale = getLocaleForLanguage(langCode)
        if (targetLocale == null) {
            // Revert to system default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val localeManager = context.getSystemService(LocaleManager::class.java)
                    localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                } catch (_: Exception) {}
            }
            return context
        }

        Locale.setDefault(targetLocale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList(targetLocale)
            } catch (_: Exception) {}
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        }
        val configContext = context.createConfigurationContext(config)
        return LocalizedContext(context, configContext)
    }

    fun getLocalizedConfiguration(context: Context, langCode: String): Configuration {
        val targetLocale = getLocaleForLanguage(langCode)
        val config = Configuration(context.resources.configuration)
        if (targetLocale != null) {
            config.setLocale(targetLocale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(targetLocale))
            }
        }
        return config
    }
}
