package com.saymaven.downloader.japaneseasmr.service.glossary

/**
 * Modul kamus ASMR dan Voice Drama Jepang.
 * Berisi onomatopoeia aksi erotis (shikoshiko, pyupyu, dll.), istilah khas DLsite,
 * posisi seksual, dan frasa desahan/klimaks agar tidak diterjemahkan harfiah oleh mesin.
 * Seluruh Regex di-compile sekali saat inisialisasi singleton.
 */
object JapaneseAsmrGlossary {

    val GLOSSARY_ID = mapOf(
        // Struktur Bab & Format
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

        // Onomatopoeia & Suara Aksi
        "シコシコ" to "Onani / Mengocok Nikmat",
        "しこしこ" to "Onani / Mengocok Nikmat",
        "ピュッピュッ" to "Pancaran Sperma",
        "ぴゅっぴゅっ" to "Pancaran Sperma",
        "パンパン" to "Benturan Panggul Cepat",
        "ぱんぱん" to "Benturan Panggul Cepat",
        "ズチュズチュ" to "Suara Basah Penetrasi",
        "ぐちゅぐちゅ" to "Suara Basah Penetrasi",
        "グチュグチュ" to "Suara Basah Penetrasi",
        "ジュポジュポ" to "Hisapan Oral (Blowjob) Intens",
        "じゅぽじゅぽ" to "Hisapan Oral (Blowjob) Intens",
        "チュパチュパ" to "Hisapan Kecupan Basah",
        "ちゅぱちゅぱ" to "Hisapan Kecupan Basah",
        "ペロペロ" to "Jilatan Lidah Sensual",
        "ぺろぺろ" to "Jilatan Lidah Sensual",
        "レロレロ" to "Jilatan Lidah Sensual",
        "れろれろ" to "Jilatan Lidah Sensual",
        "ハムハム" to "Gigitan Lembut Manja",
        "はむはむ" to "Gigitan Lembut Manja",
        "ヌプヌプ" to "Gerakan Licin Berlendir",
        "ぬちゅぬちゅ" to "Gerakan Licin Berlendir",
        "コスコス" to "Gesekan Sensual",
        "こすこす" to "Gesekan Sensual",
        "ドクドク" to "Denyutan Kencang Sperma",
        "どくどく" to "Denyutan Kencang Sperma",
        "ビクビク" to "Berkedut Nikmat",
        "びくびく" to "Berkedut Nikmat",
        "トロトロ" to "Meleleh Nikmat",
        "とろとろ" to "Meleleh Nikmat",
        "フーフー" to "Tiupan Napas Hangat Telinga",
        "ふーふー" to "Tiupan Napas Hangat Telinga",
        "カリカリ" to "Pembersihan Telinga (Earpick)",
        "かりかり" to "Pembersihan Telinga (Earpick)",
        "ぼふぼふ" to "Usapan Bulu Halus (Bonten)",

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

        // Sensual / R-18 Istilah DLsite
        "オナサポ" to "Panduan Masturbasi",
        "寸止め" to "Edging (Menahan Puncak)",
        "射精管理" to "Kontrol Orgasme",
        "搾精" to "Pengurasan Benih",
        "焦らし" to "Godaan Menahan Puncak",
        "手コキ" to "Handjob",
        "フェラチオ" to "Blowjob",
        "フェラ" to "Blowjob",
        "足コキ" to "Footjob",
        "パイズリ" to "Titfuck",
        "中出し" to "Pelepasan di Dalam (Creampie)",
        "乳首責め" to "Stimulasi Puting",
        "淫語" to "Kata-kata Nakal (Dirty Talk)",
        "催眠" to "Hipnosis",
        "わからせ" to "Penundukan Bocah Tengil",
        "理解らせ" to "Penundukan Bocah Tengil",
        "メス堕ち" to "Tunduk Pasrah",
        "快楽堕ち" to "Tunduk Pasrah pada Kenikmatan",
        "罵倒" to "Ejekan Sensual",
        "ざこ" to "Lemah",
        "ザコ" to "Lemah",
        "奉仕" to "Pelayanan Tulus",
        "ご褒美" to "Hadiah",
        "お仕置き" to "Hukuman",
        "初体験" to "Pengalaman Pertama",
        "筆下ろし" to "Pengambilan Keperawanan (First Time)",
        "筆おろし" to "Pengambilan Keperawanan (First Time)",
        "ピロートーク" to "Obrolan Ranjang (Pillow Talk)",
        "見抜き" to "Menonton Masturbasi",
        "生ハメ" to "Bercinta Tanpa Pengaman",
        "生えっち" to "Bercinta Tanpa Pengaman",
        "ゴムハメ" to "Bercinta dengan Kondom",
        "駅弁" to "Posisi Gendong (Ekiben)",
        "正常位" to "Posisi Misionaris",
        "騎乗位" to "Posisi Cowgirl",
        "対面座位" to "Posisi Duduk Berhadapan",
        "バック" to "Gaya Doggy (Dari Belakang)",
        "顔面騎乗" to "Facesitting (Duduk di Wajah)",
        "危険日" to "Masa Subur (Hari Rawan Hamil)",
        "種付け" to "Inseminasi Pembuahan",
        "朝勃ち" to "Ereksi Pagi Hari",
        "ザー汁" to "Cairan Sperma Kental",
        "若いザー汁" to "Cairan Sperma Kental",
        "抜いてあげる" to "Kukocokkan Sampai Keluar",
        "抜いてやる" to "Kukocokkan Sampai Keluar"
    )

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

