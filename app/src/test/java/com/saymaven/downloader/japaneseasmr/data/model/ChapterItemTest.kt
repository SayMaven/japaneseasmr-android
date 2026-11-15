package com.saymaven.downloader.japaneseasmr.data.model

import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterItemTest {

    @Test
    fun testChapterItemSerialization_withFileName() {
        val chapters = listOf(
            ChapterItem(startMs = 0L, endMs = 180000L, title = "01 Prologue", fileName = "01.mp3"),
            ChapterItem(startMs = 180000L, endMs = 1500000L, title = "02 Main", fileName = "02.mp3")
        )

        val json = ChapterItem.toJsonArray(chapters)
        val deserialized = ChapterItem.fromJsonArray(json)

        assertEquals(2, deserialized.size)
        assertEquals("01 Prologue", deserialized[0].title)
        assertEquals("01.mp3", deserialized[0].fileName)
        assertEquals(0L, deserialized[0].startMs)
        assertEquals(180000L, deserialized[0].endMs)

        assertEquals("02 Main", deserialized[1].title)
        assertEquals("02.mp3", deserialized[1].fileName)
    }

    @Test
    fun testChapterItemSerialization_backwardCompatibility_withoutFileName() {
        // Simulates old json saved in database from earlier versions without fileName
        val legacyJson = """[{"startMs":0,"endMs":60000,"title":"Intro"},{"startMs":60000,"endMs":120000,"title":"Outro"}]"""
        val deserialized = ChapterItem.fromJsonArray(legacyJson)

        assertEquals(2, deserialized.size)
        assertEquals("Intro", deserialized[0].title)
        assertNull(deserialized[0].fileName)
        assertEquals(0L, deserialized[0].startMs)
        assertEquals(60000L, deserialized[0].endMs)

        assertEquals("Outro", deserialized[1].title)
        assertNull(deserialized[1].fileName)
    }

    @Test
    fun testSourceTypeEnum() {
        assertEquals(SourceType.DL_SITE, SourceType.fromValue("DL_SITE"))
        assertEquals(SourceType.LOCAL_SINGLE, SourceType.fromValue("LOCAL_SINGLE"))
        assertEquals(SourceType.LOCAL_FOLDER, SourceType.fromValue("LOCAL_FOLDER"))
        // Fallback to DL_SITE on invalid or null
        assertEquals(SourceType.DL_SITE, SourceType.fromValue(null))
        assertEquals(SourceType.DL_SITE, SourceType.fromValue("UNKNOWN"))
    }

    @Test
    fun testHistoryEntityDefaults() {
        val entity = HistoryEntity(
            rjid = "RJ123456",
            title = "Sample Title",
            coverUrl = "http://example.com/cover.jpg",
            localFilePath = "/path/to/file.mp3",
            downloadDate = "15 Nov 2026",
            fileSize = "100 MB"
        )

        assertEquals("DL_SITE", entity.sourceType)
        assertNull(entity.linkedRjId)
    }
}
