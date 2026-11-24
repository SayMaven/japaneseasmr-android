package com.saymaven.downloader.japaneseasmr.di

import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.ui.screens.browse.AsmrOneBrowseViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.history.HistoryViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.queue.QueueViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.settings.SettingsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AsmrDatabase.getDatabase(androidContext()) }
    single { get<AsmrDatabase>().historyDao() }
    single { get<AsmrDatabase>().translationCacheDao() }
    single { get<AsmrDatabase>().playlistDao() }
    single { PreferencesManager(androidContext()) }

    viewModel { QueueViewModel() }
    viewModel { HistoryViewModel(androidApplication()) }
    viewModel { PlayerViewModel(androidApplication()) }
    viewModel { SettingsViewModel(androidApplication()) }
    viewModel { AsmrOneBrowseViewModel(androidApplication()) }
}
