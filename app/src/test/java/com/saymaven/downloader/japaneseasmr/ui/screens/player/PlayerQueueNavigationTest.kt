package com.saymaven.downloader.japaneseasmr.ui.screens.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerQueueNavigationTest {

    @Test
    fun `resolveAdjacentIndex advances and wraps with repeat all`() {
        assertEquals(
            1,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 0,
                size = 3,
                forward = true,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
        assertEquals(
            -1,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 2,
                size = 3,
                forward = true,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
        assertEquals(
            0,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 2,
                size = 3,
                forward = true,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )
    }

    @Test
    fun `resolveAdjacentIndex moves backward and wraps with repeat all`() {
        assertEquals(
            1,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 2,
                size = 3,
                forward = false,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
        assertEquals(
            -1,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 0,
                size = 3,
                forward = false,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
        assertEquals(
            2,
            PlayerViewModel.resolveAdjacentIndex(
                currentIndex = 0,
                size = 3,
                forward = false,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )
    }

    @Test
    fun `resolveAdjacentIndex picks different item when shuffle is enabled`() {
        val next = PlayerViewModel.resolveAdjacentIndex(
            currentIndex = 1,
            size = 4,
            forward = true,
            shuffle = true,
            repeatMode = Player.REPEAT_MODE_OFF,
            randomIndexProvider = { 1 }
        )
        assertNotEquals(1, next)
        assertEquals(2, next)
    }
}
