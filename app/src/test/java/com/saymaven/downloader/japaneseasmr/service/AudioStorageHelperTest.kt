package com.saymaven.downloader.japaneseasmr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStorageHelperTest {

    @Test
    fun testNaturalOrderComparator_numericSorting() {
        val files = listOf("track10.mp3", "track1.mp3", "track2.mp3", "track20.mp3", "track3.mp3")
        val sorted = files.sortedWith(AudioStorageHelper.NATURAL_ORDER_COMPARATOR)
        val expected = listOf("track1.mp3", "track2.mp3", "track3.mp3", "track10.mp3", "track20.mp3")
        assertEquals(expected, sorted)
    }

    @Test
    fun testNaturalOrderComparator_leadingZerosAndMixed() {
        val files = listOf("02.mp3", "01.mp3", "10.mp3", "03.mp3")
        val sorted = files.sortedWith(AudioStorageHelper.NATURAL_ORDER_COMPARATOR)
        val expected = listOf("01.mp3", "02.mp3", "03.mp3", "10.mp3")
        assertEquals(expected, sorted)
    }

    @Test
    fun testGenerateLocalId_deterministicAndPrefixed() {
        val path1 = "/storage/emulated/0/Music/ASMR_Album"
        val id1 = AudioStorageHelper.generateLocalId(path1)
        val id2 = AudioStorageHelper.generateLocalId(path1)

        assertEquals("Same path must produce same local ID", id1, id2)
        assertTrue("Local ID must start with LOCAL_", id1.startsWith("LOCAL_"))
        assertEquals("LOCAL_ + 10 chars", 16, id1.length)
    }

    @Test
    fun testFormatPathForDisplay() {
        val primary = "/storage/emulated/0/Download/JapaneseASMR"
        val displayPrimary = AudioStorageHelper.formatPathForDisplay(primary)
        assertEquals("Download/JapaneseASMR", displayPrimary)

        val sd = "/storage/ABCD-1234/Music/ASMR"
        val displaySd = AudioStorageHelper.formatPathForDisplay(sd)
        assertEquals("SD Card/Music/ASMR", displaySd)
    }
}
