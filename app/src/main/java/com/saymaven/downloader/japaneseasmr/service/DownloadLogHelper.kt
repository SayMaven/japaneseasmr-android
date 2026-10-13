package com.saymaven.downloader.japaneseasmr.service

import com.saymaven.downloader.japaneseasmr.JapaneseAsmrApp
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import java.util.Locale

object DownloadLogHelper {

    fun getAppLanguage(): String {
        return try {
            val pref = PreferencesManager.getFastCachedLanguage(JapaneseAsmrApp.instance)
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

    // --- Queue Management Logs ---

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

    fun notificationStarting(): String = when (getAppLanguage()) {
        "in" -> "Memulai unduhan antrean..."
        "ja" -> "ダウンロードキューを開始中..."
        "zh" -> "正在启动队列下载..."
        else -> "Starting queue download..."
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
        "ja" -> "[ERROR] $rjid の処理に失敗しました"
        "zh" -> "[ERROR] 处理 $rjid 失败"
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
        "ja" -> "[SUCCESS] 完了: $path ($sizeStr)"
        "zh" -> "[SUCCESS] 完成: $path ($sizeStr)"
        else -> "[SUCCESS] Done: $path ($sizeStr)"
    }

    fun allCompleted(): String = when (getAppLanguage()) {
        "in" -> "[i] Semua antrean telah selesai diproses."
        "ja" -> "[i] すべてのキューの処理が完了しました。"
        "zh" -> "[i] 所有下载队列均已处理完毕。"
        else -> "[i] All download queue items processed successfully."
    }

    // --- Fast Remux & Merging Logs ---

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

    fun fastRemuxM4aStart(count: Int): String = when (getAppLanguage()) {
        "in" -> "[+] Fast Remux: Menggabungkan $count track M4A..."
        "ja" -> "[+] Fast Remux: $count 個の M4A トラックを結合中..."
        "zh" -> "[+] Fast Remux: 正在合并 $count 个 M4A 音轨..."
        else -> "[+] Fast Remux: Merging $count M4A tracks..."
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

    // --- Audio Downloader Stream / Download Logs ---

    fun speedCompleted(): String = when (getAppLanguage()) {
        "in" -> "Selesai"
        "ja" -> "完了"
        "zh" -> "完成"
        else -> "Done"
    }

    fun hlsFetchingPlaylist(): String = when (getAppLanguage()) {
        "in" -> "[i] Mengambil playlist HLS (.m3u8)..."
        "ja" -> "[i] HLSプレイリスト (.m3u8) を取得中..."
        "zh" -> "[i] 正在获取 HLS 播放列表 (.m3u8)..."
        else -> "[i] Fetching HLS playlist (.m3u8)..."
    }

    fun hlsPlaylistFetchFailed(code: Int): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal mengambil playlist m3u8 (HTTP $code)"
        "ja" -> "[!] m3u8プレイリストの取得に失敗しました (HTTP $code)"
        "zh" -> "[!] 获取 m3u8 播放列表失败 (HTTP $code)"
        else -> "[!] Failed to fetch m3u8 playlist (HTTP $code)"
    }

    fun hlsNoSegmentsFound(): String = when (getAppLanguage()) {
        "in" -> "[!] Tidak ada segmen audio ditemukan di playlist m3u8."
        "ja" -> "[!] m3u8プレイリストに音声セグメントが見つかりませんでした。"
        "zh" -> "[!] m3u8 播放列表中未发现音频分段。"
        else -> "[!] No audio segments found in m3u8 playlist."
    }

    fun hlsSegmentsDetected(count: Int, concurrency: Int): String = when (getAppLanguage()) {
        "in" -> "[i] Terdeteksi $count segmen audio HLS. Memulai unduhan paralel ($concurrency koneksi)..."
        "ja" -> "[i] $count 個のHLS音声セグメントを検出。並列ダウンロードを開始 ($concurrency 接続)..."
        "zh" -> "[i] 检测到 $count 个 HLS 音频分段。开始并行下载 ($concurrency 连接)..."
        else -> "[i] Detected $count HLS audio segments. Starting parallel download ($concurrency connections)..."
    }

    fun downloadSegmentProgress(done: Int, total: Int, pct: Int, downStr: String, speedStr: String): String = when (getAppLanguage()) {
        "in" -> "  [download] Segmen $done/$total ($pct%) - $downStr @ $speedStr"
        "ja" -> "  [download] セグメント $done/$total ($pct%) - $downStr @ $speedStr"
        "zh" -> "  [download] 分段 $done/$total ($pct%) - $downStr @ $speedStr"
        else -> "  [download] Segment $done/$total ($pct%) - $downStr @ $speedStr"
    }

    fun hlsMergingSegments(count: Int): String = when (getAppLanguage()) {
        "in" -> "[i] Menggabungkan $count segmen audio secara streaming cepat..."
        "ja" -> "[i] $count 個の音声セグメントを高速ストリーミングで結合中..."
        "zh" -> "[i] 正在通过快速流式合并 $count 个音频分段..."
        else -> "[i] Merging $count audio segments via fast streaming..."
    }

    fun hlsPackagingM4a(): String = when (getAppLanguage()) {
        "in" -> "[i] Fast Remux: Mengemas audio ke container ISO M4A (100% Seekable & Indeks Durasi Presisi)..."
        "ja" -> "[i] Fast Remux: 音声をISO M4Aコンテナにパッケージ中 (高精度シーク＆正確な時間)..."
        "zh" -> "[i] Fast Remux: 正在将音频打包至 ISO M4A 容器 (100% 可精确定位与准确时长)..."
        else -> "[i] Fast Remux: Packaging audio into ISO M4A container (100% Seekable & Precise Duration)..."
    }

    fun hlsPackagingFallback(): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal mengemas ISO M4A, menyalin file stream..."
        "ja" -> "[!] ISO M4Aのパッケージに失敗、ストリームファイルをコピー中..."
        "zh" -> "[!] ISO M4A 打包失败，正在复制流文件..."
        else -> "[!] Failed to package ISO M4A, copying raw stream file..."
    }

    fun audioFileCreated(sizeStr: String): String = when (getAppLanguage()) {
        "in" -> "[SUCCESS] File audio berhasil dibuat: $sizeStr (100% Seekable & Jernih)"
        "ja" -> "[SUCCESS] 音声ファイルが正常に作成されました: $sizeStr (高音質＆シーク可能)"
        "zh" -> "[SUCCESS] 音频文件创建成功: $sizeStr (100% 可精确定位与高音质)"
        else -> "[SUCCESS] Audio file successfully created: $sizeStr (100% Seekable & Crisp)"
    }

    fun hlsDownloadFailed(err: String?): String = when (getAppLanguage()) {
        "in" -> "[ERROR] Gagal mengunduh HLS: $err"
        "ja" -> "[ERROR] HLSのダウンロードに失敗しました: $err"
        "zh" -> "[ERROR] 下载 HLS 失败: $err"
        else -> "[ERROR] Failed to download HLS: $err"
    }

    fun adtsHeaderNotFound(): String = when (getAppLanguage()) {
        "in" -> "[!] Header ADTS pertama tidak ditemukan."
        "ja" -> "[!] 最初のADTSヘッダーが見つかりませんでした。"
        "zh" -> "[!] 未找到初始 ADTS 文件头。"
        else -> "[!] Initial ADTS header not found."
    }

    fun remuxM4aProgress(pct: Int, frames: Long): String = when (getAppLanguage()) {
        "in" -> "  [remux] Fast Remux ke container M4A: $pct% ($frames frame)..."
        "ja" -> "  [remux] M4AコンテナへのFast Remux: $pct% ($frames フレーム)..."
        "zh" -> "  [remux] Fast Remux 至 M4A 容器: $pct% ($frames 帧)..."
        else -> "  [remux] Fast Remux to M4A container: $pct% ($frames frames)..."
    }

    fun muxM4aFailed(err: String?): String = when (getAppLanguage()) {
        "in" -> "[!] Gagal muxing M4A: $err"
        "ja" -> "[!] M4Aの多重化に失敗しました: $err"
        "zh" -> "[!] M4A 混流封装失败: $err"
        else -> "[!] Failed to mux M4A: $err"
    }

    fun checkingDirectFileSize(): String = when (getAppLanguage()) {
        "in" -> "[i] Memeriksa ukuran file direct audio..."
        "ja" -> "[i] 音声ファイルのサイズを確認中..."
        "zh" -> "[i] 正在检查音频文件大小..."
        else -> "[i] Checking direct audio file size..."
    }

    fun downloadingSingleStream(): String = when (getAppLanguage()) {
        "in" -> "[i] Mengunduh single-stream..."
        "ja" -> "[i] シングルストリームでダウンロード中..."
        "zh" -> "[i] 正在以单线程下载..."
        else -> "[i] Downloading single-stream..."
    }

    fun startMultiPartRange(totalStr: String, concurrency: Int): String = when (getAppLanguage()) {
        "in" -> "[i] Ukuran file: $totalStr. Memulai unduhan multi-part Range ($concurrency thread paralel)..."
        "ja" -> "[i] ファイルサイズ: $totalStr。マルチパートRange並列ダウンロードを開始 ($concurrency スレッド)..."
        "zh" -> "[i] 文件大小: $totalStr。开始多分段 Range 并行下载 ($concurrency 并行线程)..."
        else -> "[i] File size: $totalStr. Starting multi-part Range download ($concurrency parallel threads)..."
    }

    fun multiPartDownloadSuccess(sizeStr: String): String = when (getAppLanguage()) {
        "in" -> "[SUCCESS] Unduhan multi-part selesai: $sizeStr"
        "ja" -> "[SUCCESS] マルチパートダウンロードが完了しました: $sizeStr"
        "zh" -> "[SUCCESS] 多分段下载完成: $sizeStr"
        else -> "[SUCCESS] Multi-part download complete: $sizeStr"
    }

    fun multiPartDownloadFailed(err: String?): String = when (getAppLanguage()) {
        "in" -> "[ERROR] Gagal unduhan multi-part: $err"
        "ja" -> "[ERROR] マルチパートダウンロードに失敗しました: $err"
        "zh" -> "[ERROR] 多分段下载失败: $err"
        else -> "[ERROR] Multi-part download failed: $err"
    }

    fun directDownloadSuccess(sizeStr: String): String = when (getAppLanguage()) {
        "in" -> "[SUCCESS] Unduhan selesai: $sizeStr"
        "ja" -> "[SUCCESS] ダウンロード完了: $sizeStr"
        "zh" -> "[SUCCESS] 下载完成: $sizeStr"
        else -> "[SUCCESS] Download complete: $sizeStr"
    }
}
