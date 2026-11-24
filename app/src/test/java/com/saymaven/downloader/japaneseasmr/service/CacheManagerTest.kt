package com.saymaven.downloader.japaneseasmr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `enforceAutoCleanOnDir does nothing when autoCleanEnabled is false`() {
        val dir = tempFolder.newFolder("cache_off")
        val f1 = File(dir, "old.tmp").apply { writeBytes(ByteArray(1024)) }
        assertFalse(CacheManager.enforceAutoCleanOnDir(dir, limitMb = 200, autoCleanEnabled = false))
        assertTrue(f1.exists())
    }

    @Test
    fun `calculateDirectorySizeBytes sums nested files accurately`() {
        val dir = tempFolder.newFolder("cache_size")
        val sub = File(dir, "image_cache").apply { mkdirs() }
        File(dir, "a.tmp").writeBytes(ByteArray(1500))
        File(sub, "b.img").writeBytes(ByteArray(2500))
        assertEquals(4000L, CacheManager.calculateDirectorySizeBytes(dir))
    }

    @Test
    fun `formatLimitLabel formats MB and GB cleanly`() {
        assertEquals("200 MB", CacheManager.formatLimitLabel(200))
        assertEquals("1 GB", CacheManager.formatLimitLabel(1024))
        assertEquals("10 GB", CacheManager.formatLimitLabel(10240))
    }
}
