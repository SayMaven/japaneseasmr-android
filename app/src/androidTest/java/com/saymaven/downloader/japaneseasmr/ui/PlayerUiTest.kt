package com.saymaven.downloader.japaneseasmr.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.service.UsbDacInfo
import com.saymaven.downloader.japaneseasmr.ui.screens.player.components.PlayerControlsSection
import com.saymaven.downloader.japaneseasmr.ui.theme.JapaneseASMRTheme
import org.junit.Rule
import org.junit.Test

class PlayerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerControlsSection_rendersTitleAndTimeWithoutCrash() {
        composeTestRule.setContent {
            JapaneseASMRTheme {
                PlayerControlsSection(
                    currentRjId = "RJ01234567",
                    isPlaying = false,
                    effectivePosition = 65_000L,
                    duration = 180_000L,
                    showRemainingTime = false,
                    onToggleRemainingTime = {},
                    onSliderDrag = {},
                    onSliderDragFinished = {},
                    chapters = listOf(ChapterItem(title = "Track 1", startMs = 0L)),
                    isLoadingChapters = false,
                    activeChapterIndex = 0,
                    activeChapter = ChapterItem(title = "Track 1", startMs = 0L),
                    translatedChapters = emptyList(),
                    isTranslationActive = false,
                    isTranslating = false,
                    dacState = UsbDacInfo(),
                    audioSpecs = "48kHz / 24-bit",
                    playerButtonOrder = "chapter,speed,sleep,gain,playlist,link,cover,translate",
                    repeatMode = 0,
                    sleepTimerRemainingMs = null,
                    isOnlineStreaming = false,
                    shuffleMode = false,
                    playbackSpeed = 1.0f,
                    onOpenChapterSheet = {},
                    onOpenSleepTimerSheet = {},
                    onOpenSpeedDialog = {},
                    onOpenAudioGainDialog = {},
                    onOpenPlaylistPickerSheet = {},
                    onOpenLinkRjDialog = {},
                    onOpenCoverPickerSheet = {},
                    onToggleTranslation = {},
                    onCycleRepeatMode = {},
                    onToggleShuffleMode = {},
                    onPlayPrevious = {},
                    onReplay10 = {},
                    onTogglePlayPause = {},
                    onForward10 = {},
                    onPlayNext = {}
                )
            }
        }

        composeTestRule.onNodeWithText("01:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("03:00").assertIsDisplayed()
    }

    @Test
    fun themeWrapper_rendersText() {
        composeTestRule.setContent {
            JapaneseASMRTheme {
                Text("JapaneseASMR")
            }
        }
        composeTestRule.onNodeWithText("JapaneseASMR").assertIsDisplayed()
    }
}
