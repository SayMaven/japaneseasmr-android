package com.saymaven.downloader.japaneseasmr.service.glossary

/**
 * Filter pasca-terjemahan (Post-Processing) untuk Bahasa Indonesia.
 * Berfungsi membersihkan hasil terjemahan mesin yang kaku/formal,
 * mengubah kata ganti robotik (Anda/Saya) menjadi bahasa bisikan santai (kamu/aku),
 * serta memperbaiki kekeliruan umum (tusuk daging, ayam kecil, kucing, aku pergi, dll.).
 */
object IndonesianPostFilter {

    private val REFINEMENTS = listOf(
        // Kata Ganti Formal ke Santai (Bisikan Intim ASMR)
        Regex("\\bAnda\\b") to "kamu",
        Regex("\\banda\\b") to "kamu",
        Regex("\\bSaya\\b") to "aku",
        Regex("\\bsaya\\b") to "aku",
        Regex("\\bBeliau\\b") to "dia",
        Regex("\\bbeliau\\b") to "dia",

        // Frasa Kaku Desahan & Kenikmatan (Mencegah "Aku pergi")
        Regex("\\b(?:aku|saya)\\s+akan\\s+pergi\\b", RegexOption.IGNORE_CASE) to "aku mau keluar!",
        Regex("\\b(?:aku|saya)\\s+sudah\\s+pergi\\b", RegexOption.IGNORE_CASE) to "aku sudah klimaks!",
        Regex("\\b(?:aku|saya)\\s+pergi\\b", RegexOption.IGNORE_CASE) to "aku mau keluar!",
        Regex("\\bakan\\s+pergi\\b", RegexOption.IGNORE_CASE) to "mau keluar",
        Regex("\\bbiarkan\\s+aku\\s+pergi\\b", RegexOption.IGNORE_CASE) to "buat aku klimaks",
        Regex("\\bmerasa\\s+baik\\b", RegexOption.IGNORE_CASE) to "terasa nikmat",
        Regex("\\bmerasa\\s+enak\\b", RegexOption.IGNORE_CASE) to "terasa nikmat",
        Regex("\\bsangat\\s+nyaman\\b", RegexOption.IGNORE_CASE) to "enak sekali",
        Regex("\\btidak\\s+bisa\\s+lagi\\b", RegexOption.IGNORE_CASE) to "nggak kuat lagi",
        Regex("\\btidak\\s+bisa\\s+melakukannya\\b", RegexOption.IGNORE_CASE) to "nggak tahan lagi",

        // Anatomi Organ Intim (Mengoreksi ayam kecil, tusuk daging, kucing/cat, dll.)
        Regex("\\banak\\s+ayam\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\bayam\\s+kecil\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\btusuk\\s+daging\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\bstik\\s+daging\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\btongkat\\s+daging\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\bkokang\\b", RegexOption.IGNORE_CASE) to "penis",
        Regex("\\bkucing\\b", RegexOption.IGNORE_CASE) to "kewanitaan",
        Regex("\\bCat\\b") to "vagina",
        Regex("\\bcat\\b") to "vagina",
        Regex("\\blubang\\s+kecil\\b", RegexOption.IGNORE_CASE) to "daerah kewanitaan",

        // Aktivitas Seksual & ASMR
        Regex("\\bpekerjaan\\s+pukulan\\b", RegexOption.IGNORE_CASE) to "blowjob",
        Regex("\\bpekerjaan\\s+tangan\\b", RegexOption.IGNORE_CASE) to "handjob",
        Regex("\\bpekerjaan\\s+kaki\\b", RegexOption.IGNORE_CASE) to "footjob",
        Regex("\\bpekerjaan\\s+payudara\\b", RegexOption.IGNORE_CASE) to "stimulasi payudara (titfuck)",
        Regex("\\bpekerjaan\\s+sarung\\s+tangan\\b", RegexOption.IGNORE_CASE) to "handjob bersarung tangan",
        Regex("\\bsemburan\\s+garam\\b", RegexOption.IGNORE_CASE) to "pelepasan cairan (squirting)",
        Regex("\\btiupan\\s+garam\\b", RegexOption.IGNORE_CASE) to "pelepasan cairan (squirting)",
        Regex("\\bejakulasi\\s+mulut\\b", RegexOption.IGNORE_CASE) to "klimaks di mulut",
        Regex("\\bkeluar\\s+di\\s+mulut\\b", RegexOption.IGNORE_CASE) to "klimaks di mulut",
        Regex("\\bseks\\s+antar\\s+desa\\b", RegexOption.IGNORE_CASE) to "stimulasi paha (sumata)",
        Regex("\\bantar\\s+desa\\b", RegexOption.IGNORE_CASE) to "stimulasi paha",
        Regex("\\blelaki\\s+tua\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\bpria\\s+tua\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\borang\\s+tua\\b", RegexOption.IGNORE_CASE) to "Paman",
        Regex("\\bbantal\\s+berbicara\\b", RegexOption.IGNORE_CASE) to "obrolan ranjang (pillow talk)",
        Regex("\\bmenuduh\\s+secara\\s+lisan\\b", RegexOption.IGNORE_CASE) to "ejekan sensual",
        Regex("\\bdukungan\\s+bawang\\b", RegexOption.IGNORE_CASE) to "panduan masturbasi (onasapo)",
        Regex("\\bbawang\\b", RegexOption.IGNORE_CASE) to "onasapo",
        Regex("\\bkontrol\\s+tepi\\b", RegexOption.IGNORE_CASE) to "edging (menahan puncak)",
        Regex("\\bejakulasi\\s+tepi\\b", RegexOption.IGNORE_CASE) to "edging (menahan puncak)",
        Regex("\\bair\\s+mani\\s+tebal\\b", RegexOption.IGNORE_CASE) to "sperma kental",
        Regex("\\bmenarikmu\\s+keluar\\b", RegexOption.IGNORE_CASE) to "membuatmu keluar",
        Regex("\\bmenarik\\s+keluar\\b", RegexOption.IGNORE_CASE) to "membuat keluar",
        Regex("\\bberkelahi\\s+secara\\s+seksual\\b", RegexOption.IGNORE_CASE) to "bercinta",
        Regex("\\bpermainan\\s+seks\\b", RegexOption.IGNORE_CASE) to "bercinta",
        Regex("\\bkuas\\s+ditarik\\s+ke\\s+bawah\\b", RegexOption.IGNORE_CASE) to "pengambilan keperawanan (fudeoroshi)",
        Regex("\\bkotak\\s+makan\\s+siang\\s+stasiun\\b", RegexOption.IGNORE_CASE) to "posisi gendong (ekiben)",
        Regex("\\bnasi\\s+kotak\\s+stasiun\\b", RegexOption.IGNORE_CASE) to "posisi gendong (ekiben)"
    )

    /**
     * Menyaring dan menyempurnakan hasil terjemahan bahasa Indonesia
     * agar terdengar natural, santai, dan sesuai konteks ASMR.
     */
    fun refineIndonesian(text: String): String {
        var result = text
        for ((regex, rep) in REFINEMENTS) {
            result = result.replace(regex, rep)
        }
        return result
            .replace(Regex("\\[\\s*\\]"), "")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex("【\\s*】"), "")
            .replace(Regex("  +"), " ")
            .trim()
    }
}
