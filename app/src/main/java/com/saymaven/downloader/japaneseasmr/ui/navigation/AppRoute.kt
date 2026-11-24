package com.saymaven.downloader.japaneseasmr.ui.navigation

import com.saymaven.downloader.japaneseasmr.ui.components.NavTab
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route definitions for JapaneseASMR.
 */
@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Browse : AppRoute

    @Serializable
    data object Queue : AppRoute

    @Serializable
    data object History : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class WorkDetail(val workId: Long) : AppRoute

    @Serializable
    data object Player : AppRoute

    fun toNavTab(): NavTab? = when (this) {
        Home -> NavTab.HOME
        Browse -> NavTab.BROWSE
        Queue -> NavTab.QUEUE
        History -> NavTab.HISTORY
        Settings -> NavTab.SETTINGS
        is WorkDetail, Player -> null
    }

    companion object {
        fun fromNavTab(tab: NavTab): AppRoute = when (tab) {
            NavTab.HOME -> Home
            NavTab.BROWSE -> Browse
            NavTab.QUEUE -> Queue
            NavTab.HISTORY -> History
            NavTab.SETTINGS -> Settings
        }
    }
}
