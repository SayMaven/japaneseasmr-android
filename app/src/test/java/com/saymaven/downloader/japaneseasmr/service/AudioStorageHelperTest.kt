package com.saymaven.downloader.japaneseasmr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioStorageHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun testDeepNestedStructure_findLeafAudioFolders() {
        val root = tempFolder.newFolder("ImportRoot")
        val rjDir = File(root, "RJ01697860").apply { mkdir() }
        val mp3Dir = File(rjDir, "mp3").apply { mkdir() }
        val seWithDir = File(mp3Dir, "SEあり").apply { mkdir() }
        val seWithoutDir = File(mp3Dir, "SEなし").apply { mkdir() }

        // Create dummy audio files
        File(seWithDir, "01.mp3").writeText("audio content")
        File(seWithDir, "02.mp3").writeText("audio content")
        File(seWithoutDir, "01.mp3").writeText("audio content")

        val leaves = AudioStorageHelper.findLeafAudioFolders(root, maxDepth = 5)
        assertEquals(2, leaves.size)
        assertTrue("Must detect SEあり leaf", leaves.any { it.name == "SEあり" })
        assertTrue("Must detect SEなし leaf", leaves.any { it.name == "SEなし" })
    }

    @Test
    fun testFindRjCodeInPath_resolvesAncestorRj() {
        val root = tempFolder.newFolder("ASMR_Library")
        val rjDir = File(root, "[RJ01697860] Lovely ASMR Work").apply { mkdir() }
        val subDir = File(rjDir, "mp3").apply { mkdir() }
        val leafDir = File(subDir, "SEあり").apply { mkdir() }

        val detectedRj = AudioStorageHelper.findRjCodeInPath(leafDir, rootBoundary = root)
        assertEquals("RJ01697860", detectedRj)
    }

    @Test
    fun testFindCoverInPath_resolvesAncestorCover() {
        val root = tempFolder.newFolder("ASMR_Library2")
        val rjDir = File(root, "RJ01697860").apply { mkdir() }
        val coverFile = File(rjDir, "RJ01697860_img_main.jpg").apply { writeText("fake image bytes") }
        val subDir = File(rjDir, "mp3").apply { mkdir() }
        val leafDir = File(subDir, "SEあり").apply { mkdir() }

        val resolvedCover = AudioStorageHelper.findCoverInPath(leafDir, rootBoundary = root)
        assertNotNull(resolvedCover)
        assertEquals(coverFile.canonicalPath, resolvedCover?.canonicalPath)
    }

    @Test
    fun testFindLocalCoverArt_prioritizesAriOverNashi() {
        val root = tempFolder.newFolder("ASMR_Jacket")
        val nashiFile = File(root, "01_jacket_なし.jpg").apply { writeText("fake image bytes") }
        val ariFile = File(root, "02_jacket_あり.jpg").apply { writeText("fake image bytes") }

        val cover = AudioStorageHelper.findLocalCoverArt(root)
        assertNotNull(cover)
        assertEquals(ariFile.canonicalPath, cover?.canonicalPath)
    }

    @Test
    fun testGetAllLocalImagesInFolder_includesImageSubdirectories() {
        val root = tempFolder.newFolder("ASMR_WithSubdir")
        val thumbDir = File(root, "thumb").apply { mkdir() }
        val imgInRoot = File(root, "main.jpg").apply { writeText("fake bytes") }
        val imgInThumb = File(thumbDir, "preview.png").apply { writeText("fake bytes") }

        val allImages = AudioStorageHelper.getAllLocalImagesInFolder(root)
        assertEquals(2, allImages.size)
        assertTrue(allImages.any { it.canonicalPath == imgInRoot.canonicalPath })
        assertTrue(allImages.any { it.canonicalPath == imgInThumb.canonicalPath })
    }

    @Test
    fun testMoveAudioFile_movesDirectoryRecursively() {
        val root = tempFolder.newFolder("MoveTestRoot")
        val downloadDir = File(root, "Download").apply { mkdir() }
        val sourceDir = File(root, "AlbumFolder").apply { mkdir() }
        File(sourceDir, "01.mp3").writeText("track 1 audio bytes")
        File(sourceDir, "02.mp3").writeText("track 2 audio bytes")
        File(sourceDir, "cover.jpg").writeText("cover bytes")

        val movedDir = AudioStorageHelper.moveAudioFile(sourceDir, "Favorit", downloadDir)
        assertNotNull(movedDir)
        assertTrue(movedDir!!.exists())
        assertTrue(movedDir.isDirectory)
        assertEquals("Favorit", movedDir.parentFile?.name)
        assertEquals(2, AudioStorageHelper.getAudioFilesInFolder(movedDir).size)
        assertTrue(File(movedDir, "cover.jpg").exists())
    }

    @Test
    fun testMoveAudioFile_movesSingleFileWithCompanionFiles() {
        val root = tempFolder.newFolder("MoveSingleRoot")
        val downloadDir = File(root, "Download").apply { mkdir() }
        val sourceAudio = File(root, "RJ123456.mp3").apply { writeText("audio content") }
        val sourceCue = File(root, "RJ123456.cue").apply { writeText("cue content") }
        val sourceCover = File(root, "RJ123456.jpg").apply { writeText("cover bytes") }

        val movedFile = AudioStorageHelper.moveAudioFile(sourceAudio, "Hardcore", downloadDir)
        assertNotNull(movedFile)
        assertTrue(movedFile!!.exists())
        assertEquals("Hardcore", movedFile.parentFile?.name)
        assertTrue("Companion .cue must be moved", File(movedFile.parentFile, "RJ123456.cue").exists())
        assertTrue("Companion .jpg must be moved", File(movedFile.parentFile, "RJ123456.jpg").exists())
    }
}
