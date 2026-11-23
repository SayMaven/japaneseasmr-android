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

        // DLsite Title Tropes & Format
        "土下座" to "Dogeza (Sujud Memohon)",
        "全裸土下座" to "Sujud Telanjang Bulat",
        "即ヌキ" to "Langsung Ejakulasi",
        "即ヌキ110円" to "Langsung Ejakulasi 110 Yen",
        "ヌキ" to "Ejakulasi",
        "抜き" to "Ejakulasi",
        "ハーレム" to "Harem",
        "ナース" to "Perawat",
        "ハーレムナース" to "Harem Perawat",
        "パパ活" to "Kencan Berbayar (Papa Katsu)",
        "パパ生活" to "Kehidupan Papa Katsu (Sugar Dating)",
        "おじさん" to "Paman (Om-om)",
        "見下し" to "Merendahkan",
        "快楽堕ち" to "Tunduk Pasrah pada Kenikmatan",
        "オホ" to "Desahan Ahegao (Oho)",
        "ちマゾ" to "Sangat Masokis (Chimaso)",
        "チマゾ" to "Sangat Masokis (Chimaso)",
        "オナホ化" to "Dijadikan Boneka Pemuas (Onaho)",
        "バイノーラル" to "Audio Binaural (3D)",
        "バイノーラルサウンド" to "Audio Binaural (3D)",
        "ダウナー" to "Gadis Pendiam (Downer)",
        "ダウナーJK" to "Siswi SMA Pendiam",
        "JK" to "Siswi SMA (JK)",
        "全裸" to "Telanjang Bulat",
        "必死に" to "Mati-matian",
        "泣きながら" to "Sambil Menangis",
        "調教" to "Pelatihan Penundukan Disiplin",
        "わからせ調教" to "Pelatihan Penundukan Bocah Tengil",
        "勃起不全" to "Gangguan Ereksi",
        "入院したら" to "Saat Masuk Rumah Sakit",
        "夜勤" to "Dinas Malam",
        "再会" to "Bertemu Kembali",
        "巨根" to "Penis Besar",
        "屈服" to "Tunduk Pasrah",
        "別離" to "Perpisahan",
        "誘惑" to "Godaan",
        "性交" to "Persetubuhan",
        "湯気" to "Uap Hangat",
        "精液" to "Sperma",
        "嗅ぎ" to "Menghirup / Mengendus",

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
     * Membersihkan fuseji dan menormalkan istilah khusus ASMR/DLsite pada judul
     * tanpa merusak tata bahasa alami Jepang.
     * Jika [forLlm] = true, hanya membersihkan fuseji agar LLM menerima nuansa slang asli.
     */
    fun preProcessJapanese(text: String, forLlm: Boolean = false): String {
        var t = text
        for ((regex, rep) in FUSEJI_RULES) {
            t = t.replace(regex, rep)
        }
        t = t.replace("○", "").replace("●", "").replace("×", "").replace("〇", "")
        if (forLlm) return t

        // Slang & Onomatopoeia normalizations (tanpa sisipan kurung kaku)
        t = t.replace("シコシコ", "オナニー")
        t = t.replace("しこしこ", "オナニー")
        t = t.replace("ジュポジュポ", "激しいフェラチオ")
        t = t.replace("じゅぽじゅぽ", "激しいフェラチオ")
        t = t.replace("ズチュズチュ", "激しいピストン")
        t = t.replace("ぐちゅぐちゅ", "激しいピストン")
        t = t.replace("グチュグチュ", "激しいピストン")

        // Anatomi (mencegah glitch harfiah mesin penerjemah)
        t = t.replace("おちんぽ", "ペニス")
        t = t.replace("ちんぽ", "ペニス")
        t = t.replace("おちんちん", "ペニス")
        t = t.replace("ちんちん", "ペニス")
        t = t.replace("肉棒", "ペニス")
        t = t.replace("おまんこ", "ヴァギナ")
        t = t.replace("まんこ", "ヴァギナ")
        t = t.replace("ザー汁", "精液")

        // Trope DLsite yang sering salah arti jika dibiarkan mentah
        t = t.replace("駅弁", "立ちバック")
        t = t.replace("生ハメ", "生セックス")
        t = t.replace("生えっち", "生セックス")
        t = t.replace("オナサポ", "オナニーサポート")
        t = t.replace("素股", "太もも摩擦")
        t = t.replace("手マン", "指愛撫")
        t = t.replace("筆下ろし", "初体験")
        t = t.replace("筆おろし", "初体験")
        t = t.replace("わからせ調教", "懲らしめの調教")
        t = t.replace("理解らせ調教", "懲らしめの調教")
        t = t.replace("わからせ", "懲らしめ")
        t = t.replace("理解らせ", "懲らしめ")
        t = t.replace("ちマゾ", "マゾヒスト")
        t = t.replace("チマゾ", "マゾヒスト")
        t = t.replace("オナホ化", "オナホール化")

        return t
    }

    /**
     * Pra-pemrosesan ringan khusus subtitle/dialog agar alur percakapan tetap natural.
     */
    fun preProcessForSubtitle(text: String): String {
        var t = text
        for ((regex, rep) in FUSEJI_RULES) {
            t = t.replace(regex, rep)
        }
        return t.replace("○", "").replace("●", "").replace("×", "").replace("〇", "")
    }
}
