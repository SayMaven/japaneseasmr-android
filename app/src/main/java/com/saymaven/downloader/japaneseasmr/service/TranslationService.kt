package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.dao.TranslationCacheDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * TranslationService Engine:
 * 1. Smart ASMR & Doujin Glossary (Pre/Post-Processor) untuk istilah khas DLsite / RJ.
 * 2. Multi-tier Online Translator (MyMemory API + Google Translate Failover).
 * 3. Bilingual mapping (Judul Utama & Bab Tracklist).
 * 4. High-Performance Persistent Room Database & Memory Cache (0ms Instant Loading).
 */
object TranslationService {

    @Volatile
    private var cacheDao: TranslationCacheDao? = null
    private val memoryCache = ConcurrentHashMap<String, String>()

    fun init(dao: TranslationCacheDao) {
        this.cacheDao = dao
    }

    fun init(context: Context) {
        if (cacheDao == null) {
            synchronized(this) {
                if (cacheDao == null) {
                    cacheDao = AsmrDatabase.getDatabase(context.applicationContext).translationCacheDao()
                }
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

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
        "初体験" to "Pengalaman Pertama",

        // Dialog & Judul Khas DLsite
        "なんで助けてくれないの" to "Kenapa Kamu Tidak Menolongku?",
        "どうして助けてくれないの" to "Kenapa Kamu Tidak Menolongku?",
        "助けてくれないの" to "Kenapa Tidak Menolongku?",
        "輪姦の授業" to "Pelajaran Pelecehan Kelompok (Rinkan)",
        "輪姦" to "Pelecehan Kelompok (Rinkan)",
        "親子姦" to "Hubungan Terlarang (Orang Tua & Anak)",
        "近親相姦" to "Hubungan Sedarah (Inses)",
        "母子姦" to "Hubungan Terlarang Ibu & Anak",
        "姉弟姦" to "Hubungan Terlarang Kakak & Adik",
        "妹姦" to "Hubungan Terlarang dengan Adik",
        "奴隷堕ち" to "Kejatuhan Menjadi Budak",
        "悪堕ち" to "Kejatuhan ke Sisi Gelap",
        "ひとりぼっちJK" to "Siswi SMA yang Kesepian",
        "万引きJK" to "Siswi SMA yang Mengutil",
        "万引き" to "Kasus Mengutil",
        "痴漢" to "Pelecehan Seksual (Chikan)",
        "調教" to "Penjinakan / Pelatihan (Training)",
        "洗脳" to "Cuci Otak (Brainwash)",
        "屈服" to "Tunduk Pasrah",
        "敗北" to "Kekalahan",
        "肉便器" to "Budak Nafsu",
        "寝取られ" to "NTR (Ditikung / Direbut)",
        "寝取り" to "Merebut Pasangan Orang (Netori)",
        "催眠音声" to "Audio Hipnosis",
        "全裸" to "Telanjang Bulat",
        "露出" to "Ekshibisi / Pamer Tubuh",
        "野外" to "Luar Ruangan (Outdoor)",
        "拘束" to "Ikatan / Dikekang (Restraint)",

        // Wakarase & Doujin Tropes
        "わからせ" to "Memberi Pelajaran",
        "【わからせ】" to "[Memberi Pelajaran]",
        "生意気褐色メス" to "Gadis Berkulit Eksotis yang Sombong",
        "褐色メス" to "Gadis Berkulit Eksotis",
        "居候先" to "Rumah Tumpangan",
        "妊娠確定生えっち" to "Hubungan Intim Tanpa Pengaman",
        "生えっち" to "Hubungan Intim Tanpa Pengaman",
        "仲直り生えっち" to "Hubungan Intim Tanda Berdamai",
        "口内射精フェラ" to "Klimaks di Dalam Mulut",
        "口内射精" to "Klimaks di Dalam Mulut",
        "からかい素股" to "Menggoda dengan Stimulasi Paha",
        "強要手マン" to "Memaksa Stimulasi Jari",
        "足コキいじめ" to "Menggoda dengan Stimulasi Kaki",
        "手コキいじめ" to "Menggoda dengan Stimulasi Tangan",
        "素股" to "Stimulasi Paha (Sumata)",
        "手マン" to "Stimulasi Jari (Teman)",
        "逆襲" to "Serangan Balik",

        // Posisi & Istilah Khas Tracklist ASMR
        "バック" to "Posisi Belakang (Doggy Style)",
        "正常位" to "Posisi Misionaris",
        "騎乗位" to "Posisi Cowgirl (Kijoui)",
        "対面座位" to "Posisi Duduk Berhadapan",
        "駅弁" to "Posisi Gendong (Ekiben)",
        "パイズリフェラ" to "Stimulasi Payudara & Oral (Blowjob)",
        "お掃除フェラ" to "Pembersihan Oral (Blowjob)",
        "オナニー喘ぎ" to "Desahan Masturbasi",
        "右乳首舐め" to "Jilatan Puting Kanan",
        "左乳首舐め" to "Jilatan Puting Kiri",
        "右耳舐め" to "Jilatan Telinga Kanan",
        "左耳舐め" to "Jilatan Telinga Kiri",
        "両耳舐め" to "Jilatan Kedua Telinga",
        "全裸土下座謝罪" to "Permintaan Maaf Bersujud Telanjang Bulat",
        "全裸土下座" to "Bersujud Telanjang Bulat",
        "土下座" to "Bersujud (Dogeza)",
        "FT" to "Obrolan Santai VA (Free Talk)",
        "ボーナストラック" to "Trek Bonus",
        "筆下ろし" to "Pengalaman Pertama (Keperawanan)",
        "筆おろし" to "Pengalaman Pertama (Keperawanan)",
        "ピロートーク" to "Pillow Talk (Obrolan Ranjang)",
        "見抜き" to "Menonton Masturbasi (Minuki)",
        "タイトルコール" to "Panggilan Judul (Title Call)",
        "女性向け" to "Khusus Pendengar Wanita",
        "女性受け" to "Khusus Pendengar Wanita",
        "女性受けトラック" to "Trek Khusus Pendengar Wanita",
        "女性向けトラック" to "Trek Khusus Pendengar Wanita",
        "露天風呂" to "Pemandian Air Panas Terbuka (Rotenburo)",
        "混浴" to "Pemandian Campur (Konyoku)",
        "生配信" to "Live Streaming",
        "裏垢" to "Akun Rahasia (Ura-aka)",
        "パパ活" to "Sugar Dating (Papakatsu)",
        "地雷系" to "Gaya Jirai (Jirai-kei)",
        "ロリババア" to "Loli-babaa (Nenek Berwujud Loli)",
        "魔力供給" to "Suplai Mana (Energi Sihir)",
        "オナホごっこ" to "Roleplay Onahole",
        "オホ声" to "Desahan Sensual (Oho)",
        "ごっくん" to "Menelan Sperma (Gokkun)",
        "顔面騎乗" to "Facesitting (Duduk di Wajah)",
        "危険日" to "Masa Subur (Hari Rawan Hamil)",
        "寝取られ" to "Netorare (Direbut Orang Lain)",
        "時間停止" to "Penghentian Waktu (Time Stop)",
        "命乞い" to "Memohon Ampun untuk Nyawanya",
        "悪堕ち" to "Korupsi Moral (Aku-ochi)",
        "ケモロリ" to "Gadis Loli Kemonomimi",
        "バキュームフェラ" to "Blowjob Hisapan Vakum Kuat",
        "耐久添い寝トラック" to "Trek Menemani Tidur Durasi Panjang",
        "心音なし耐久添い寝トラック" to "Trek Menemani Tidur Durasi Panjang Tanpa Detak Jantung",
        "耐久添い寝" to "Menemani Tidur Durasi Panjang",
        "耐久トラック" to "Trek Durasi Panjang",
        "添い寝トラック" to "Trek Menemani Tidur",
        "キャストトーク" to "Obrolan Pengisi Suara (Cast Talk)",
        "寝息_右" to "Napas Tidur Pulas (Kanan)",
        "寝息_左" to "Napas Tidur Pulas (Kiri)",
        "耳舐め_右" to "Jilatan Telinga (Kanan)",
        "耳舐め_左" to "Jilatan Telinga (Kiri)"
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
        "初体験" to "First Time",

        // Common Dialog & DLsite Tropes
        "なんで助けてくれないの" to "Why won't you help me?",
        "どうして助けてくれないの" to "Why won't you help me?",
        "助けてくれないの" to "Won't you help me?",
        "輪姦の授業" to "Group Assault Lesson (Rinkan)",
        "輪姦" to "Group Assault (Rinkan)",
        "親子姦" to "Taboo Parent-Child Relationship",
        "近親相姦" to "Incestuous Relationship",
        "母子姦" to "Mother-Son Taboo Relationship",
        "姉弟姦" to "Sibling Taboo Relationship",
        "妹姦" to "Sister Taboo Relationship",
        "奴隷堕ち" to "Fall into Slavery",
        "悪堕ち" to "Fall to Darkness",
        "ひとりぼっちJK" to "Lonely High School Girl",
        "万引きJK" to "Shoplifting High School Girl",
        "万引き" to "Shoplifting",
        "痴漢" to "Molestation (Chikan)",
        "調教" to "Training / Conditioning",
        "洗脳" to "Brainwashing",
        "屈服" to "Submission",
        "敗北" to "Defeat",
        "肉便器" to "Meat Toilet",
        "寝取られ" to "NTR (Netorare)",
        "寝取り" to "Netori",
        "催眠音声" to "Hypnosis Audio",
        "全裸" to "Fully Naked",
        "露出" to "Exhibitionism",
        "野外" to "Outdoor",
        "拘束" to "Restraint / Bondage",

        // Wakarase & Doujin Tropes
        "わからせ" to "Teaching a Lesson",
        "【わからせ】" to "[Teaching a Lesson]",
        "生意気褐色メス" to "Cheeky Tan Girl",
        "褐色メス" to "Tan Girl",
        "居候先" to "Boarding House",
        "妊娠確定生えっち" to "Raw Unprotected Intercourse",
        "生えっち" to "Raw Unprotected Intercourse",
        "仲直り生えっち" to "Reconciling Intercourse",
        "口内射精フェラ" to "Oral Creampie Blowjob",
        "口内射精" to "Oral Creampie",
        "からかい素股" to "Teasing with Thighjob (Sumata)",
        "強要手マン" to "Forced Fingering",
        "足コキいじめ" to "Teasing with Footjob",
        "手コキいじめ" to "Teasing with Handjob",
        "素股" to "Thighjob (Sumata)",
        "手マン" to "Fingering",
        "逆襲" to "Counterattack",

        // Positions & Tracklist Terms
        "バック" to "Doggy Style (Back)",
        "正常位" to "Missionary Position",
        "騎乗位" to "Cowgirl Position",
        "対面座位" to "Facing Seated Position",
        "駅弁" to "Standing Carry Position (Ekiben)",
        "パイズリフェラ" to "Titfuck & Blowjob",
        "お掃除フェラ" to "Clean-up Blowjob",
        "オナニー喘ぎ" to "Masturbation Panting",
        "右乳首舐め" to "Right Nipple Licking",
        "左乳首舐め" to "Left Nipple Licking",
        "右耳舐め" to "Right Ear Licking",
        "左耳舐め" to "Left Ear Licking",
        "両耳舐め" to "Both Ears Licking",
        "全裸土下座謝罪" to "Naked Dogeza Apology",
        "全裸土下座" to "Naked Dogeza",
        "土下座" to "Dogeza (Kowtow)",
        "FT" to "Free Talk",
        "ボーナストラック" to "Bonus Track",
        "筆下ろし" to "Losing Virginity (First Time)",
        "筆おろし" to "Losing Virginity (First Time)",
        "ピロートーク" to "Pillow Talk",
        "見抜き" to "Masturbation Peeping (Minuki)",
        "タイトルコール" to "Title Call",
        "耐久添い寝トラック" to "Long Co-Sleeping Endurance Track",
        "心音なし耐久添い寝トラック" to "Long Co-Sleeping Track without Heartbeat",
        "耐久添い寝" to "Long Co-Sleeping Endurance",
        "耐久トラック" to "Endurance Track",
        "添い寝トラック" to "Co-Sleeping Track",
        "キャストトーク" to "Cast Talk",
        "寝息_右" to "Sleeping Breath (Right)",
        "寝息_左" to "Sleeping Breath (Left)",
        "耳舐め_右" to "Ear Licking (Right)",
        "耳舐め_左" to "Ear Licking (Left)"
    )

    /**
     * Menerjemahkan satu teks Jepang ke bahasa tujuan (ID / EN)
     * Menggunakan engine Google Translate + kamus ASMR DLsite + pre/post-processing kontekstual.
     * Dilengkapi cache persisten SQLite Room + in-memory cache (0ms instant loading).
     */
    suspend fun translateText(japaneseText: String, targetLang: String = "id"): String = withContext(Dispatchers.IO) {
        val trimmed = japaneseText.trim()
        if (trimmed.isBlank()) return@withContext ""

        val cacheKey = "$targetLang:$trimmed"

        // 0. Cek memory cache (0ms instant)
        memoryCache[cacheKey]?.let { return@withContext it }

        val isEn = targetLang.equals("en", ignoreCase = true)
        val glossary = if (isEn) GLOSSARY_EN else GLOSSARY_ID

        // 1. Cek kecocokan langsung di Kamus ASMR jika teks adalah tag murni
        glossary[trimmed]?.let {
            memoryCache[cacheKey] = it
            return@withContext it
        }

        // 1b. Cek kecocokan kamus dengan nomor bab/track prefix (misal: #1., 01., ◆1:, Tr.01)
        val prefixMatch = CHAPTER_PREFIX_REGEX.find(trimmed)
        if (prefixMatch != null) {
            val prefix = prefixMatch.groupValues[1]
            val body = prefixMatch.groupValues[2].trim()
            glossary[body]?.let {
                val combined = "$prefix$it"
                memoryCache[cacheKey] = combined
                return@withContext combined
            }
        }

        // 1c. Cek Room Database Cache persisten (instan <2ms tanpa koneksi internet)
        try {
            val cachedInDb = cacheDao?.getTranslation(trimmed, targetLang)
            if (!cachedInDb.isNullOrBlank()) {
                memoryCache[cacheKey] = cachedInDb
                return@withContext cachedInDb
            }
        } catch (e: Exception) {
            // fallback silently to online
        }

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
        val finalResult = postProcessTranslation(result, isEn)

        // 6. Simpan hasil ke Memory Cache dan SQLite Room Database
        if (finalResult.isNotBlank()) {
            memoryCache[cacheKey] = finalResult
            try {
                cacheDao?.insertTranslation(
                    TranslationCacheEntity(
                        sourceText = trimmed,
                        targetLang = targetLang,
                        translatedText = finalResult
                    )
                )
            } catch (e: Exception) {
                // ignore db insert error
            }
        }

        finalResult
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
        Regex("レ[○●×〇]プ", RegexOption.IGNORE_CASE) to "レイプ",
        Regex("強[○●×〇]", RegexOption.IGNORE_CASE) to "強姦",
        Regex("[○●×〇]ナサポ", RegexOption.IGNORE_CASE) to "オナサポ",
        Regex("オ[○●×〇]サポ", RegexOption.IGNORE_CASE) to "オナサポ"
    )

    private val YEAR_PATTERN = Regex("([1-6１-６])年生編")
    private val TRACK_NUM_PATTERN = Regex("トラック\\s*([0-9０-９]+)")
    private val TRACK_TAG_PATTERN = Regex("トラック")
    private val CHAPTER_PREFIX_REGEX = Regex(
        "^([#◆【\\[]?\\s*(?:(?:TR|Track|TRACK|トラック|T)?\\.?\\s*\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*|\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*))(.*)$",
        RegexOption.IGNORE_CASE
    )

    // Pola Morfologis Generik untuk Hubungan Karakter & Aksi ASMR (Berlaku untuk semua nama karakter)
    private val OJISAN_IJIME_PATTERN = Regex("([^\\s・,，]+(?:ちゃん|さん|くん)?)のおじさんを([^\\s]+)いじめ")
    private val OJISAN_IJIME2_PATTERN = Regex("([^\\s・,，]+(?:ちゃん|さん|くん)?)のおじさんに([^\\s]+)いじめ")
    private val OJISAN_KYOUYOU_PATTERN = Regex("([^\\s・,，]+(?:ちゃん|さん|くん)?)のおじさんに強要([^\\s]+)")
    private val OJISAN_KARAKAI_PATTERN = Regex("([^\\s・,，]+(?:ちゃん|さん|くん)?)のおじさんにからかい([^\\s]+)")
    private val WAKARASE_PATTERN = Regex("([^\\s・,，]+)にわからせ")
    private val GYAKUSHUU_PATTERN = Regex("([^\\s・,，]+)に逆襲！?")
    private val OCHI_NAME_PATTERN = Regex("堕ち([\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff]+)")
    private val NAME_OCHI_PATTERN = Regex("([\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff]+)堕ち")
    private val COND_JK_NAME_PATTERN = Regex("([\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff]+)JK([\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff]+)")
    private val COND_JK_PATTERN = Regex("([\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff]+)JK")

    private val POST_PROCESS_ID = listOf(
        // トラック fixes: pastikan selalu menjadi 'Trek', bukan 'jalur' atau 'lacak'
        Regex("__TRK_([0-9]+)__") to "Trek $1",
        Regex("__TRK__") to "Trek",
        Regex("\\b(?:jalur|lacak|track)\\s*(\\d+)", RegexOption.IGNORE_CASE) to "Trek $1",
        Regex("^(?:jalur|lacak)\\b", RegexOption.IGNORE_CASE) to "Trek",
        Regex("◆\\s*(?:jalur|lacak|track)\\b", RegexOption.IGNORE_CASE) to "◆ Trek",
        Regex("【(?:jalur|lacak|track)\\s*(\\d+)】", RegexOption.IGNORE_CASE) to "【Trek $1】",
        Regex("\\[(?:jalur|lacak|track)\\s*(\\d+)\\]", RegexOption.IGNORE_CASE) to "[Trek $1]",

        // Dialog & Frasa Natural (Bukan Kaku/Robotic)
        Regex("\\b(?:mengapa|kenapa)\\s+anda\\s+tidak\\s+(?:membantu|menolong)\\s+saya\\b", RegexOption.IGNORE_CASE) to "Kenapa Kamu Tidak Menolongku?",
        Regex("\\b(?:mengapa|kenapa)\\s+anda\\s+tidak\\s+(?:membantu|menolong)ku\\b", RegexOption.IGNORE_CASE) to "Kenapa Kamu Tidak Menolongku?",
        Regex("\\b(?:mengapa|kenapa)\\s+kamu\\s+tidak\\s+membantuku\\b", RegexOption.IGNORE_CASE) to "Kenapa Kamu Tidak Menolongku?",
        Regex("\\b(?:mengapa|kenapa)\\s+tidak\\s+ada\\s+yang\\s+membantu\\b", RegexOption.IGNORE_CASE) to "Kenapa Tidak Ada yang Menolong?",
        Regex("\\b(?:mengapa|kenapa)\\s+tidak\\s+membantu\\s+saya\\b", RegexOption.IGNORE_CASE) to "Kenapa Tidak Menolongku?",

        // ASMR & Doujin Trope Refinements
        Regex("\\bpelajaran gangbang\\b", RegexOption.IGNORE_CASE) to "Pelajaran Pelecehan Kelompok (Rinkan)",
        Regex("\\bgangbang\\b", RegexOption.IGNORE_CASE) to "Pelecehan Kelompok (Rinkan)",
        Regex("\\bdegradasi ke perbudakan\\b", RegexOption.IGNORE_CASE) to "Kejatuhan Menjadi Budak",
        Regex("\\bbudak yang jatuh\\b", RegexOption.IGNORE_CASE) to "Kejatuhan Menjadi Budak",
        Regex("\\bBudak Jatuh\\b", RegexOption.IGNORE_CASE) to "Kejatuhan Menjadi Budak",
        Regex("\\bgadis SMA pengutil\\b", RegexOption.IGNORE_CASE) to "siswi SMA yang tertangkap mengutil",
        Regex("\\bMengutil JK\\b", RegexOption.IGNORE_CASE) to "Siswi SMA yang Mengutil",
        Regex("\\bSendirian JK\\b", RegexOption.IGNORE_CASE) to "Siswi SMA yang Kesepian",
        Regex("\\bgadis SMA yang kesepian\\b", RegexOption.IGNORE_CASE) to "Siswi SMA yang Kesepian",
        Regex("\\bgadis SMA\\b", RegexOption.IGNORE_CASE) to "siswi SMA",
        Regex("\\bwanita SMA\\b", RegexOption.IGNORE_CASE) to "siswi SMA",
        Regex("\\bseks paman-anak\\b", RegexOption.IGNORE_CASE) to "Hubungan Terlarang (Orang Tua & Anak)",
        Regex("\\bperzinahan orang tua-anak\\b", RegexOption.IGNORE_CASE) to "Hubungan Terlarang (Orang Tua & Anak)",
        Regex("\\bhubungan terlarang orang tua dan anak\\b", RegexOption.IGNORE_CASE) to "Hubungan Terlarang (Orang Tua & Anak)",
        Regex("\\bhubungan terlarang orang tua-anak\\b", RegexOption.IGNORE_CASE) to "Hubungan Terlarang (Orang Tua & Anak)",
        Regex("\\b([A-Za-z]+)\\s+yang\\s+terjatuh\\b", RegexOption.IGNORE_CASE) to "Kejatuhan $1",
        Regex("\\b([A-Za-z]+)\\s+Jatuh Gelap\\b", RegexOption.IGNORE_CASE) to "Kejatuhan $1",
        Regex("\\bKejatuhan Gelap\\s+([A-Za-z]+)\\b", RegexOption.IGNORE_CASE) to "Kejatuhan $1",

        // Paman, Hubungan & Konteks Mesum
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
        Regex("\\blutut saya kering\\b", RegexOption.IGNORE_CASE) to "tenggorokan saya kering",

        // RJ01466903 & Wakarase / Slang Refinements
        Regex("\\[Wakase\\]", RegexOption.IGNORE_CASE) to "[Memberi Pelajaran]",
        Regex("\\[Hukuman\\]", RegexOption.IGNORE_CASE) to "[Memberi Pelajaran]",
        Regex("\\bseks antar desa\\b", RegexOption.IGNORE_CASE) to "stimulasi paha",
        Regex("\\bantar desa\\b", RegexOption.IGNORE_CASE) to "paha",
        Regex("\\bfreeloader\\b", RegexOption.IGNORE_CASE) to "rumah tumpangan",
        Regex("\\brumah freeloader\\b", RegexOption.IGNORE_CASE) to "rumah tumpangan",
        Regex("\\bpekerjaan tangan\\b", RegexOption.IGNORE_CASE) to "stimulasi tangan",
        Regex("\\bpekerjaan kaki\\b", RegexOption.IGNORE_CASE) to "stimulasi kaki",
        Regex("\\bfootjob\\b", RegexOption.IGNORE_CASE) to "stimulasi kaki",
        Regex("\\bhandjob\\b", RegexOption.IGNORE_CASE) to "stimulasi tangan",
        Regex("\\bseks tanpa kondom\\b", RegexOption.IGNORE_CASE) to "hubungan intim tanpa pengaman",
        Regex("\\bseks tanpa karet\\b", RegexOption.IGNORE_CASE) to "hubungan intim tanpa pengaman",
        Regex("\\bseks tanpa pengaman\\b", RegexOption.IGNORE_CASE) to "hubungan intim tanpa pengaman",
        Regex("\\bKlimaks di dalam mulut\\b", RegexOption.IGNORE_CASE) to "klimaks di dalam mulut",
        Regex("\\bejakulasi di mulutnya\\b", RegexOption.IGNORE_CASE) to "klimaks di dalam mulut",
        Regex("\\bejakulasi di mulut\\b", RegexOption.IGNORE_CASE) to "klimaks di dalam mulut",
        Regex("\\bejakulasi mulut\\b", RegexOption.IGNORE_CASE) to "klimaks di dalam mulut",
        Regex("\\bwanita coklat sombong\\b", RegexOption.IGNORE_CASE) to "gadis berkulit eksotis yang sombong",
        Regex("\\bwanita berkulit coklat\\b", RegexOption.IGNORE_CASE) to "gadis berkulit eksotis",
        Regex("\\bgadis berkulit coklat\\b", RegexOption.IGNORE_CASE) to "gadis berkulit eksotis",
        Regex("\\bwanita coklat\\b", RegexOption.IGNORE_CASE) to "gadis berkulit eksotis",
        Regex("\\bberbaikan dengannya\\b", RegexOption.IGNORE_CASE) to "tanda berdamai",
        Regex("\\bpekerjaan payudara\\b", RegexOption.IGNORE_CASE) to "stimulasi payudara (titfuck)",
        Regex("\\bpekerjaan pukulan\\b", RegexOption.IGNORE_CASE) to "blowjob",
        Regex("\\bmahasiswi\\b", RegexOption.IGNORE_CASE) to "oral (blowjob)",
        Regex("\\bhummus\\b", RegexOption.IGNORE_CASE) to "gigitan lembut",
        Regex("\\bkencing yang intens\\b", RegexOption.IGNORE_CASE) to "penetrasi intens",
        Regex("\\bduduk di tanah\\b", RegexOption.IGNORE_CASE) to "bersujud (dogeza)",
        Regex("\\bjus memek\\b", RegexOption.IGNORE_CASE) to "cairan cinta",
        Regex("\\bボーナストラック\\b", RegexOption.IGNORE_CASE) to "Trek Bonus",
        Regex("\\bボーナス\\s*Trek\\b", RegexOption.IGNORE_CASE) to "Trek Bonus",
        Regex("\\bBonusTrek\\b", RegexOption.IGNORE_CASE) to "Trek Bonus",
        Regex("\\bbudak daging\\b", RegexOption.IGNORE_CASE) to "budak nafsu",
        Regex("\\bestrus\\b", RegexOption.IGNORE_CASE) to "terangsang",
        Regex("\\bkembali berhubungan seks\\b", RegexOption.IGNORE_CASE) to "seks gaya doggy",
        Regex("\\bseks kembali\\b", RegexOption.IGNORE_CASE) to "seks gaya doggy",
        Regex("\\bpenyiksaan puting\\b", RegexOption.IGNORE_CASE) to "stimulasi puting",
        Regex("\\bmenuduhku secara lisan\\b", RegexOption.IGNORE_CASE) to "ejekan sensual",
        Regex("\\bTolong brengsek\\b", RegexOption.IGNORE_CASE) to "Tolong kocok",
        Regex("\\bbrengsek\\b", RegexOption.IGNORE_CASE) to "masturbasi",
        Regex("\\blapisan pembantu\\b", RegexOption.IGNORE_CASE) to "cosplayer maid",
        Regex("\\blapisan populer\\b", RegexOption.IGNORE_CASE) to "cosplayer populer",
        Regex("\\blapisan\\b", RegexOption.IGNORE_CASE) to "cosplayer",
        Regex("\\bpekerjaan sarung tangan\\b", RegexOption.IGNORE_CASE) to "handjob bersarung tangan",
        Regex("\\(HIDUNG\\s*:\\s*", RegexOption.IGNORE_CASE) to "(Tanpa SE: ",
        Regex("（HIDUNG\\s*：\\s*", RegexOption.IGNORE_CASE) to "(Tanpa SE: ",
        Regex("\\bHIDUNG\\s*:\\s*", RegexOption.IGNORE_CASE) to "Tanpa SE: ",
        Regex("^(?:オマケ|おまけ)\\s*([0-9０-９]+)[：:]?", RegexOption.IGNORE_CASE) to "Bonus $1: ",
        Regex("\\bピロートーク\\b", RegexOption.IGNORE_CASE) to "Pillow Talk (Obrolan Ranjang)",
        Regex("\\bbantal berbicara\\b", RegexOption.IGNORE_CASE) to "obrolan ranjang (pillow talk)",
        Regex("\\bbicara bebas\\b", RegexOption.IGNORE_CASE) to "Free Talk",
        Regex("\\bpembicaraan bebas\\b", RegexOption.IGNORE_CASE) to "Free Talk",
        Regex("\\bkelinci terbalik\\b", RegexOption.IGNORE_CASE) to "reverse bunny",
        Regex("\\bkostum kelinci terbalik\\b", RegexOption.IGNORE_CASE) to "reverse bunny",
        Regex("\\brute dimana aku tersesat\\b", RegexOption.IGNORE_CASE) to "rute kalah",
        Regex("\\brute dimana saya memohon.*tersesat\\b", RegexOption.IGNORE_CASE) to "rute kalah setelah memohon",
        Regex("\\bIni pertama kalinya aku hidup\\b", RegexOption.IGNORE_CASE) to "Ini pertama kalinya tanpa pengaman",
        Regex("\\bBicara Bebas\\b", RegexOption.IGNORE_CASE) to "Free Talk",
        Regex("\\bUrinoir daging\\b", RegexOption.IGNORE_CASE) to "Budak nafsu",
        Regex("\\btoilet wanita\\b", RegexOption.IGNORE_CASE) to "budak nafsu (mesubinki)",
        Regex("\\bgadis kesehatan pengiriman\\b", RegexOption.IGNORE_CASE) to "gadis panggilan (delivery health)",
        Regex("\\bkesehatan pengiriman\\b", RegexOption.IGNORE_CASE) to "layanan panggilan (delivery health)",
        Regex("\\bmangkuk ibu dan anak\\b", RegexOption.IGNORE_CASE) to "threesome ibu dan anak (oyakodon)",
        Regex("\\bmangkuk ibu-anak\\b", RegexOption.IGNORE_CASE) to "threesome ibu dan anak (oyakodon)",
        Regex("\\bpecinta dagu\\b", RegexOption.IGNORE_CASE) to "pemanja penis",
        Regex("\\bDagu menjilat\\b", RegexOption.IGNORE_CASE) to "Memanjakan penis",
        Regex("\\bpenyemaian serentak\\b", RegexOption.IGNORE_CASE) to "inseminasi serentak (tanezuke)",
        Regex("\\bpenyemaian\\b", RegexOption.IGNORE_CASE) to "inseminasi (tanezuke)",
        Regex("\\bekspresi payudara\\b", RegexOption.IGNORE_CASE) to "memerah ASI (laktasi)",
        Regex("\\bekspresi diri\\b", RegexOption.IGNORE_CASE) to "memerah ASI sendiri",
        Regex("\\bmenarikmu keluar\\b", RegexOption.IGNORE_CASE) to "membuatmu ejakulasi",
        Regex("\\bmenarik keluar\\b", RegexOption.IGNORE_CASE) to "membuat ejakulasi",
        Regex("\\b(?:penerima|resepsionis) wanita\\b", RegexOption.IGNORE_CASE) to "khusus pendengar wanita",
        Regex("\\bwanita yang menerima\\b", RegexOption.IGNORE_CASE) to "khusus pendengar wanita",
        Regex("\\bKacang lebar\\b", RegexOption.IGNORE_CASE) to "Soramame",
        Regex("\\bvelochew\\b", RegexOption.IGNORE_CASE) to "ciuman lidah (deep kiss)",
        Regex("\\bpermainan Bodo\\b", RegexOption.IGNORE_CASE) to "board game (permainan papan)",
        Regex("\\bBodo\\b", RegexOption.IGNORE_CASE) to "board game",
        Regex("\\bMusim semi tidak pernah kering\\b", RegexOption.IGNORE_CASE) to "mata air yang tak pernah kering",
        Regex("\\bpengiriman seks\\b", RegexOption.IGNORE_CASE) to "live stream seks",
        Regex("\\bpenyampaian hubungan intim\\b", RegexOption.IGNORE_CASE) to "live stream hubungan intim",
        Regex("\\bpengiriman hubungan intim\\b", RegexOption.IGNORE_CASE) to "live stream hubungan intim",
        Regex("\\bpengelupasan kulit\\b", RegexOption.IGNORE_CASE) to "membuka kulup secara lembut",
        Regex("\\bkaret sex\\b", RegexOption.IGNORE_CASE) to "seks dengan kondom",
        Regex("\\bperawat bawah\\b", RegexOption.IGNORE_CASE) to "perawat pendiam (downer)",
        Regex("\\bpiston penggerak tumpukan\\b", RegexOption.IGNORE_CASE) to "penetrasi vertikal intens (pile driver)",
        Regex("\\bvagina goreng kecil\\b", RegexOption.IGNORE_CASE) to "vagina lemah tak berdaya (zako)",
        Regex("\\bdi balik layar streamer\\b", RegexOption.IGNORE_CASE) to "streamer akun rahasia (ura-aka)",
        Regex("\\bcatu daya magis\\b", RegexOption.IGNORE_CASE) to "suplai mana (energi sihir)",
        Regex("\\bcatu daya\\b", RegexOption.IGNORE_CASE) to "suplai mana",
        Regex("\\bAyah gadis ranjau darat aktif\\b", RegexOption.IGNORE_CASE) to "Gadis Jirai-kei sugar dating (papakatsu)",
        Regex("\\branjau darat\\b", RegexOption.IGNORE_CASE) to "gaya jirai (jirai-kei)",
        Regex("\\bpenjelajahan malam\\b", RegexOption.IGNORE_CASE) to "kunjungan malam (yobai)",
        Regex("\\bperayapan malam\\b", RegexOption.IGNORE_CASE) to "kunjungan malam (yobai)",
        Regex("\\bpenis yang kasar\\b", RegexOption.IGNORE_CASE) to "penis berukuran kecil (sochin)",
        Regex("\\borang yang kasar\\b", RegexOption.IGNORE_CASE) to "pria berpenis kecil (sochin)",
        Regex("\\bpelaku masturbasi\\b", RegexOption.IGNORE_CASE) to "onahole",
        Regex("\\bbermain masturbasi\\b", RegexOption.IGNORE_CASE) to "roleplay onahole",
        Regex("\\bmainkan masturbasi\\b", RegexOption.IGNORE_CASE) to "roleplay onahole",
        Regex("\\bidiot vulgar\\b", RegexOption.IGNORE_CASE) to "desahan sensual (oho-koe)",
        Regex("\\bmemohon seumur hidup\\b", RegexOption.IGNORE_CASE) to "memohon ampun untuk nyawanya",
        Regex("\\bdikhianati oleh pria tampan\\b", RegexOption.IGNORE_CASE) to "direbut oleh pria tampan (ikemen)",
        Regex("\\bdikhianati oleh orang tua yang (?:jorok|kotor)\\b", RegexOption.IGNORE_CASE) to "direbut oleh paman yang jorok",
        Regex("\\bbersuara keren\\b", RegexOption.IGNORE_CASE) to "desahan sensual (oho-koe)",
        Regex("\\bOjo voice\\b", RegexOption.IGNORE_CASE) to "desahan kenikmatan (oho-koe)",
        Regex("\\bhisap otak\\b", RegexOption.IGNORE_CASE) to "oral peleleh otak (brain fellatio)",
        Regex("\\bputri murni yang jahat dan jatuh\\b", RegexOption.IGNORE_CASE) to "putri suci yang jatuh ke dalam kegelapan (aku-ochi)",
        Regex("\\bkebejatan jahat\\b", RegexOption.IGNORE_CASE) to "kejatuhan ke dalam kegelapan (aku-ochi)",
        Regex("\\bkuas ditarik ke bawah\\b", RegexOption.IGNORE_CASE) to "pengambilan keperawanan (fudeoroshi)",

        // Batch 10: Sister/Healing, Rental Chikan, Gibo/Stepmother, Fuuki Iin, Loli Vampire
        Regex("\\bTruk tidur\\b", RegexOption.IGNORE_CASE) to "Trek tidur",
        Regex("\\bTruk\\b", RegexOption.IGNORE_CASE) to "Trek",
        Regex("\\bseorang saudari sehat\\b", RegexOption.IGNORE_CASE) to "Suster Biarawati yang suci",
        Regex("\\bNun\\b", RegexOption.IGNORE_CASE) to "Biarawati",
        Regex("\\bLayanan Penganiaya Terbalik\\b", RegexOption.IGNORE_CASE) to "Layanan Reverse Chikan",
        Regex("\\bpenganiaya terbalik\\b", RegexOption.IGNORE_CASE) to "reverse chikan",
        Regex("\\bpermainan penganiayaan\\b", RegexOption.IGNORE_CASE) to "permainan chikan (sensual)",
        Regex("\\bpenganiayaan terbalik\\b", RegexOption.IGNORE_CASE) to "reverse chikan (pelecehan balik)",
        Regex("\\bpenganiaya\\b", RegexOption.IGNORE_CASE) to "chikan",
        Regex("\\bdianiaya\\b", RegexOption.IGNORE_CASE) to "diberi rangsangan chikan",
        Regex("\\brilis pers impregnasi\\b", RegexOption.IGNORE_CASE) to "himpitan mesra yang menghamilkan",
        Regex("\\bpers impregnasi\\b", RegexOption.IGNORE_CASE) to "himpitan mesra yang menghamilkan",
        Regex("\\bacar kawin\\b", RegexOption.IGNORE_CASE) to "bercinta tiada henti",
        Regex("\\berotis cambuk\\b", RegexOption.IGNORE_CASE) to "montok sensual",
        Regex("\\bjusku\\b", RegexOption.IGNORE_CASE) to "cairan spermaku",
        Regex("\\bjus\\b", RegexOption.IGNORE_CASE) to "cairan sperma",
        Regex("\\bibu mertuaku\\b", RegexOption.IGNORE_CASE) to "ibu tiriku",
        Regex("\\bibu mertua\\b", RegexOption.IGNORE_CASE) to "ibu tiri",
        Regex("\\bwanita kantoran pelacur\\b", RegexOption.IGNORE_CASE) to "wanita kantoran (OL) binal",
        Regex("\\bpelacur\\b", RegexOption.IGNORE_CASE) to "wanita binal",
        Regex("\\bketurunan jatuh\\b", RegexOption.IGNORE_CASE) to "tunduk menjadi pelayan darah (kenzoku)",
        Regex("\\bkerabat darah\\b", RegexOption.IGNORE_CASE) to "pelayan darah (kenzoku)",
        Regex("\\bsaudara sedarah\\b", RegexOption.IGNORE_CASE) to "pelayan darah (kenzoku)",
        Regex("\\bmemakan saudaramu\\b", RegexOption.IGNORE_CASE) to "memakanmu, Kak (Onii-chan)",
        Regex("\\bkakakmu\\b", RegexOption.IGNORE_CASE) to "kamu, Kak (Onii-chan)",
        Regex("\\bjantungnya dirusak oleh ibu tiri\\b", RegexOption.IGNORE_CASE) to "hatinya ditaklukkan oleh ibu tiri",

        // Batch 11: Sci-Fi Android, Monster Tentacle/Slime, Otome Butler, Maid Cafe, Hospital Nurse
        Regex("\\bpenis besar.*mentah\\b", RegexOption.IGNORE_CASE) to "payudara montok telanjang",
        Regex("\\bclose-up full-length\\b", RegexOption.IGNORE_CASE) to "Audio Menempel Sangat Erat Sepanjang Waktu",
        Regex("\\bperawat kotor\\b", RegexOption.IGNORE_CASE) to "perawat sensual yang mesum",
        Regex("\\brumah sakit sesat\\b", RegexOption.IGNORE_CASE) to "rumah sakit mesum penuh gairah",
        Regex("\\bperawatan seks\\b", RegexOption.IGNORE_CASE) to "layanan sensual relaksasi",
        Regex("\\bperkembangan anal\\b", RegexOption.IGNORE_CASE) to "stimulasi eksplorasi anal",
        Regex("\\bLendir\\b", RegexOption.IGNORE_CASE) to "Slime",
        Regex("\\blendir payudara besar\\b", RegexOption.IGNORE_CASE) to "monster slime berpayudara montok",
        Regex("\\bslime berpayudara besar\\b", RegexOption.IGNORE_CASE) to "monster slime berpayudara montok",
        Regex("\\bJalur\\s*(\\d+)", RegexOption.IGNORE_CASE) to "Trek $1",
        Regex("\\bButler\\b", RegexOption.IGNORE_CASE) to "Pelayan Pria (Butler)",
        Regex("\\bmaster\\b", RegexOption.IGNORE_CASE) to "Tuan (Master)",
        Regex("\\bpulang ke rumah\\b", RegexOption.IGNORE_CASE) to "kunjungan ke maid cafe",
        Regex("\\bkunjungan pertamamu ke rumah\\b", RegexOption.IGNORE_CASE) to "kunjungan pertama ke maid cafe",
        Regex("\\bpertama kalinya pulang\\b", RegexOption.IGNORE_CASE) to "kunjungan pertama ke maid cafe",
        Regex("\\bke kafe pelayan\\b", RegexOption.IGNORE_CASE) to "ke maid cafe"
    )

    private val POST_PROCESS_EN = listOf(
        // Track replacements
        Regex("__TRK_([0-9]+)__") to "Track $1",
        Regex("__TRK__") to "Track",
        Regex("\\btrack\\s*(\\d+)", RegexOption.IGNORE_CASE) to "Track $1",
        Regex("◆\\s*track\\b", RegexOption.IGNORE_CASE) to "◆ Track",
        Regex("【track\\s*(\\d+)】", RegexOption.IGNORE_CASE) to "【Track $1】",
        Regex("\\[track\\s*(\\d+)\\]", RegexOption.IGNORE_CASE) to "[Track $1]",

        // Dialog & Phrases
        Regex("\\bwhy don't you help me\\b", RegexOption.IGNORE_CASE) to "Why won't you help me?",
        Regex("\\bwhy you don't help me\\b", RegexOption.IGNORE_CASE) to "Why won't you help me?",

        // ASMR tropes
        Regex("\\[Wakase\\]", RegexOption.IGNORE_CASE) to "[Teaching a Lesson]",
        Regex("\\binter-village sex\\b", RegexOption.IGNORE_CASE) to "thighjob (sumata)",
        Regex("\\bvillage sex\\b", RegexOption.IGNORE_CASE) to "thighjob",
        Regex("\\bfreeloader\\b", RegexOption.IGNORE_CASE) to "boarding house",
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

        // B. Mencegah kata 'トラック' diterjemahkan keliru menjadi 'jalur' atau 'lacak'
        t = TRACK_NUM_PATTERN.replace(t) { m ->
            val num = m.groupValues[1]
                .replace('１', '1').replace('２', '2').replace('３', '3')
                .replace('４', '4').replace('５', '5').replace('６', '6')
                .replace('７', '7').replace('８', '8').replace('９', '9')
                .replace('０', '0')
            "__TRK_${num}__"
        }
        t = TRACK_TAG_PATTERN.replace(t, "__TRK__")
        t = t.replace(Regex("_noSE\\b", RegexOption.IGNORE_CASE), " (Tanpa Efek Suara)")

        // C. Normalisasi hiragana ambigu & istilah khusus
        t = t.replace("えっちおさそい", "エッチなお誘い")
        t = t.replace("なかなおり", "仲直り")
        t = t.replace("かのじょ", "彼女")
        t = t.replace("だいきらい", "大嫌い")
        t = t.replace("ひなは", "ヒナは")
        t = t.replace("愛吏", "アイリ")

        // D. Pola Morfologis Generik (Berlaku untuk semua karakter: Rei, Yuka, Koharu, Hanako, dll)
        t = OJISAN_IJIME_PATTERN.replace(t) { m -> "${m.groupValues[1]}による${m.groupValues[2]}での叔父へのからかい" }
        t = OJISAN_IJIME2_PATTERN.replace(t) { m -> "${m.groupValues[1]}による${m.groupValues[2]}での叔父へのからかい" }
        t = OJISAN_KYOUYOU_PATTERN.replace(t) { m -> "${m.groupValues[1]}による叔父への${m.groupValues[2]}の強要" }
        t = OJISAN_KARAKAI_PATTERN.replace(t) { m -> "${m.groupValues[1]}による${m.groupValues[2]}での叔父へのからかい" }
        t = WAKARASE_PATTERN.replace(t) { m -> "${m.groupValues[1]}へのお仕置き・" }
        t = GYAKUSHUU_PATTERN.replace(t) { m -> "${m.groupValues[1]}への反撃！" }
        t = OCHI_NAME_PATTERN.replace(t) { m -> "${m.groupValues[1]}の堕落" }
        t = NAME_OCHI_PATTERN.replace(t) { m -> "${m.groupValues[1]}への堕落" }
        t = COND_JK_NAME_PATTERN.replace(t) { m -> "${m.groupValues[1]}の女子高生・${m.groupValues[2]}" }
        t = COND_JK_PATTERN.replace(t) { m -> "${m.groupValues[1]}の女子高生" }

        // E. Slang Inti Industri ASMR (Mencegah salah arti Google Translate)
        t = t.replace("【わからせ】", "【お仕置き】")
        t = t.replace("居候先", "下宿先")
        t = t.replace("素股", "太もも刺激")
        t = t.replace("手マン", "指での刺激")
        t = t.replace("生えっち", "生セックス")
        t = t.replace("オナサポ", "マスターベーション補助")
        t = t.replace("寸止め", "寸止めオーガズム")
        t = t.replace("褐色メス", "褐色肌の女の子")
        t = t.replace("調子に乗った", "生意気な")
        t = t.replace("親子姦", "親子の禁断関係")
        t = t.replace("母子姦", "母と息子の禁断関係")
        t = t.replace("姉弟姦", "姉と弟の禁断関係")
        t = t.replace("妹姦", "妹との禁断関係")
        t = t.replace("近親相姦", "近親の禁断関係")
        t = t.replace("近親姦", "近親の禁断関係")
        t = t.replace("激ピス", "激しいピストン")
        t = t.replace("マン汁", "愛液")
        t = t.replace("はむはむ", "甘噛み")
        t = t.replace("お掃除フェラ", "お掃除オーラル")
        t = t.replace("パイズリフェラ", "胸と口での奉仕")
        t = t.replace("パイズリ", "胸での刺激")
        t = t.replace("全裸土下座", "全裸での土下座")
        t = t.replace("土下座", "土下座謝罪")

        // NOSE (No Sound Effects / No SE)
        t = t.replace("（NOSE：", "（効果音なし：")
        t = t.replace("(NOSE:", "(効果音なし：")
        t = t.replace("NOSE：", "効果音なし：")

        // Subculture DLsite Tropes
        t = t.replace("レイヤー", "コスプレイヤー")
        t = t.replace("肉奴隷", "性奴隷")
        t = t.replace("発情", "性的興奮")
        t = t.replace("手袋コキ", "手袋での手コキ")
        t = t.replace("シコシコ", "オナニー")
        t = t.replace("乳首責め", "乳首刺激")
        t = t.replace("言葉責め", "淫語での焦らし")
        t = t.replace("快楽堕ち", "快楽への屈服")
        t = t.replace("バックえっち", "バックでのセックス")
        t = t.replace("ハメ撮り", "性行為の動画撮影")
        t = t.replace("オナホまんこ", "オナホのような身体")
        t = t.replace("筆下ろし", "童貞喪失の相手")
        t = t.replace("筆おろし", "童貞喪失の相手")
        t = t.replace("いちゃらぶ", "ラブラブ")
        t = t.replace("べろちゅー", "ディープキス")
        t = t.replace("膣こき", "膣での刺激")
        t = t.replace("生オナホ", "生身のオナホ")
        t = t.replace("逆レイプ", "女性主導のセックス")
        t = t.replace("見抜き", "オナニー鑑賞")
        t = t.replace("ハメパコ", "激しいセックス")
        t = t.replace("ぷにまん", "柔らかいおまんこ")
        t = t.replace("ぷに穴", "柔らかいおまんこ")
        t = t.replace("甘堕ち", "甘い快楽への屈服")
        t = t.replace("メンエス嬢", "メンズエステのセラピスト")
        t = t.replace("メンエス", "メンズエステ")
        t = t.replace("裏オプション", "秘密の裏サービス")
        t = t.replace("裏オプ嬢", "裏サービス嬢")
        t = t.replace("裏オプ", "裏サービス")
        t = t.replace("逆バニー", "逆バニー衣装")
        t = t.replace("腰ヘコ", "腰のグラインド刺激")
        t = t.replace("全肯定", "無条件の愛と肯定")
        t = t.replace("生は初めて", "生セックスは初めて")
        t = t.replace("メス便器", "性処理用の肉便器")
        t = t.replace("絶倫", "並外れた精力")
        t = t.replace("三十路", "30代の")
        t = t.replace("バブみ", "母性あふれる甘やかし")
        t = t.replace("よしよし", "頭をなでて甘やかす")
        t = t.replace("ソープごっこ", "お風呂での密着ソーププレイ")
        t = t.replace("マットプレイ", "ソープランドのマットプレイ")
        t = t.replace("童貞強姦卒業", "無理やり奪われた童貞")
        t = t.replace("鬼ピストン", "激しい連続ピストン")
        t = t.replace("分からせる", "教え込む")
        t = t.replace("わからせる", "教え込む")
        t = t.replace("デリヘル嬢", "デリバリーヘルスの女の子")
        t = t.replace("デリヘル", "デリバリーヘルス")
        t = t.replace("チン媚び", "ちんぽに媚びる")
        t = t.replace("チンカス", "恥垢")
        t = t.replace("母娘丼", "母親と娘の3P")
        t = t.replace("姉妹丼", "姉と妹の3P")
        t = t.replace("種付け", "受精させる生中出し")
        t = t.replace("搾乳", "母乳搾り")
        t = t.replace("カウパー舐め", "我慢汁舐め")
        t = t.replace("カウパー", "我慢汁")
        t = t.replace("抜いてあげ", "射精させてあげ")
        t = t.replace("抜いてやる", "射精させてやる")
        t = t.replace("女性受けトラック", "女性向けトラック")
        t = t.replace("女性受け", "女性向け")
        t = t.replace("スパイダー騎乗位", "スパイダー体位の騎乗位")
        t = t.replace("ハメ殺し", "激しい連続ピストン")
        t = t.replace("理解らセックス", "お仕置きセックス")
        t = t.replace("理解らせ", "お仕置き")
        t = t.replace("理解ら", "お仕置き")
        t = t.replace("ベロチュー", "濃厚な舌絡ませディープキス")
        t = t.replace("ポリネシアンセックス", "全身密着の官能セックス")
        t = t.replace("糸目のお姉さん", "優しい糸目のお姉さん")
        t = t.replace("ボテ腹ズリ", "妊婦のお腹での擦り付け刺激")
        t = t.replace("ボテ腹", "臨月の妊婦腹")
        t = t.replace("密着生ハメ", "密着生セックス")
        t = t.replace("胎籠もり", "子宮への受胎")
        t = t.replace("乳交", "胸での刺激")
        t = t.replace("泡泡浴玩法", "ソープランドの泡風呂プレイ")
        t = t.replace("墊上玩法", "ソープランドのマットプレイ")
        t = t.replace("ボドゲ", "ボードゲーム")
        t = t.replace("裏垢", "裏アカウント")
        t = t.replace("生ハメ配信", "生セックスの生配信")
        t = t.replace("配信者", "配信ライバー")
        t = t.replace("投げ銭", "投げ銭スパチャ")
        t = t.replace("枯れない泉", "涸れない湧き水")
        t = t.replace("ダウナー", "物静かなダウナー")
        t = t.replace("雑魚まん", "感じやすい弱いおまんこ")
        t = t.replace("雑魚おま〇こ", "感じやすい弱いおまんこ")
        t = t.replace("皮剥き", "包皮を優しく剥く")
        t = t.replace("ゴムハメ", "コンドームでのセックス")
        t = t.replace("朝勃ち", "朝の勃起")
        t = t.replace("杭打ち", "激しい縦ピストン")
        t = t.replace("逆種付け", "女性主導の種付け")
        t = t.replace("パパ活", "パパ活のパパ活交際")
        t = t.replace("地雷ギャル", "地雷系のギャル")
        t = t.replace("粗チン", "小さな粗末なちんぽ")
        t = t.replace("つよつよオス様", "強気な絶倫男性")
        t = t.replace("魔力供給", "魔力のエネルギー供給")
        t = t.replace("逆夜這い", "女性からの夜這い")
        t = t.replace("夜這い", "夜這い訪問")
        t = t.replace("オナホごっこ", "オナホールごっこ")
        t = t.replace("危険日", "妊娠しやすい危険日")
        t = t.replace("顔面騎乗", "顔面への騎乗位")
        t = t.replace("顔騎", "顔面への騎乗位")
        t = t.replace("ごっくん", "精液のごっくん飲み")
        t = t.replace("耳孕スメント", "耳が妊娠するほどの甘い囁き")
        t = t.replace("あまハラ", "甘やかしハラスメント")
        t = t.replace("寝取られる", "他の男に奪われる")
        t = t.replace("寝取られ", "他の男に奪われるNTR")
        t = t.replace("時間停止", "時間を止める")
        t = t.replace("命乞い", "命乞いの懇願")
        t = t.replace("悪堕ち", "悪に堕ちた")
        t = t.replace("オナホコキ", "オナホールでの手コキ")
        t = t.replace("ちん嗅ぎ", "ちんぽの臭いを嗅ぐ")
        t = t.replace("バキュームフェラ", "強力な吸引フェラ")
        t = t.replace("ケモロリ", "獣耳のロリ女の子")
        t = t.replace("オホ声", "淫らな喘ぎ声オホ")
        t = t.replace("熟れ熟れ", "色気たっぷりに熟れた")

        // Batch 10 additions
        t = t.replace(Regex("[_（\\(]?(?:SE有|SE無|SEなし|SE無し|SEあり|SE\\s*ON|SE\\s*OFF|BGMなし)[）\\)]?"), "")
        t = t.replace("【BGMなし】", "")
        t = t.replace("心音なし", "鼓動なしの")
        t = t.replace("耐久添い寝トラック", "長時間添い寝の耐久トラック")
        t = t.replace("耐久添い寝", "長時間添い寝の耐久")
        t = t.replace("健全なシスター", "純粋で清らかな修道女シスター")
        t = t.replace("シスター", "修道女シスター")
        t = t.replace("聖職者", "修道女")
        t = t.replace("耳かき", "耳かき掃除")
        t = t.replace("レンタル逆痴漢サービス", "レンタル逆痴漢サービス")
        t = t.replace("逆痴漢", "女性からの逆痴漢")
        t = t.replace("レンタル痴漢屋", "レンタル痴漢サービス")
        t = t.replace("痴漢屋", "痴漢プレイサービス")
        t = t.replace("痴漢されちゃって", "痴漢プレイで刺激されちゃって")
        t = t.replace("痴漢されて", "痴漢プレイで刺激されて")
        t = t.replace("ヤリマンOL", "奔放でエッチなOL")
        t = t.replace("ヤリマン", "奔放でエッチな女の子")
        t = t.replace("社会的に終わりたくないなら", "社会的な信用を失いたくないなら")
        t = t.replace("若いザー汁", "若い男性の濃厚な精液")
        t = t.replace("ザー汁", "濃厚な精液")
        t = t.replace("義母", "義理の母")
        t = t.replace("デカケツ", "大きなお尻")
        t = t.replace("お母さんにぴゅっぴゅっして", "お母さんにいっぱい精液を出して")
        t = t.replace("確定でキミの子供", "絶対にキミの子供")
        t = t.replace("風紀活動", "風紀委員活動")
        t = t.replace("フーゾク活動", "秘密の風俗店サービス活動")
        t = t.replace("むちエロ", "豊満でエッチな")
        t = t.replace("交尾漬け", "連続の交尾")
        t = t.replace("性活", "性生活")
        t = t.replace("素股", "太ももでの素股刺激")
        t = t.replace("逆転フェンス押し付け交尾", "フェンスに押し付けられて交尾")
        t = t.replace("孕ませプレス", "妊娠させるような密着プレス")
        t = t.replace("眷属堕ち", "眷属として服従")
        t = t.replace("強制眷属", "強制的に眷属の従者にする")
        t = t.replace("眷属になるの", "血の眷属になるの")
        t = t.replace("眷属", "血の眷属")
        t = t.replace("つるぷにおまんこ", "すべすべで柔らかい身体")
        t = t.replace("甘オホ", "甘い淫らな喘ぎ声")
        t = t.replace("おにーちゃん", "お兄ちゃん")

        // Batch 11 additions: Sci-Fi Android, Monster Tentacle/Slime, Otome Butler, Maid Cafe, Hospital Nurse
        t = t.replace("生デカチチ", "大きな素肌の美乳")
        t = t.replace("デカチチ", "大きな美乳")
        t = t.replace("性処理治療", "性的なケア治療")
        t = t.replace("性処理", "性的快楽のケア")
        t = t.replace("ドスケベナース", "とてもエッチな看護師")
        t = t.replace("変態病院", "エッチで淫らな病院")
        t = t.replace("ド密着", "密着ハグ")
        t = t.replace("W耳舐め", "両耳舐め")
        t = t.replace("お耳セックス", "耳への官能的な刺激")
        t = t.replace("アナル開発", "アナルの性感開発")
        t = t.replace("マゾメス", "従順なマゾヒスト")
        t = t.replace("ガン突き", "激しいピストン突き")
        t = t.replace("フタナリ", "ふたなり")
        t = t.replace("爆乳スライム", "巨乳のモンスター・スライム")
        t = t.replace("スライム", "モンスター・スライム")
        t = t.replace("耳ぬちゅ舐め", "耳をねっとりと舐める")
        t = t.replace("ぐちゅぬちゅ", "濃厚で湿った")
        t = t.replace("むぎゅむぎゅぷにぷに", "柔らかく弾力のある")
        t = t.replace("ぷるぷるおまんこ", "柔らかく弾むような身体")
        t = t.replace("ジュポ舐め", "激しいバキューム吸引")
        t = t.replace("ご主人様化", "ご主人様を夢中にさせる")
        t = t.replace("ガチ恋", "本気で愛する")
        t = t.replace("ご帰宅用", "メイドカフェへのご来店用")
        t = t.replace("ご帰宅", "メイドカフェへのご来店")
        t = t.replace("お手手エッチ", "手を使った官能的なエッチ")
        t = t.replace("お手手えっち", "手を使った官能的なエッチ")
        t = t.replace("推眠", "催眠睡眠")
        t = t.replace("女性向け風俗", "女性向けの癒しサービス")

        // Normalisasi katakana hukum / birokrasi khas judul karya RJ
        t = t.replace("施行ニヨリ", "の施行により")
        t = t.replace("ニヨリ", "により")
        t = t.replace("ニオイテ", "において")
        t = t.replace("トスル", "とする")

        // F. Pola numerik umum bab sekolah (1年生編 -> Bab Tahun ke-1)
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
     * Menerjemahkan daftar judul bab secara berurutan dengan optimasi performa tinggi:
     * 1. Pengecekan memory cache (0ms).
     * 2. Bulk query SQLite Room DB dalam satu query SQL (batch SELECT IN).
     * 3. Hanya teks yang benar-benar belum pernah diterjemahkan yang memanggil jaringan online.
     * 4. Batch insert hasil baru ke SQLite Room DB.
     */
    suspend fun translateBatch(
        titles: List<String>,
        targetLang: String = "id",
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        if (titles.isEmpty()) return@withContext emptyList()
        val total = titles.size

        // 1. Cek judul-judul yang sudah ada di memory cache
        val uncachedIndices = mutableListOf<Int>()
        val results = ArrayList<String>(total)

        for ((idx, title) in titles.withIndex()) {
            val trimmed = title.trim()
            val cacheKey = "$targetLang:$trimmed"
            val mem = memoryCache[cacheKey]
            if (mem != null) {
                results.add(mem)
            } else {
                results.add("") // placeholder
                uncachedIndices.add(idx)
            }
        }

        // 2. Jika ada yang belum di memory cache, query Room DB sekaligus (batch SELECT IN)
        if (uncachedIndices.isNotEmpty() && cacheDao != null) {
            val missingTitles = uncachedIndices.map { titles[it].trim() }.distinct()
            try {
                val dbItems = cacheDao?.getTranslations(missingTitles, targetLang) ?: emptyList()
                val dbMap = dbItems.associateBy({ it.sourceText }, { it.translatedText })
                val remainingIndices = mutableListOf<Int>()

                for (idx in uncachedIndices) {
                    val trimmed = titles[idx].trim()
                    val dbVal = dbMap[trimmed]
                    if (!dbVal.isNullOrBlank()) {
                        results[idx] = dbVal
                        memoryCache["$targetLang:$trimmed"] = dbVal
                    } else {
                        remainingIndices.add(idx)
                    }
                }
                uncachedIndices.clear()
                uncachedIndices.addAll(remainingIndices)
            } catch (e: Exception) {
                // lanjut ke penerjemahan online jika query db gagal
            }
        }

        // 3. Untuk sisa yang benar-benar belum pernah diterjemahkan, panggil translateText
        val newToInsert = mutableListOf<TranslationCacheEntity>()
        for ((step, idx) in uncachedIndices.withIndex()) {
            val origTitle = titles[idx]
            val trans = translateText(origTitle, targetLang)
            val finalVal = trans.ifBlank { origTitle }
            results[idx] = finalVal
            if (origTitle.isNotBlank() && finalVal != origTitle) {
                newToInsert.add(TranslationCacheEntity(origTitle.trim(), targetLang, finalVal))
            }
            onProgress?.invoke(total - uncachedIndices.size + step + 1, total)
        }

        if (newToInsert.isNotEmpty()) {
            try {
                cacheDao?.insertTranslations(newToInsert)
            } catch (e: Exception) {
                // abaikan error db
            }
        }

        // Berikan callback 100% jika semua sudah dari cache
        if (uncachedIndices.isEmpty()) {
            onProgress?.invoke(total, total)
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
        refined = refined.replace(Regex("\\[\\s*\\]"), "")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex("【\\s*】"), "")
            .replace(Regex("  +"), " ")
            .trim()
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
