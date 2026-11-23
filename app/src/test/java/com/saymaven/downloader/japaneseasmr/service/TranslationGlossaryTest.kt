package com.saymaven.downloader.japaneseasmr.service

import com.saymaven.downloader.japaneseasmr.service.glossary.ChineseAsmrGlossary
import com.saymaven.downloader.japaneseasmr.service.glossary.IndonesianPostFilter
import com.saymaven.downloader.japaneseasmr.service.glossary.JapaneseAsmrGlossary
import org.junit.Assert.assertEquals
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

    @Test
    fun testJapaneseTitleNotProcessedAsChinese() {
        val jaTitle = "【繁体中文版】巨根でおじさんに中出しされるJK"
        assertTrue(ChineseAsmrGlossary.containsJapaneseKana(jaTitle))
        assertFalse("Japanese title with kana must not trigger Chinese preprocessor", ChineseAsmrGlossary.containsChinese(jaTitle))
        org.junit.Assert.assertEquals("ja", TranslationService.detectSourceLanguage(jaTitle))

        // Japanese title with Katakana (ヌキ) is identified via kana
        val jaTitleWithKatakana = "【繁体中文版】【即ヌキ110円】全裸土下座調教"
        assertTrue(ChineseAsmrGlossary.containsJapaneseKana(jaTitleWithKatakana))
        assertEquals("ja", TranslationService.detectSourceLanguage(jaTitleWithKatakana))

        // Pure kanji title without kana must still be detected as Japanese
        val jaTitlePureKanji = "【繁体中文版】全裸土下座調教"
        assertFalse(ChineseAsmrGlossary.containsJapaneseKana(jaTitlePureKanji))
        assertFalse("Pure kanji Japanese title must not be marked definitively Chinese", ChineseAsmrGlossary.isDefinitivelyChinese(jaTitlePureKanji))
        assertEquals("ja", TranslationService.detectSourceLanguage(jaTitlePureKanji))

        val cnTitle = "【中文音声】大肉棒中出"
        assertFalse(ChineseAsmrGlossary.containsJapaneseKana(cnTitle))
        assertTrue("Pure Chinese title must trigger Chinese preprocessor", ChineseAsmrGlossary.containsChinese(cnTitle))
        org.junit.Assert.assertEquals("zh-CN", TranslationService.detectSourceLanguage(cnTitle))
    }

    @Test
    fun testLiveGoogleTranslation() = kotlinx.coroutines.runBlocking {
        val result = TranslationService.translateText("こんにちは", "id", "google")
        println("LIVE TRANSLATION RESULT: '$result'")
        assertTrue("Translation should not be blank", result.isNotBlank())
        assertTrue("Translation should be Halo", result.contains("Halo", ignoreCase = true) || result.contains("Selamat", ignoreCase = true))

        val batchInput = listOf(
            "【4泊5日 / ハーレムナース】勃起不全で入院したら夜勤ナースに再会",
            "全裸土下座～泣きながら必死に土下座するJKをわからせ調教～",
            "プロローグ",
            "エピローグ"
        )
        val batchRes = TranslationService.translateBatch(batchInput, "id", "google")
        println("LIVE BATCH RESULT: $batchRes")
        org.junit.Assert.assertEquals(4, batchRes.size)
        for (item in batchInput) {
            val tr = batchRes[item]
            println("TRANSLATION OF '$item': '$tr'")
            assertFalse("Must not return original text as translation", tr.isNullOrBlank() || tr == item)
        }
    }

    @Test
    fun testParseGoogleWebResponse_bothFormats() {
        // Format 1: clients5.google.com JSON string array
        val clients5Json = "[\"Halo dunia\"]"
        val parsed1 = TranslationService.parseGoogleWebResponse(clients5Json)
        assertEquals("Halo dunia", parsed1)

        // Format 2: translate.googleapis.com gtx multi-dimensional array
        val gtxJson = "[[[\"Halo dunia\",\"こんにちは世界\",null,null,10]],null,\"ja\"]"
        val parsed2 = TranslationService.parseGoogleWebResponse(gtxJson)
        assertEquals("Halo dunia", parsed2)
    }
}
