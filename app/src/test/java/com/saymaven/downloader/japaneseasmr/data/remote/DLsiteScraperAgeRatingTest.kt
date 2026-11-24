package com.saymaven.downloader.japaneseasmr.data.remote

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class DLsiteScraperAgeRatingTest {

    @Test
    fun `parseAgeRatingFromDocument detects new DLsite Vue labels`() {
        val safeHtml = """<dd class="c-productInfo__value"><span class="label_all label_M">全年齢</span></dd>"""
        val r15Html = """<dd class="c-productInfo__value"><span class="label_r15 label_M">R-15</span></dd>"""
        val r18Html = """<dd class="c-productInfo__value"><span class="label_r18 label_M">18禁</span></dd>"""

        assertEquals("SAFE", DLsiteScraper.parseAgeRatingFromDocument(Jsoup.parse(safeHtml)))
        assertEquals("R-15", DLsiteScraper.parseAgeRatingFromDocument(Jsoup.parse(r15Html)))
        assertEquals("R18", DLsiteScraper.parseAgeRatingFromDocument(Jsoup.parse(r18Html)))
    }

    @Test
    fun `parseAgeRatingFromDocument preserves classic table selector`() {
        val classicHtml = """<table><tr><th>年齢指定</th><td>全年齢</td></tr></table>"""
        assertEquals("SAFE", DLsiteScraper.parseAgeRatingFromDocument(Jsoup.parse(classicHtml)))
    }

    @Test
    fun `parseAgeRatingFromDocument does not infer R18 from maniax title suffix`() {
        val maniaxHtml = """<html><head><title>作品タイトル | DLsite 同人 - R18</title></head><body></body></html>"""
        assertEquals("-", DLsiteScraper.parseAgeRatingFromDocument(Jsoup.parse(maniaxHtml)))
    }
}
