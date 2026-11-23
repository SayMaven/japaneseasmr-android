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
}
