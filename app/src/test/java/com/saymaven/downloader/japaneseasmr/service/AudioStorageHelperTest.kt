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

    @Test
    fun testExtractRjCode_handlesVariationsAndNormalizes() {
        assertEquals("RJ01081584", AudioStorageHelper.extractRjCode("RJ01081584"))
        assertEquals("RJ01081584", AudioStorageHelper.extractRjCode("[RJ-01081584] Title"))
        assertEquals("VJ12345678", AudioStorageHelper.extractRjCode("vj_12345678"))
        assertEquals("BJ123456", AudioStorageHelper.extractRjCode("bj 123456"))
        assertEquals(null, AudioStorageHelper.extractRjCode("plain_audio_file.mp3"))
        assertEquals(null, AudioStorageHelper.extractRjCode("RJ1234"))
    }

    @Test
    fun testFindSpecificCoverForAudioFile_resolvesCompanionAndRjMatchingImage() {
        val root = tempFolder.newFolder("MultiAudioCollection")
        val audio1 = File(root, "voice_track_01.mp3").apply { writeText("audio") }
        val audio2 = File(root, "[RJ01081584] Work.mp3").apply { writeText("audio") }
        val audio3 = File(root, "unrelated_track.mp3").apply { writeText("audio") }

        val companion1 = File(root, "voice_track_01.jpg").apply { writeText("image 1") }
        val rjImage2 = File(root, "RJ01081584_img_main.jpg").apply { writeText("image 2") }
        File(root, "cover.jpg").apply { writeText("generic blanket cover") }

        // audio1 matches its companion stem
        val resolvedCover1 = AudioStorageHelper.findSpecificCoverForAudioFile(audio1)
        assertNotNull(resolvedCover1)
        assertEquals(companion1.canonicalPath, resolvedCover1?.canonicalPath)

        // audio2 matches image containing its RJ code
        val resolvedCover2 = AudioStorageHelper.findSpecificCoverForAudioFile(audio2, "RJ01081584")
        assertNotNull(resolvedCover2)
        assertEquals(rjImage2.canonicalPath, resolvedCover2?.canonicalPath)

        // audio3 has no companion or RJ match; must NOT pick the generic cover.jpg
        val resolvedCover3 = AudioStorageHelper.findSpecificCoverForAudioFile(audio3)
        assertEquals(null, resolvedCover3)
    }

    @Test
    fun testIsDedicatedSingleWorkFolder_identifiesSingleVsMultiTrackFolders() {
        val root = tempFolder.newFolder("FolderStructure")
        val singleFolder = File(root, "SingleWork").apply { mkdir() }
        File(singleFolder, "track.mp3").writeText("audio")

        val multiFolder = File(root, "HardcoreCollection").apply { mkdir() }
        File(multiFolder, "audio1.mp3").writeText("audio")
        File(multiFolder, "audio2.mp3").writeText("audio")
        File(multiFolder, "audio3.mp3").writeText("audio")

        assertTrue("Single track folder is dedicated", AudioStorageHelper.isDedicatedSingleWorkFolder(singleFolder))
        org.junit.Assert.assertFalse("Collection folder with multiple audios is not dedicated", AudioStorageHelper.isDedicatedSingleWorkFolder(multiFolder))
    }

    @Test
    fun testIsMismatchedFolderCover_detectsBlanketCoverOnIndividualAudio() {
        val root = tempFolder.newFolder("Hardcore")
        val audio1 = File(root, "[RJ01081584] Audio1.mp3").apply { writeText("audio") }
        File(root, "Audio2.mp3").apply { writeText("audio") }
        val blanketCover = File(root, "cover.jpg").apply { writeText("blanket cover") }
        val specificCover = File(root, "[RJ01081584] Audio1.jpg").apply { writeText("specific cover") }

        // A blanket cover in a multi-audio folder is a mismatch for individual audios
        assertTrue(AudioStorageHelper.isMismatchedFolderCover(audio1, blanketCover.absolutePath, "RJ01081584"))

        // A specific companion cover matching stem is NOT a mismatch
        org.junit.Assert.assertFalse(AudioStorageHelper.isMismatchedFolderCover(audio1, specificCover.absolutePath, "RJ01081584"))

        // Remote DLsite URLs are NOT mismatched
        org.junit.Assert.assertFalse(AudioStorageHelper.isMismatchedFolderCover(audio1, "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01080000/RJ01081584_img_main.jpg", "RJ01081584"))
    }

    @Test
    fun testGetAudioFilesRecursive_collectsPerTrackSubfoldersInOrder() {
        val workRoot = tempFolder.newFolder("RJ01716841")
        val sub1 = File(workRoot, "Track 01").apply { mkdir() }
        val sub2 = File(workRoot, "Track 02").apply { mkdir() }
        val sub10 = File(workRoot, "Track 10").apply { mkdir() }

        File(sub1, "audio.mp3").writeText("audio 1")
        File(sub2, "audio.mp3").writeText("audio 2")
        File(sub10, "audio.mp3").writeText("audio 10")

        val recursive = AudioStorageHelper.getAudioFilesRecursive(workRoot)
        assertEquals(3, recursive.size)
        assertEquals("Track 01", recursive[0].parentFile?.name)
        assertEquals("Track 02", recursive[1].parentFile?.name)
        assertEquals("Track 10", recursive[2].parentFile?.name)
    }
}
