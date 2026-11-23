package com.saymaven.downloader.japaneseasmr.service

import com.saymaven.downloader.japaneseasmr.service.glossary.IndonesianPostFilter
import com.saymaven.downloader.japaneseasmr.service.glossary.JapaneseAsmrGlossary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationGlossaryTest {

    @Test
    fun testWakaraseDoesNotProduceOshiokiGlitch() {
        val input = "【即ヌキ110円】全裸土下座 ～泣きながら必死に土下座するJKをわからせ調教～"
        val pre = JapaneseAsmrGlossary.preProcessJapanese(input)

        // Make sure "お仕置き" is never injected because it causes "おShiji き" glitch
        assertFalse("Preprocessed text should not contain お仕置き", pre.contains("お仕置き"))
        assertTrue("Preprocessed text should normalize wakarase choukyou", pre.contains("懲らしめの調教"))
        assertTrue("Preprocessed text should normalize dogeza", pre.contains("土下座謝罪"))
        assertTrue("Preprocessed text should normalize soku nuki", pre.contains("即時射精"))
    }

    @Test
    fun testIndonesianPostFilterFixesScreenshotsGlitches() {
        // Screenshot 3 & 4 glitch simulation
        val glitchOutput = "[Artinya, ヌキ110 yen] Kursi tanah telanjang ~ menangis きながら akan mati に kursi tanah するJK をおShiji き pelatihan ~"
        val refined = IndonesianPostFilter.refineIndonesian(glitchOutput)

        assertFalse("Must not contain kursi tanah", refined.contains("kursi tanah", ignoreCase = true))
        assertFalse("Must not contain おShiji き", refined.contains("おShiji", ignoreCase = true))
        assertFalse("Must not contain raw ヌキ", refined.contains("ヌキ"))
        assertFalse("Must not contain hanging きながら", refined.contains("きながら"))
        assertTrue("Must contain bersujud memohon ampun", refined.contains("bersujud memohon ampun"))
    }

    @Test
    fun testHaremNurseAndChimasoRefinements() {
        // Screenshot 1 & 2 glitch simulation
        val glitch1 = "[4 tempat tidur 5 hari / ハーレムナース] ... ナースに bertemukembali りのたびに"
        val refined1 = IndonesianPostFilter.refineIndonesian(glitch1)
        assertFalse(refined1.contains("ハーレムナース"))
        assertTrue(refined1.contains("harem perawat", ignoreCase = true))
        assertTrue(refined1.contains("bertemu kembali", ignoreCase = true))
        assertTrue(refined1.contains("setiap kali", ignoreCase = true))

        val glitch2 = "[Kuai 楽fall ちオホ] おじさん見下しパパ生活 ... ちマゾ オナホ化"
        val refined2 = IndonesianPostFilter.refineIndonesian(glitch2)
        assertFalse(refined2.contains("ちマゾ"))
        assertFalse(refined2.contains("Kuai 楽fall ち"))
        assertTrue(refined2.contains("tunduk kenikmatan", ignoreCase = true))
        assertTrue(refined2.contains("Paman", ignoreCase = true))
        assertTrue(refined2.contains("kencan berbayar", ignoreCase = true))
    }
}
