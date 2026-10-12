package com.saymaven.downloader.japaneseasmr.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
     * Menggunakan engine Google Translate + kamus ASMR DLsite + pre/post-processing kontekstual.
     */
    suspend fun translateText(japaneseText: String, targetLang: String = "id"): String = withContext(Dispatchers.IO) {
        val trimmed = japaneseText.trim()
        if (trimmed.isBlank()) return@withContext ""

        val isEn = targetLang.equals("en", ignoreCase = true)
        val glossary = if (isEn) GLOSSARY_EN else GLOSSARY_ID

        // 1. Cek kecocokan langsung di Kamus ASMR jika teks adalah tag murni
        glossary[trimmed]?.let { return@withContext it }

        // 2. Pre-processing: bersihkan sensor fuseji (○, ●, ×), normalisasi hiragana & konteks ASMR
        val preProcessed = preProcessJapaneseText(trimmed, isEn)

        // 3. Panggil Mesin Terjemahan Google Web (Utama & Cepat & Kontekstual)
        var onlineTranslated = translateViaGoogleWeb(preProcessed, if (isEn) "en" else "id")

        // 4. Failover jika Google Web tidak merespon
        if (onlineTranslated.isNullOrBlank()) {
            val langPair = if (isEn) "ja|en" else "ja|id"
            onlineTranslated = translateViaMyMemory(preProcessed, langPair)
        }

        val result = onlineTranslated ?: preProcessed

        // 5. Post-process: koreksi terjemahan kaku/salah konteks khas ASMR
        postProcessTranslation(result, isEn)
    }

    // Pre-compiled regex patterns untuk efisiensi instan (tanpa alokasi ulang saat batch)
    private val FUSEJI_RULES = listOf(
        Regex("こ[○●×〇]まんこ", RegexOption.IGNORE_CASE) to "こまんこ",
        Regex("ち[○●×〇]ぽ", RegexOption.IGNORE_CASE) to "ちんぽ",
        Regex("ま[○●×〇]こ", RegexOption.IGNORE_CASE) to "まんこ",
        Regex("せ[○●×〇]くす", RegexOption.IGNORE_CASE) to "セックス",
        Regex("セ[○●×〇]クス", RegexOption.IGNORE_CASE) to "セックス",
        Regex("オ[○●×〇]ニー", RegexOption.IGNORE_CASE) to "オナニー",
        Regex("お[○●×〇]にー", RegexOption.IGNORE_CASE) to "オナニー",
        Regex("処[○●×〇]", RegexOption.IGNORE_CASE) to "処女",
        Regex("精[○●×〇]", RegexOption.IGNORE_CASE) to "精液",
        Regex("中[○●×〇]し", RegexOption.IGNORE_CASE) to "中出し",
        Regex("潮[○●×〇]き", RegexOption.IGNORE_CASE) to "潮吹き",
        Regex("[○●×〇]ナサポ", RegexOption.IGNORE_CASE) to "オナサポ",
        Regex("オ[○●×〇]サポ", RegexOption.IGNORE_CASE) to "オナサポ"
    )

    private val YEAR_PATTERN = Regex("([1-6１-６])年生編")

    private val POST_PROCESS_ID = listOf(
        Regex("\\blelaki tua\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\borang tua\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\bpamanmu\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\bpaman saya\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\bteman pamanku\\b", RegexOption.IGNORE_CASE) to "pacar Paman",
        Regex("\\bbersikap nakal\\b", RegexOption.IGNORE_CASE) to "berbuat mesum (ecchi)",
        Regex("\\bperilaku nakal\\b", RegexOption.IGNORE_CASE) to "hal mesum (ecchi)",
        Regex("\\bpermintaan nakal\\b", RegexOption.IGNORE_CASE) to "ajakan mesum",
        Regex("\\btawaran seksual\\b", RegexOption.IGNORE_CASE) to "ajakan mesum",
        Regex("\\bmendamaikan kekasihku\\b", RegexOption.IGNORE_CASE) to "baikan dengan pacar",
        Regex("\\bmendamaikan kekasih\\b", RegexOption.IGNORE_CASE) to "baikan dengan pacar",
        Regex("\\bNakaori Kekasih\\b", RegexOption.IGNORE_CASE) to "Baikan dengan Pacar",
        Regex("\\banak ayam\\b", RegexOption.IGNORE_CASE) to "Hina",
        Regex("\\banak - anak ayam\\b", RegexOption.IGNORE_CASE) to "Hina",
        Regex("\\bHina masih bayi\\b", RegexOption.IGNORE_CASE) to "Hina anak bayi",
        Regex("\\bAiji\\b", RegexOption.IGNORE_CASE) to "Airi",
        Regex("\\bAiry\\b", RegexOption.IGNORE_CASE) to "Airi",
        Regex("\\bKiwai Kirai\\b", RegexOption.IGNORE_CASE) to "benci",
        Regex("\\bKiwai\\b", RegexOption.IGNORE_CASE) to "benci",
        Regex("\\bmencium telinga\\b", RegexOption.IGNORE_CASE) to "membersihkan telinga",
        Regex("\\bgaruk telinga\\b", RegexOption.IGNORE_CASE) to "membersihkan telinga",
        Regex("\\btusuk telinga\\b", RegexOption.IGNORE_CASE) to "membersihkan telinga",
        Regex("\\bTelinga bersiul\\b", RegexOption.IGNORE_CASE) to "Pembersihan Telinga",
        Regex("\\bDukungan bawang\\b", RegexOption.IGNORE_CASE) to "Panduan Masturbasi",
        Regex("\\bbawang\\b", RegexOption.IGNORE_CASE) to "Onasapo",
        Regex("\\bejakulasi tepi\\b", RegexOption.IGNORE_CASE) to "Edging (Menahan Puncak)",
        Regex("\\bkontrol tepi\\b", RegexOption.IGNORE_CASE) to "Edging",
        Regex("\\blutut saya kering\\b", RegexOption.IGNORE_CASE) to "tenggorokan saya kering"
    )

    private val POST_PROCESS_EN = listOf(
        Regex("\\bold man\\b", RegexOption.IGNORE_CASE) to "Uncle",
        Regex("\\bold guy\\b", RegexOption.IGNORE_CASE) to "Uncle",
        Regex("\\byour uncle\\b", RegexOption.IGNORE_CASE) to "Uncle",
        Regex("\\bmy uncle\\b", RegexOption.IGNORE_CASE) to "Uncle",
        Regex("\\breconciliate my lover\\b", RegexOption.IGNORE_CASE) to "make up with lover",
        Regex("\\bnaughty behavior\\b", RegexOption.IGNORE_CASE) to "doing ecchi things",
        Regex("\\bbehaving naughtily\\b", RegexOption.IGNORE_CASE) to "doing ecchi things",
        Regex("\\bOnion support\\b", RegexOption.IGNORE_CASE) to "Onasapo (Masturbation Support)",
        Regex("\\bwhistling ear\\b", RegexOption.IGNORE_CASE) to "Ear Cleaning",
        Regex("\\binch stop\\b", RegexOption.IGNORE_CASE) to "Edging"
    )

    private fun preProcessJapaneseText(text: String, isEn: Boolean): String {
        var t = text

        // A. Buka sensor fuseji khas DLsite (こ○まんこ, ち○ぽ, ま○こ, dll)
        for ((regex, rep) in FUSEJI_RULES) {
            t = t.replace(regex, rep)
        }
        t = t.replace("○", "").replace("●", "").replace("×", "").replace("〇", "")

        // B. Normalisasi hiragana ambigu agar penerjemah mengenali kanji yang benar
        t = t.replace("えっちおさそい", "エッチなお誘い")
        t = t.replace("なかなおり", "仲直り")
        t = t.replace("かのじょ", "彼女")
        t = t.replace("だいきらい", "大嫌い")
        t = t.replace("ひなは", "ヒナは")
        t = t.replace("愛吏", "アイリ")

        // C. Pola numerik umum bab sekolah (1年生編 -> Bab Tahun ke-1)
        val match = YEAR_PATTERN.find(t)
        if (match != null) {
            val num = match.groupValues.getOrNull(1) ?: "1"
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
            t = YEAR_PATTERN.replace(t, replacement)
        }

        return t
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
        val rules = if (isEn) POST_PROCESS_EN else POST_PROCESS_ID
        for ((regex, rep) in rules) {
            refined = refined.replace(regex, rep)
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
