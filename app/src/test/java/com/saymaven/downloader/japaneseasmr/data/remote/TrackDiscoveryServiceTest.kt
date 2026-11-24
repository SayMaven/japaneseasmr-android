package com.saymaven.downloader.japaneseasmr.data.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDiscoveryServiceTest {

    @Test
    fun testFilterSmartAsmrOneTracks_collectsAllPerTrackSubfolders() {
        val tree = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "folder")
                put("title", "01. Track One")
                put("children", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "audio")
                        put("title", "01.mp3")
                        put("mediaStreamUrl", "https://example.com/01.mp3")
                        put("duration", 120.0)
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "folder")
                put("title", "02. Track Two")
                put("children", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "audio")
                        put("title", "02.mp3")
                        put("mediaStreamUrl", "https://example.com/02.mp3")
                        put("duration", 150.0)
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "folder")
                put("title", "03. Track Three")
                put("children", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "audio")
                        put("title", "03.mp3")
                        put("mediaStreamUrl", "https://example.com/03.mp3")
                        put("duration", 180.0)
                    })
                })
            })
        }

        val tracks = TrackDiscoveryService.filterSmartAsmrOneTracks(tree, "mp3")
        assertEquals(3, tracks.size)
        assertEquals("01", tracks[0].name)
        assertEquals("01. Track One", tracks[0].relativeSubPath)
        assertEquals("02", tracks[1].name)
        assertEquals("02. Track Two", tracks[1].relativeSubPath)
        assertEquals("03", tracks[2].name)
        assertEquals("03. Track Three", tracks[2].relativeSubPath)
    }

    @Test
    fun testFilterSmartAsmrOneTracks_prefersMainFolderOverPromoAndRecap() {
        fun audio(title: String, dur: Double) = JSONObject().apply {
            put("type", "audio")
            put("title", title)
            put("mediaStreamUrl", "https://example.com/$title")
            put("duration", dur)
        }
        val mp3Folder = JSONObject().apply {
            put("type", "folder")
            put("title", "01 mp3")
            put("children", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "folder")
                    put("title", "01 本編")
                    put("children", JSONArray().apply {
                        put(audio("01 track1.mp3", 1000.0))
                        put(audio("02 track2.mp3", 1200.0))
                    })
                })
                put(JSONObject().apply {
                    put("type", "folder")
                    put("title", "02 Hシーン回想")
                    put("children", JSONArray().apply {
                        put(audio("01 h1.mp3", 900.0))
                    })
                })
                put(JSONObject().apply {
                    put("type", "folder")
                    put("title", "04 販促ボイス")
                    put("children", JSONArray().apply {
                        put(audio("01 promo.mp3", 60.0))
                    })
                })
            })
        }
        val tree = JSONArray().apply { put(mp3Folder) }
        val tracks = TrackDiscoveryService.filterSmartAsmrOneTracks(tree, "mp3")
        assertEquals(2, tracks.size)
        assertEquals("01 track1", tracks[0].name)
        assertEquals("02 track2", tracks[1].name)
    }
}
