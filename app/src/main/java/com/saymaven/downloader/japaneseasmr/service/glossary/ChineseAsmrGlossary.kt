package com.saymaven.downloader.japaneseasmr.service.glossary

/**
 * Modul kamus ASMR dan Voice Drama Mandarin (Subtitle / Lirik .lrc / .srt).
 * Menormalisasi slang percakapan dan istilah erotis Mandarin agar tidak diterjemahkan harfiah
 * oleh Google Translate (misal: 小鸡鸡 menjadi anak ayam, 肉棒 menjadi tusuk daging, dll.).
 */
object ChineseAsmrGlossary {

    private val CN_REPLACEMENTS = listOf(
        // Anatomi Pria (mencegah ayam kecil / anak ayam / tusuk daging / kokang)
        "小鸡鸡" to "阴茎",
        "小JJ" to "阴茎",
        "小弟弟" to "阴茎",
        "大肉棒" to "阴茎",
        "肉棒" to "阴茎",
        "大鸡巴" to "阴茎",
        "鸡巴" to "阴茎",
        "巨根" to "巨大阴茎",
        "马眼" to "尿道口",
        "龟头" to "阴茎头部",

        // Anatomi Wanita (mencegah glitch kucing/cat atau sekadar lubang)
        "小穴" to "女性阴道",
        "骚穴" to "女性阴道",
        "肉穴" to "女性阴道",
        "骚逼" to "女性阴道",
        "小逼" to "女性阴道",
        "蜜穴" to "女性阴道",
        "嫩穴" to "女性阴道",
        "花穴" to "女性阴道",
        "小红豆" to "阴蒂",
        "骚乳" to "丰满乳房",
        "骚屁股" to "性感臀部",

        // Cairan & Ejakulasi
        "浓精" to "浓稠精液",
        "白浊" to "白色精液",
        "精水" to "精液",
        "骚水" to "爱液",
        "淫水" to "爱液",
        "水好多" to "非常湿润",
        "流了好多水" to "流出了很多爱液",
        "快射了" to "快要射精了",
        "要射了" to "快要射精了",
        "射在里面" to "体内射精",
        "射在子宫" to "子宫内射精",
        "内射" to "体内射精",
        "中出" to "体内射精",
        "颜射" to "脸部射精",
        "口爆" to "口腔内射精",

        // Aksi & Aktivitas Seksual/ASMR
        "口交" to "口交奉仕",
        "吹箫" to "口交奉仕",
        "深喉" to "深喉口交",
        "乳交" to "胸部刺激",
        "足交" to "足部刺激",
        "手淫" to "手淫自慰",
        "打飞机" to "手淫自慰",
        "撸管" to "手淫自慰",
        "抽送" to "激烈的进出抽插",
        "抽插" to "激烈的进出抽插",
        "插进去" to "深入插入体内",
        "顶到底" to "顶到最深处",
        "顶到了" to "碰到了最深处",
        "做爱" to "亲密性交",
        "寸止" to "忍耐高潮边缘",

        // Desahan & Dialog Intim (mencegah "aku pergi")
        "我要去了" to "我快要高潮绝顶了",
        "要去了" to "我快要高潮绝顶了",
        "去了去了" to "我高潮绝顶了",
        "高潮了" to "达到高潮绝顶了",
        "好舒服啊" to "感觉非常舒服非常享受",
        "好舒服" to "感觉非常舒服享受",
        "好爽" to "感觉太美妙享受了",
        "爽死了" to "感觉太美妙享受了",
        "太爽了" to "感觉太美妙享受了",
        "不行了" to "快要忍不住了",
        "受不了了" to "快要忍受不住快感了",
        "饶了我吧" to "求求你饶了我吧",
        "不要停" to "请千万不要停下来",
        "再深一点" to "请再深入一点",
        "用力一点" to "请更加用力一点",

        // Panggilan & Karakter
        "小骚货" to "性感情趣的女孩子",
        "小浪货" to "性感情趣的女孩子",
        "小母狗" to "乖顺顺从的女孩子",
        "肉便器" to "发泄欲望的奴隶",
        "大叔" to "叔叔"
    )

    // Penanda judul khusus rilis Mandarin / terjemahan Mandarin DLsite
    private val CHINESE_DISTINCTIVE_MARKERS = listOf(
        "【中文】", "【中文版】", "【漢化】", "【汉化】", "【国语】", "【普通话】", "【台配】", "【字幕】",
        "中文音声", "漢化組", "汉化组", "字幕组", "字幕組"
    )

    // Karakter Hanzi Simplified spesifik yang TIDAK ada dalam daftar Jouyou/Jinmeiyou Kanji Jepang
    private val CHINESE_SPECIFIC_CHARS = setOf(
        '这', '们', '么', '谁', '给', '说', '话', '没', '让', '从', '时', '样', '点', '还', '把', '被'
    )

    /**
     * Mendeteksi apakah teks mengandung huruf Kana Jepang (Hiragana/Katakana).
     */
    fun containsJapaneseKana(text: String): Boolean {
        for (char in text) {
            val ub = Character.UnicodeBlock.of(char)
            if (ub == Character.UnicodeBlock.HIRAGANA || ub == Character.UnicodeBlock.KATAKANA) {
                return true
            }
        }
        return false
    }

    /**
     * Mendeteksi apakah teks benar-benar berbahasa Mandarin (bukan judul Jepang yang menggunakan Kanji).
     */
    fun isDefinitivelyChinese(text: String): Boolean {
        if (containsJapaneseKana(text)) return false
        for (marker in CHINESE_DISTINCTIVE_MARKERS) {
            if (text.contains(marker, ignoreCase = true)) return true
        }
        for (char in text) {
            if (char in CHINESE_SPECIFIC_CHARS) return true
        }
        return false
    }

    /**
     * Kompatibilitas alias untuk isDefinitivelyChinese.
     */
    fun containsChinese(text: String): Boolean = isDefinitivelyChinese(text)

    /**
     * Menormalisasi slang teks Mandarin sebelum dikirim ke mesin terjemahan.
     */
    fun preProcessChinese(text: String): String {
        var t = text
        for ((target, replacement) in CN_REPLACEMENTS) {
            if (t.contains(target)) {
                t = t.replace(target, replacement)
            }
        }
        return t
    }
}
