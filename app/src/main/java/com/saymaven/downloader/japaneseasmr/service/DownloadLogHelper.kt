package com.saymaven.downloader.japaneseasmr.service

import com.saymaven.downloader.japaneseasmr.JapaneseAsmrApp
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import java.util.Locale

object DownloadLogHelper {

    fun getAppLanguage(): String {
        return try {
            val pref = PreferencesManager(JapaneseAsmrApp.instance).getInitialAppLanguage()
            if (pref == "system") {
                val sysLang = Locale.getDefault().language.lowercase()
                when {
                    sysLang.startsWith("in") || sysLang.startsWith("id") -> "in"
                    sysLang.startsWith("ja") -> "ja"
                    sysLang.startsWith("zh") -> "zh"
                    else -> "en"
                }
            } else {
                pref
            }
        } catch (e: Exception) {
            "en"
        }
    }

    fun addedToQueue(rjid: String): String = when (getAppLanguage()) {
        "in" -> "[+] Ditambahkan ke antrean: $rjid"
        "ja" -> "[+] キューに追加されました: $rjid"
        "zh" -> "[+] 已添加到队列: $rjid"
        else -> "[+] Added to queue: $rjid"
    }

    fun queueCleared(): String = when (getAppLanguage()) {
        "in" -> "[i] Antrean unduhan dibersihkan."
        "ja" -> "[i] ダウンロードキューがクリアされました。"
        "zh" -> "[i] 下载队列已清空。"
        else -> "[i] Download queue cleared."
    }

    fun removedFromQueue(rjid: String): String = when (getAppLanguage()) {
        "in" -> "[-] Dihapus dari antrean: $rjid"
        "ja" -> "[-] キューから削除されました: $rjid"
        "zh" -> "[-] 已从队列移除: $rjid"
        else -> "[-] Removed from queue: $rjid"
    }

    fun startProcessing(): String = when (getAppLanguage()) {
        "in" -> "[i] Memulai proses pemrosesan antrean unduhan..."
        "ja" -> "[i] ダウンロードキューの処理を開始します..."
        "zh" -> "[i] 开始处理下载队列..."
        else -> "[i] Starting download queue processing..."
    }

    fun processingItem(idx: Int, total: Int, rjid: String): String = when (getAppLanguage()) {
        "in" -> "[*] Memproses [$idx/$total]: $rjid"
        "ja" -> "[*] 処理中 [$idx/$total]: $rjid"
        "zh" -> "[*] 正在处理 [$idx/$total]: $rjid"
        else -> "[*] Processing [$idx/$total]: $rjid"
    }

    fun fileAlreadyExists(rjid: String, fileName: String): String = when (getAppLanguage()) {
        "in" -> "  [i] File audio [$rjid] sudah ada di penyimpanan ($fileName)."
        "ja" -> "  [i] 音声ファイル [$rjid] はすでに保存されています ($fileName)。"
        "zh" -> "  [i] 音频文件 [$rjid] 已在本地存储存在 ($fileName)。"
        else -> "  [i] Audio file [$rjid] already exists in storage ($fileName)."
    }

    fun skippingDownload(): String = when (getAppLanguage()) {
        "in" -> "  [i] Melewati proses unduhan..."
        "ja" -> "  [i] ダウンロードをスキップします..."
        "zh" -> "  [i] 跳过下载进程..."
        else -> "  [i] Skipping download..."
    }

    fun statusAlreadyExists(): String = when (getAppLanguage()) {
        "in" -> "Sudah ada di penyimpanan (Dilewati)"
        "ja" -> "すでに保存済み (スキップ)"
        "zh" -> "已在本地存储 (跳过)"
        else -> "Already in storage (Skipped)"
    }

    fun statusFetchingMetadata(): String = when (getAppLanguage()) {
        "in" -> "Mengambil metadata karya..."
        "ja" -> "作品情報を取得中..."
        "zh" -> "正在获取作品元数据..."
        else -> "Fetching work metadata..."
    }

    fun statusFetchingFailed(): String = when (getAppLanguage()) {
        "in" -> "Gagal mengambil metadata"
        "ja" -> "作品情報の取得に失敗"
        "zh" -> "获取作品元数据失败"
        else -> "Failed to fetch metadata"
    }

