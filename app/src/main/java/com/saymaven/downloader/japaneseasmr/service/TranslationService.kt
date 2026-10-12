package com.saymaven.downloader.japaneseasmr.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * TranslationService Engine:
 * 1. Smart ASMR & Doujin Glossary (Pre/Post-Processor) untuk istilah khas DLsite / RJ.
 * 2. Multi-tier Online Translator (MyMemory API + Google Translate Failover).
 * 3. Bilingual mapping (Judul Utama & Bab Tracklist).
 */
object TranslationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    // Kamus Istilah ASMR Khusus DLsite (Bahasa Indonesia & English)
    private val GLOSSARY_ID = mapOf(
        // Struktur Bab
        "プロローグ" to "Prolog",
        "エピローグ" to "Epilog",
        "本編" to "Cerita Utama",
        "おまけ" to "Bonus Track",
        "特典" to "Trek Spesial",
        "フリートーク" to "Obrolan Santai VA (Free Talk)",
        "アフターストーリー" to "Cerita Lanjutan (After Story)",
        "左右確認" to "Tes Suara Kiri & Kanan",
        "バイノーラル" to "Audio Binaural (3D)",
        "ダミーヘッドマイク" to "Mikrofon Dummy Head",
        "前編" to "Bagian Awal",
        "後編" to "Bagian Akhir",
        "中編" to "Bagian Tengah",

        // Aktivitas ASMR
        "耳かき" to "Pembersihan Telinga",
        "耳掃除" to "Pembersihan Telinga",
        "梵天" to "Pembersih Bulu Halus (Bonten)",
        "綿棒" to "Korek Kuping Kapas",
        "耳ふー" to "Tiupan Telinga",
        "耳舐め" to "Jilatan Telinga",
        "耳はむ" to "Gigitan Lembut Telinga",
        "耳責め" to "Stimulasi Telinga Intens",
        "鼓膜マッサージ" to "Pijatan Gendang Telinga",
        "囁き" to "Bisikan Lembut",
        "ささやき" to "Bisikan Lembut",
        "吐息" to "Hembusan Nafas",
        "添い寝" to "Tidur Bersama / Pelukan",
        "膝枕" to "Bantal Paha",
        "シャンプー" to "Keramas Rambut",
        "洗髪" to "Cuci Rambut",
        "マッサージ" to "Pijatan Relaksasi",
        "オイルマッサージ" to "Pijat Minyak Hangat",
        "炭酸" to "Gelembung Soda (Tansan)",
        "リップ音" to "Suara Basah Bibir",
        "キス音" to "Suara Ciuman",
        "心音" to "Suara Detak Jantung",
        "雨音" to "Suara Hujan",
        "環境音" to "Suara Suasana Alam",
        "睡眠導入" to "Pengantar Tidur Nyenyak",
        "安眠" to "Tidur Pulas",
        "脳とろ" to "Sensasi Otak Meleleh (Tingles)",
        "癒し" to "Relaksasi / Healing",

        // Karakter & Tropes
        "メスガキ" to "Mesugaki (Bocah Tengil)",
        "雌ガキ" to "Mesugaki (Bocah Tengil)",
        "ツンデレ" to "Tsundere",
        "ヤンデレ" to "Yandere",
        "クーデレ" to "Kuudere",
        "小悪魔" to "Iblis Cilik Menggoda",
        "甘サド" to "Sadis Manis",
        "幼馴染" to "Teman Masa Kecil",
        "後輩" to "Junior / Adik Kelas",
        "先輩" to "Senior / Kakak Kelas",
        "同級生" to "Teman Sekelas",
        "お姉さん" to "Kakak Perempuan",
        "お姉ちゃん" to "Kakak Perempuan",
        "妹" to "Adik Perempuan",
        "義妹" to "Adik Tiri",
        "義母" to "Ibu Tiri",
        "彼女" to "Pacar",
        "妻" to "Istri",
        "奥さん" to "Istri",
        "女上司" to "Bos Perempuan",
        "メイド" to "Pelayan (Maid)",
        "ギャル" to "Cewek Gal",
        "お嬢様" to "Nona Muda",
        "男の娘" to "Femboy",

        // Suasana & Hubungan
        "甘々" to "Sangat Manja & Lembut",
        "イチャイチャ" to "Bermesraan Manja",
        "甘やかし" to "Memanjakan Penuh Kasih",
        "溺愛" to "Cinta Buta",
        "密着" to "Pelukan Erat",
        "告白" to "Pengakuan Cinta",
        "純愛" to "Cinta Murni",
        "背徳感" to "Sensasi Terlarang",

        // Sensual / R-18
        "オナサポ" to "Panduan Masturbasi",
        "寸止め" to "Edging (Menahan Puncak)",
        "射精管理" to "Kontrol Orgasme",
        "搾精" to "Pengurasan Benih",
        "焦らし" to "Godaan Menahan",
        "手コキ" to "Handjob",
        "フェラチオ" to "Blowjob",
        "フェラ" to "Blowjob",
        "足コキ" to "Footjob",
        "パイズリ" to "Titfuck",
        "中出し" to "Pelepasan di Dalam",
        "乳首責め" to "Stimulasi Puting",
        "淫語" to "Kata-kata Nakal (Dirty Talk)",
        "催眠" to "Hipnosis",
        "わからせ" to "Penundukan Bocah Tengil",
        "メス堕ち" to "Tunduk Pasrah",
        "罵倒" to "Ejekan Sensual",
        "ざこ" to "Lemah",
        "ザコ" to "Lemah",
        "奉仕" to "Pelayanan Tulus",
        "ご褒美" to "Hadiah",
        "お仕置き" to "Hukuman",
        "初体験" to "Pengalaman Pertama"
    )

    private val GLOSSARY_EN = mapOf(
        // Structure
        "プロローグ" to "Prologue",
        "エピローグ" to "Epilogue",
        "本編" to "Main Story",
        "おまけ" to "Bonus Track",
        "特典" to "Special Track",
        "フリートーク" to "Free Talk",
        "アフターストーリー" to "After Story",
        "左右確認" to "Sound Check (L/R)",
        "バイノーラル" to "Binaural Audio (3D)",
        "ダミーヘッドマイク" to "Dummy Head Mic",
        "前編" to "Part 1",
        "後編" to "Part 2",
        "中編" to "Part 2",

        // ASMR Activities
        "耳かき" to "Ear Cleaning",
        "耳掃除" to "Ear Cleaning",
        "梵天" to "Fluffy Feather Pick",
        "綿棒" to "Cotton Swab",
        "耳ふー" to "Ear Blowing",
        "耳舐め" to "Ear Licking",
        "耳はむ" to "Ear Nibbling",
        "耳責め" to "Intense Ear Teasing",
        "鼓膜マッサージ" to "Eardrum Massage",
        "囁き" to "Whispering",
        "ささやき" to "Whispering",
        "吐息" to "Breaths / Sighs",
        "添い寝" to "Sleeping Together / Cuddle",
        "膝枕" to "Lap Pillow",
        "シャンプー" to "Hair Shampoo",
        "洗髪" to "Hair Washing",
        "マッサージ" to "Massage",
        "オイルマッサージ" to "Oil Massage",
        "炭酸" to "Carbonated Fizz",
        "リップ音" to "Mouth Sounds",
        "キス音" to "Kissing Sounds",
        "心音" to "Heartbeat Sounds",
        "雨音" to "Rain Sounds",
        "環境音" to "Ambient Sounds",
        "睡眠導入" to "Sleep Induction",
        "安眠" to "Deep Sleep",
        "脳とろ" to "Brain Melting Tingles",
        "癒し" to "Soothing Relaxation",

        // Characters
        "メスガキ" to "Mesugaki (Bratty Girl)",
        "雌ガキ" to "Mesugaki (Bratty Girl)",
        "ツンデレ" to "Tsundere",
        "ヤンデレ" to "Yandere",
        "クーデレ" to "Kuudere",
        "小悪魔" to "Playful Little Devil",
        "甘サド" to "Sweet Sadist",
        "幼馴染" to "Childhood Friend",
        "後輩" to "Junior",
        "先輩" to "Senior",
        "同級生" to "Classmate",
        "お姉さん" to "Older Sister",
        "お姉ちゃん" to "Older Sister",
        "妹" to "Younger Sister",
        "義妹" to "Stepsister",
        "義母" to "Stepmother",
        "彼女" to "Girlfriend",
        "妻" to "Wife",
        "奥さん" to "Wife",
        "女上司" to "Female Boss",
        "メイド" to "Maid",
        "ギャル" to "Gyaru",
        "お嬢様" to "Young Lady",
        "男の娘" to "Femboy",

        // Mood
        "甘々" to "Super Sweet & Pampering",
        "イチャイチャ" to "Flirting & Cuddling",
        "甘やかし" to "Loving Pampering",
        "溺愛" to "Doting Affection",
        "密着" to "Clinging Close",
        "告白" to "Love Confession",
        "純愛" to "Pure Love",
        "背徳感" to "Guilty Pleasure",

        // R-18
        "オナサポ" to "Guided Masturbation (Onasapo)",
        "寸止め" to "Edging / Denial",
        "射精管理" to "Orgasm Control",
        "搾精" to "Milking",
        "焦らし" to "Teasing / Denial",
        "手コキ" to "Handjob",
        "フェラチオ" to "Blowjob",
        "フェラ" to "Blowjob",
        "足コキ" to "Footjob",
        "パイズリ" to "Titfuck",
        "中出し" to "Creampie",
        "乳首責め" to "Nipple Teasing",
        "淫語" to "Dirty Talk",
        "催眠" to "Hypnosis",
        "わからせ" to "Brat Taming (Correction)",
        "メス堕ち" to "Becoming Submissive",
        "罵倒" to "Verbal Abuse",
        "ざこ" to "Weakling",
        "ザコ" to "Weakling",
        "奉仕" to "Devoted Service",
        "ご褒美" to "Reward",
        "お仕置き" to "Punishment",
        "初体験" to "First Time"
    )

    /**
     * Menerjemahkan satu teks Jepang ke bahasa tujuan (ID / EN)
     * Menggunakan kamus ASMR + failover online translator.
     */
    suspend fun translateText(japaneseText: String, targetLang: String = "id"): String = withContext(Dispatchers.IO) {
        val trimmed = japaneseText.trim()
        if (trimmed.isBlank()) return@withContext ""

        val isEn = targetLang.equals("en", ignoreCase = true)
        val glossary = if (isEn) GLOSSARY_EN else GLOSSARY_ID

        // 1. Cek kecocokan langsung di Kamus ASMR jika teks adalah tag murni
        glossary[trimmed]?.let { return@withContext it }

        // 2. Pre-process pola Track / Bagian numerik umum (contoh: 1年生編 -> Bab Tahun ke-1)
        var preProcessed = trimmed
        val yearPattern = Pattern.compile("([1-6１-６])年生編")
        val yearMatcher = yearPattern.matcher(preProcessed)
        if (yearMatcher.find()) {
            val num = yearMatcher.group(1) ?: "1"
            val normNum = when (num) {
                "１" -> "1"
                "２" -> "2"
                "３" -> "3"
                "４" -> "4"
                "５" -> "5"
                "６" -> "6"
                else -> num
            }
            val replacement = if (isEn) "Year $normNum Arc" else "Bab Tahun ke-$normNum"
            preProcessed = yearMatcher.replaceAll(replacement)
        }

        // 3. Panggil Mesin Terjemahan Online
        val langPair = if (isEn) "ja|en" else "ja|id"
        var onlineTranslated = translateViaMyMemory(preProcessed, langPair)
        if (onlineTranslated.isNullOrBlank()) {
            onlineTranslated = translateViaGoogleWeb(preProcessed, if (isEn) "en" else "id")
        }

        val result = onlineTranslated ?: preProcessed

        // 4. Post-process koreksi terjemahan mesin yang salah/kaku
        postProcessTranslation(result, isEn)
    }

    /**
     * Menerjemahkan daftar judul bab secara berurutan
     */
    suspend fun translateBatch(
        titles: List<String>,
        targetLang: String = "id",
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        for ((idx, title) in titles.withIndex()) {
            val translated = translateText(title, targetLang)
            results.add(translated.ifBlank { title })
            onProgress?.invoke(idx + 1, titles.size)
        }
        results
    }

    private fun translateViaMyMemory(text: String, langPair: String): String? {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$langPair"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val status = json.optInt("responseStatus", 0)
                if (status == 200) {
                    val resData = json.optJSONObject("responseData")
                    val trans = resData?.optString("translatedText")?.trim()
                    if (!trans.isNullOrBlank() && !trans.contains("MYMEMORY WARNING")) {
                        cleanHtmlEntities(trans)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun translateViaGoogleWeb(text: String, tl: String): String? {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=$tl&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val array = org.json.JSONArray(body)
                val parts = array.optJSONArray(0) ?: return null
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val item = parts.optJSONArray(i)
                    val segment = item?.optString(0)
                    if (!segment.isNullOrBlank()) {
                        sb.append(segment)
                    }
                }
                val res = sb.toString().trim()
                if (res.isNotBlank()) cleanHtmlEntities(res) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun postProcessTranslation(text: String, isEn: Boolean): String {
        var refined = text
        if (!isEn) {
            // Perbaikan terjemahan umum yang sering kaku dalam Bahasa Indonesia
            refined = refined.replace("Dukungan bawang", "Panduan Masturbasi", ignoreCase = true)
            refined = refined.replace("bawang", "Onasapo", ignoreCase = true)
            refined = refined.replace("Telinga bersiul", "Pembersihan Telinga", ignoreCase = true)
            refined = refined.replace("garuk telinga", "Pembersihan Telinga", ignoreCase = true)
            refined = refined.replace("tusuk telinga", "Pembersihan Telinga", ignoreCase = true)
            refined = refined.replace("anak perempuan junior", "Junior Mesugaki", ignoreCase = true)
            refined = refined.replace("Kekacauan", "Bermesraan", ignoreCase = true)
            refined = refined.replace("ejakulasi tepi", "Edging (Menahan Puncak)", ignoreCase = true)
            refined = refined.replace("kontrol tepi", "Edging", ignoreCase = true)
        } else {
            // Perbaikan terjemahan umum dalam English
            refined = refined.replace("Onion support", "Onasapo (Masturbation Support)", ignoreCase = true)
            refined = refined.replace("whistling ear", "Ear Cleaning", ignoreCase = true)
            refined = refined.replace("inch stop", "Edging", ignoreCase = true)
        }
        return refined
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
