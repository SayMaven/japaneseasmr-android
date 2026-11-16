package com.saymaven.downloader.japaneseasmr.service

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRepeatModeTest {

    private fun getNextRepeatMode(currentMode: Int): Int {
        return when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun mapToInternalExoRepeatMode(userMode: Int): Int {
        return if (userMode == Player.REPEAT_MODE_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    private fun calculateAdjacentIndex(
        currentIndex: Int,
        listSize: Int,
        forward: Boolean,
        repeatMode: Int
    ): Int {
        if (listSize <= 0 || currentIndex !in 0 until listSize) return -1
        return if (forward) {
            if (currentIndex + 1 < listSize) currentIndex + 1
            else if (repeatMode == Player.REPEAT_MODE_ALL) 0 else -1
        } else {
            if (currentIndex - 1 >= 0) currentIndex - 1
            else if (repeatMode == Player.REPEAT_MODE_ALL) listSize - 1 else -1
        }
    }

    @Test
    fun testCycleRepeatModeTransitions() {
        var mode = Player.REPEAT_MODE_OFF
        mode = getNextRepeatMode(mode)
        assertEquals(Player.REPEAT_MODE_ALL, mode)

        mode = getNextRepeatMode(mode)
        assertEquals(Player.REPEAT_MODE_ONE, mode)

        mode = getNextRepeatMode(mode)
        assertEquals(Player.REPEAT_MODE_OFF, mode)
    }

    @Test
    fun testMappingToInternalExoPlayerRepeatMode() {
        assertEquals(Player.REPEAT_MODE_OFF, mapToInternalExoRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_OFF, mapToInternalExoRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_ONE, mapToInternalExoRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun testRepeatAllWrapsToBeginningAtEndOfPlaylist() {
        val listSize = 5
        val lastIdx = 4

        val nextIdx = calculateAdjacentIndex(
            currentIndex = lastIdx,
            listSize = listSize,
            forward = true,
            repeatMode = Player.REPEAT_MODE_ALL
        )
        assertEquals(0, nextIdx)
    }

    @Test
    fun testRepeatOffStopsAtEndOfPlaylist() {
        val listSize = 5
        val lastIdx = 4

        val nextIdx = calculateAdjacentIndex(
            currentIndex = lastIdx,
            listSize = listSize,
            forward = true,
            repeatMode = Player.REPEAT_MODE_OFF
        )
        assertEquals(-1, nextIdx)
    }

    @Test
    fun testRepeatAllWrapsToEndWhenNavigatingBackwardsAtStart() {
        val listSize = 5
        val startIdx = 0

        val prevIdx = calculateAdjacentIndex(
            currentIndex = startIdx,
            listSize = listSize,
            forward = false,
            repeatMode = Player.REPEAT_MODE_ALL
        )
        assertEquals(4, prevIdx)
    }

    @Test
    fun testNormalForwardProgressionWithinPlaylist() {
        val listSize = 5
        val currentIdx = 2

        val nextIdx = calculateAdjacentIndex(
            currentIndex = currentIdx,
            listSize = listSize,
            forward = true,
            repeatMode = Player.REPEAT_MODE_ALL
        )
        assertEquals(3, nextIdx)
    }
}