    fun metadataFetchFailed(rjid: String, err: String?): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal mengambil metadata $rjid: $err"
        "ja" -> "[!] $rjid のメタデータ取得に失敗しました: $err"
        "zh" -> "[!] 获取 $rjid 的元数据失败: $err"
        else -> "[!] Failed to fetch metadata for $rjid: $err"
    }

    fun tracksFound(count: Int, trackNames: String): String = when (getAppLanguage()) {
        "in" -> "  [i] Ditemukan $count track ($trackNames)"
        "ja" -> "  [i] $count 個のトラックが見つかりました ($trackNames)"
        "zh" -> "  [i] 发现 $count 个音轨 ($trackNames)"
        else -> "  [i] Found $count tracks ($trackNames)"
    }

    fun downloadingCover(): String = when (getAppLanguage()) {
        "in" -> "  [1/3] Mengunduh cover image..."
        "ja" -> "  [1/3] カバー画像をダウンロード中..."
        "zh" -> "  [1/3] 正在下载封面图片..."
        else -> "  [1/3] Downloading cover image..."
    }

    fun downloadingTrackLog(name: String, idx: Int, total: Int): String = when (getAppLanguage()) {
        "in" -> "  [2/3] Mengunduh $name [$idx/$total]..."
        "ja" -> "  [2/3] $name をダウンロード中 [$idx/$total]..."
        "zh" -> "  [2/3] 正在下载 $name [$idx/$total]..."
        else -> "  [2/3] Downloading $name [$idx/$total]..."
    }

    fun statusDownloadingTrack(name: String, idx: Int, total: Int, pct: Int? = null): String = when (getAppLanguage()) {
        "in" -> if (pct != null) "Mengunduh $name [$idx/$total] ($pct%)" else "Mengunduh $name [$idx/$total]"
        "ja" -> if (pct != null) "$name をダウンロード中 [$idx/$total] ($pct%)" else "$name をダウンロード中 [$idx/$total]"
        "zh" -> if (pct != null) "正在下载 $name [$idx/$total] ($pct%)" else "正在下载 $name [$idx/$total]"
        else -> if (pct != null) "Downloading $name [$idx/$total] ($pct%)" else "Downloading $name [$idx/$total]"
    }

    fun trackDownloadFailed(name: String): String = when (getAppLanguage()) {
        "in" -> "  [!] Gagal mengunduh track $name"
        "ja" -> "  [!] トラック $name のダウンロードに失敗しました"
        "zh" -> "  [!] 下载音轨 $name 失败"
        else -> "  [!] Failed to download track $name"
    }

    fun statusDownloadFailed(): String = when (getAppLanguage()) {
        "in" -> "Gagal mengunduh audio"
        "ja" -> "音声のダウンロードに失敗"
        "zh" -> "音频下载失败"
        else -> "Failed to download audio"
    }

    fun processFailed(rjid: String): String = when (getAppLanguage()) {
        "in" -> "[ERROR] Gagal memproses $rjid"
        "ja" -> "[エラー] $rjid の処理に失敗しました"
        "zh" -> "[错误] 处理 $rjid 失败"
        else -> "[ERROR] Failed to process $rjid"
    }

    fun mergingAndTaggingLog(): String = when (getAppLanguage()) {
        "in" -> "  [3/3] Menggabungkan audio, menyematkan metadata & cover art..."
        "ja" -> "  [3/3] 音声を結合し、メタデータとカバーアートを埋め込み中..."
        "zh" -> "  [3/3] 正在合并音频、写入元数据并嵌入封面..."
        else -> "  [3/3] Merging audio, embedding metadata & cover art..."
    }

    fun statusMerging(): String = when (getAppLanguage()) {
        "in" -> "Menggabungkan audio & menyematkan metadata..."
        "ja" -> "音声を結合しメタデータを埋め込み中..."
        "zh" -> "正在合并音频并写入元数据..."
        else -> "Merging audio & embedding metadata..."
    }

    fun mergingTracksCount(count: Int): String = when (getAppLanguage()) {
        "in" -> "  [+] Menggabungkan $count track audio (Fast Remux)..."
        "ja" -> "  [+] $count 個の音声トラックを結合中 (Fast Remux)..."
        "zh" -> "  [+] 正在合并 $count 个音轨 (Fast Remux)..."
        else -> "  [+] Merging $count audio tracks (Fast Remux)..."
    }

    fun chaptersMapped(count: Int): String = when (getAppLanguage()) {
        "in" -> "  [i] Berhasil memetakan $count bab cerita!"
        "ja" -> "  [i] $count 個のチャプターを正常にマッピングしました！"
        "zh" -> "  [i] 成功映射 $count 个故事章节！"
        else -> "  [i] Successfully mapped $count story chapters!"
    }

    fun cueSheetCreated(name: String): String = when (getAppLanguage()) {
        "in" -> "  [+] Berkas CUE Sheet berhasil dibuat: $name.cue"
        "ja" -> "  [+] CUEシートが正常に作成されました: $name.cue"
        "zh" -> "  [+] CUE 文件已成功生成: $name.cue"
        else -> "  [+] CUE Sheet successfully created: $name.cue"
    }

    fun cueSheetSkipped(): String = when (getAppLanguage()) {
        "in" -> "  [i] Penanda bab disematkan langsung ke database player (CUE Sheet dilewati)"
        "ja" -> "  [i] チャプターはプレイヤーデータベースに直接埋め込まれました (CUEシートはスキップ)"
        "zh" -> "  [i] 章节标记已直接写入播放器数据库 (跳过生成 CUE 文件)"
        else -> "  [i] Chapter markers embedded directly into player database (CUE Sheet skipped)"
    }

    fun statusCompleted(): String = when (getAppLanguage()) {
        "in" -> "Selesai diunduh"
        "ja" -> "ダウンロード完了"
        "zh" -> "下载完成"
        else -> "Download completed"
    }

    fun itemSuccess(path: String, sizeStr: String): String = when (getAppLanguage()) {
        "in" -> "[SUCCESS] Selesai: $path ($sizeStr)"
        "ja" -> "[完了] 完了: $path ($sizeStr)"
        "zh" -> "[成功] 完成: $path ($sizeStr)"
        else -> "[SUCCESS] Done: $path ($sizeStr)"
    }

    fun allCompleted(): String = when (getAppLanguage()) {
        "in" -> "[i] Semua antrean telah selesai diproses."
        "ja" -> "[i] すべてのキューの処理が完了しました。"
        "zh" -> "[i] 所有下载队列均已处理完毕。"
        else -> "[i] All download queue items processed successfully."
    }

    fun fastRemuxMp3Start(count: Int): String = when (getAppLanguage()) {
        "in" -> "[+] Fast Remux: Menggabungkan $count track MP3 secara instan..."
        "ja" -> "[+] Fast Remux: $count 個の MP3 トラックを瞬時に結合中..."
        "zh" -> "[+] Fast Remux: 正在秒级合并 $count 个 MP3 音轨..."
        else -> "[+] Fast Remux: Merging $count MP3 tracks instantly..."
    }

    fun fastRemuxMp3Success(sizeMb: Double, totalFrames: Long): String = when (getAppLanguage()) {
        "in" -> "[+] Berhasil menggabungkan MP3: %.2f MB (Fast Remux Instan, total $totalFrames frame)".format(sizeMb)
        "ja" -> "[+] MP3の結合に成功: %.2f MB (Fast Remux, 合計 $totalFrames フレーム)".format(sizeMb)
        "zh" -> "[+] MP3 合并成功: %.2f MB (Fast Remux, 共 $totalFrames 帧)".format(sizeMb)
        else -> "[+] Successfully merged MP3: %.2f MB (Instant Fast Remux, $totalFrames frames)".format(sizeMb)
    }

    fun fastRemuxMp3Fail(err: String?): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal menggabungkan file MP3: $err"
        "ja" -> "[!] MP3ファイルの結合に失敗しました: $err"
        "zh" -> "[!] 合并 MP3 文件失败: $err"
        else -> "[!] Failed to merge MP3 files: $err"
    }

    fun fastRemuxM4aFormatFail(): String = when (getAppLanguage()) {
        "in" -> "[!] Format audio M4A tidak terdeteksi."
        "ja" -> "[!] M4Aオーディオ形式が検出されませんでした。"
        "zh" -> "[!] 未检测到 M4A 音频格式。"
        else -> "[!] M4A audio format not detected."
    }

    fun fastRemuxM4aSuccess(sizeMb: Double): String = when (getAppLanguage()) {
        "in" -> "[+] Berhasil menggabungkan M4A: %.2f MB (Fast Remux Instan)".format(sizeMb)
        "ja" -> "[+] M4Aの結合に成功: %.2f MB (Fast Remux)".format(sizeMb)
        "zh" -> "[+] M4A 合并成功: %.2f MB (Fast Remux)".format(sizeMb)
        else -> "[+] Successfully merged M4A: %.2f MB (Instant Fast Remux)".format(sizeMb)
    }

    fun fastRemuxM4aFail(err: String?): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal menggabungkan M4A: $err"
        "ja" -> "[!] M4Aファイルの結合に失敗しました: $err"
        "zh" -> "[!] 合并 M4A 失败: $err"
        else -> "[!] Failed to merge M4A: $err"
    }
}