    /**
     * Membersihkan fuseji dan menormalkan teks Jepang ke istilah yang dipahami Google/LLM
     * dengan benar tanpa salah arti harfiah.
     */
    fun preProcessJapanese(text: String): String {
        var t = text
        for ((regex, rep) in FUSEJI_RULES) {
            t = t.replace(regex, rep)
        }
        t = t.replace("○", "").replace("●", "").replace("×", "").replace("〇", "")

        // Slang & Onomatopoeia normalizations
        t = t.replace("シコシコ", "オナニーの刺激")
        t = t.replace("しこしこ", "オナニーの刺激")
        t = t.replace("ピュッピュッ", "射精の勢い")
        t = t.replace("ぴゅっぴゅっ", "射精の勢い")
        t = t.replace("ジュポジュポ", "激しいフェラチオ")
        t = t.replace("じゅぽじゅぽ", "激しいフェラチオ")
        t = t.replace("パンパン", "激しいピストン")
        t = t.replace("ぱんぱん", "激しいピストン")
        t = t.replace("ズチュズチュ", "濡れた激しいピストン")
        t = t.replace("ぐちゅぐちゅ", "濡れた激しいピストン")
        t = t.replace("グチュグチュ", "濡れた激しいピストン")
        t = t.replace("ヌプヌプ", "濡れて滑らかな挿入")
        t = t.replace("ぬちゅぬちゅ", "濡れて滑らかな挿入")

        // Dialog Desahan (mencegah "aku pergi")
        t = t.replace("いっちゃう", "イッてしまう（絶頂）")
        t = t.replace("逝っちゃう", "イッてしまう（絶頂）")
        t = t.replace("イッちゃう", "イッてしまう（絶頂）")
        t = t.replace("イキそう", "絶頂しそう")
        t = t.replace("いきそう", "絶頂しそう")
        t = t.replace("イカせて", "絶頂させて")
        t = t.replace("いかせて", "絶頂させて")
        t = t.replace("おかしくなっちゃう", "快楽で頭がおかしくなりそう")
        t = t.replace("壊れちゃう", "快楽で壊れそう")
        t = t.replace("気持ち良すぎ", "とても気持ちいい")
        t = t.replace("きもちよすぎ", "とても気持ちいい")
        t = t.replace("きもちいい", "気持ちいい")
        t = t.replace("きもちい", "気持ちいい")
        t = t.replace("いっぱい出して", "精液をたくさん出して")
        t = t.replace("中にいっぱい", "膣内にたくさん出して")
        t = t.replace("抜いてあげる", "射精させてあげる")
        t = t.replace("抜いてやる", "射精させてやる")

        // Anatomi (mencegah glitch Cat/kucing atau ayam)
        t = t.replace("おちんぽ", "ペニス")
        t = t.replace("ちんぽ", "ペニス")
        t = t.replace("おちんちん", "ペニス")
        t = t.replace("ちんちん", "ペニス")
        t = t.replace("肉棒", "ペニス")
        t = t.replace("おまんこ", "女性器の膣")
        t = t.replace("まんこ", "女性器の膣")
        t = t.replace("ザー汁", "濃厚な精液")

        // Trope DLsite
        t = t.replace("駅弁", "抱き上げ立ちバック体位")
        t = t.replace("生ハメ", "生セックス")
        t = t.replace("生えっち", "生セックス")
        t = t.replace("オナサポ", "マスターベーション補助")
        t = t.replace("寸止め", "寸止めオーガズム")
        t = t.replace("搾精", "精液搾取")
        t = t.replace("素股", "太もも刺激")
        t = t.replace("手マン", "指での刺激")
        t = t.replace("筆下ろし", "童貞喪失")
        t = t.replace("筆おろし", "童貞喪失")
        t = t.replace("ピロートーク", "ベッドでの会話ピロートーク")
        t = t.replace("わからせ", "お仕置き")
        t = t.replace("理解らせ", "お仕置き")
        t = t.replace("メス堕ち", "快楽への屈服")
        t = t.replace("悪堕ち", "悪への堕落")

        return t
    }
}
