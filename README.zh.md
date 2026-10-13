<div align="center">
  <img src="assets/icon.png" width="128" height="128" alt="JapaneseASMR Downloader 图标" style="border-radius: 28px;" />
  <h1>JapaneseASMR Downloader</h1>
  <p><b>一款用于下载、管理和播放日本 ASMR 声音作品的高性能原生 Android 应用程序。</b></p>

  <p>
    <a href="README.md">English</a> |
    <a href="README.id.md">Bahasa Indonesia</a> |
    <a href="README.zh.md">简体中文</a>
  </p>

  <p>
    <a href="https://github.com/SayMaven/japaneseasmr-android/releases/latest"><img src="https://img.shields.io/badge/版本-v1.3.1-blue?style=for-the-badge" alt="版本 v1.3.1" /></a>
    <a href="https://github.com/SayMaven/japaneseasmr-android/blob/main/LICENSE"><img src="https://img.shields.io/badge/许可证-GPL%20v3.0-10b981?style=for-the-badge" alt="GPL v3.0 许可证" /></a>
    <img src="https://img.shields.io/badge/平台-Android%208.0%2B-059669?style=for-the-badge" alt="平台" />
    <img src="https://img.shields.io/badge/开发语言-Kotlin-7c3aed?style=for-the-badge" alt="开发语言" />
    <img src="https://img.shields.io/badge/UI框架-Jetpack%20Compose-1e293b?style=for-the-badge" alt="Jetpack Compose" />
  </p>

  <p>
    <a href="#v131-更新内容">更新日志</a> •
    <a href="#功能特性">功能特性</a> •
    <a href="#应用截图">应用截图</a> •
    <a href="#下载安装">下载安装</a> •
    <a href="#源码构建">源码构建</a> •
    <a href="#架构设计与技术栈">架构设计</a> •
    <a href="#开源协议">开源协议</a>
  </p>
</div>

---

## 概述

**JapaneseASMR Downloader** 是一款专为下载、归档和聆听日本 ASMR 同人音声广播剧（RJ 作品）而设计的全功能开源 Android 客户端。应用完全基于现代 Android 技术栈构建，采用 Jetpack Compose、Kotlin 协程、Room 数据库和 AndroidX Media3 (ExoPlayer)，在移动设备上带来桌面级的流式传输性能、零 OOM（内存溢出）安全保障以及稳定省电的后台音频播放体验。

---

## v1.3.1 更新内容

* **全方位多语言界面原生本地化（UI Localization）**：现已原生支持 4 种语言：**English（英语）**、**Bahasa Indonesia（印尼语）**、**日本語（日语）** 及 **简体中文（Simplified Chinese）**。
* **跟随系统语言自动切换**：基于 Android 原生资源限定符机制，应用安装后即可自动识别设备系统语言并无缝展示对应本地化文本。
* **应用内独立语言切换器**：在“设置”中新增专属语言选择菜单，允许用户独立于系统全局语言自由指定应用显示语言。
* **Android 13+ 单应用语言偏好设置适配**：深度集成 `locales_config.xml`，完美支持 Android 13 及以上系统的单应用独立语言设置面板。

---

## v1.3.0 更新内容

* **语境感知型 ASMR 翻译与自然化引擎**：
  * 双层翻译架构：优先使用 Google Web Translate 翻译，并在异常时自动无缝降级切换至 MyMemory Translation API。
  * 专为 DLsite 伏字审查去混淆优化，在翻译前自动还原标题中的遮蔽符号（`〇`、`○`、`×`、`*` 等）。
  * 针对 ASMR 领域的专业词库与语境自然化，基于 55 部真实 DLsite 作品及 11 轮测试阶梯（约 150 种常见场景）实测微调，涵盖亲密对话、角色扮演情节、拟声拟态词及音声对白。
  * 在播放器界面与下载队列卡片中提供双语同步对照显示。
* **持久化 SQLite Room 缓存引擎（数据库架构 v4）**：
  * 双层缓存流水线：线程安全的内存缓存（`ConcurrentHashMap`，0ms）结合本地 Room SQLite 持久化存储（`translation_cache` 数据表，查找耗时 <2ms）。
  * 高性能批量 SQL `IN` 查询拉取（`getTranslationsBatch`），彻底消除数据库查询抖动与跨应用启动的重复网络请求。
  * 自动化数据库架构无损迁移（`MIGRATION_3_4`）。
* **智能混合章节解析（Smart Hybrid Chapter Mapping）**：
  * 多层级叙事音轨自动分类与时间轴排序：Prologue（序章）、Main Story（主线正片）、Epilogue（尾声）、Bonus/After（特典/后日谈）及 Free Talk/Cast Comments（声优闲聊/访谈）。
  * 全面引入 Unicode NFKC 正规化，精确解析圆圈数字、全角数字及汉字音轨编号。
* **极速无损音频重封装引擎（Fast Lossless Audio Remux）**：
  * 零拷贝内核级音频合并器（基于 `FileChannel.transferTo`），支持分段 MP3 秒级无损拼接，以及分段 AAC/M4A 顺序容器重封装。
  * 音频文件瞬间合并（耗时通常小于 1 秒），完全避免高 CPU 消耗的重新编码转码，保持 100% 原始位流音质与快速跳转定位能力。
* **统一章节识别与 CUE 轨道单导出**：
  * 自动化章节元数据提取流水线，智能解析内嵌 ID3v2 `CHAP`/`CTOC` 帧、同级本地 `.cue` 伴随文件及 DLsite 远程作品目录。
  * 可选的 CUE Sheet 轨道单导出开关，在音频同目录下生成 `.cue` 文件，方便搭配发烧级桌面播放器与 DAP 播放。
* **全平台链接与番号解析器**：
  * 直接识别并自动解析粘贴的原始 URL（包括 DLsite、asmr.one 及 JapaneseASMR 重定向链接），快速提取 RJ、VJ 及 BJ 作品番号。
* **120 FPS 高刷滑动流畅度优化**：
  * 消除播放列表拖拽排序过程中的所有主线程磁盘 I/O 与 IPC 调用。
  * 预编译正则表达式、优化 Coil 内存缓存并消除多手势嵌套滑动卡顿。
* **播放器与下载队列 UI 精致重构**：
  * 全新设计的播放器工具栏，集成翻译快捷切换按钮、当前生效语言指示器以及紧凑章节信息胶囊。
  * 自适应作品封面尺寸限制（最大高度约束 `260.dp`），适配不同长宽比手机屏幕。
  * 双语下载卡片，具备实时传输遥测数据监控及优化后的空闲状态展示。

---

## 应用截图

<div align="center">
  <img src="assets/screenshots/1.jpg" width="32%" alt="截图 1" />
  <img src="assets/screenshots/2.jpg" width="32%" alt="截图 2" />
  <img src="assets/screenshots/3.jpg" width="32%" alt="截图 3" />
  <br />
  <img src="assets/screenshots/4.jpg" width="32%" alt="截图 4" />
  <img src="assets/screenshots/5.jpg" width="32%" alt="截图 5" />
  <img src="assets/screenshots/6.jpg" width="32%" alt="截图 6" />
  <br />
  <img src="assets/screenshots/7.jpg" width="32%" alt="截图 7" />
  <img src="assets/screenshots/8.jpg" width="32%" alt="截图 8" />
  <img src="assets/screenshots/9.jpg" width="32%" alt="截图 9" />
</div>

---

## 功能特性

### 语境感知型 ASMR 翻译与 Room 缓存引擎
* **领域定制化翻译**：将日文作品标题、音轨名称与章节准确翻译为流畅的目标语言，针对同人声音剧特征词汇进行专门润色。
* **伏字智能去混淆**：在发起翻译请求前自动识别并还原遮蔽字符，避免机器翻译产生断句歧义。
* **双引擎容灾机制**：Google Web Translate 优先处理，失败时自动静默切换至备用 MyMemory API。
* **双层持久缓存**：以高速内存 `ConcurrentHashMap`（0ms）搭配 Room 数据库 v4（`translation_cache` 数据表，<2ms），实现冷启动免重复联网。
* **批量 SQL 聚合获取**：单次批量 SQL `IN` 查询即可拉取全部歌单翻译，降低 CPU 与电池开销。
* **实时双语字幕**：原生播放器与下载卡片均支持日汉双语实时对照。

### 极速无损音频重封装与章节智能解析引擎
* **零拷贝内核音频合并**：针对多段分卷 MP3 文件，利用操作系统内核 `FileChannel.transferTo` 进行无损极速拼接，耗时不足 1 秒。
* **顺序 M4A 重封装**：将分段 AAC 音频重构为规范的 ISO M4A 容器，保证标准播放器下的精准定位跳转。
* **智能混合章节层级划分**：全自动将音轨按情节分类为序章、主线、尾声、特典及访谈等，按正确时序排布。
* **Unicode NFKC 标准化**：统一规范带圈数字、全角数字和汉字序号，彻底规避音轨乱序。
* **全方位章节探测**：自动识别内嵌 ID3v2 章节标签、外部 CUE 轨道表及 DLsite 远端元数据。
* **CUE 伴随文件导出**：可在设置中启用外部 `.cue` 伴随文件导出功能，无缝契合高端便携播放器（DAP）。
* **全平台链接提取**：支持粘贴 DLsite、asmr.one 及 JapaneseASMR 等网页链接自动解析提取番号。

### 高速多线程下载与流解复用引擎
* **并发多线程分段加速**：支持 4 至 32 条并发连接（默认 16 线程），充分跑满设备带宽。
* **动态连续排队下载**：下载过程中随时追加新任务，队列自动按序处理直至全部完成，无需手动干预。
* **零内存磁盘流写入**：直接写入闪存存储，哪怕下载数 GB 的超长音频也绝不会发生 `OutOfMemoryError`。
* **标准音频容器封装**：输出标准的 ISO M4A / AAC 与 MP3 文件，杜绝第三方播放器出现不可拖动进度条的问题。
* **智能存储去重验证**：自动校验下载目录中已存在的物理文件，跳过冗余重复下载。
* **实时传输控制台**：内置实时传输日志、当前速度测算与预计剩余时间（ETA），支持浮动按钮（`>_` / `</>`）一键折叠或展开。

### 现代化内置原生音频播放器
* **完整 Android MediaSession 集成**：前台服务常驻，支持锁屏高清大图展示、媒体通知控制栏与耳机线控。
* **智能睡眠定时器**：提供常用预设倒计时（5、10、15、30、60、120 分钟）并支持 +/- 5 分钟微调。暂停播放时倒计时自动随之暂停。
* **全局悬浮 Mini Player**：在“下载”、“历史”、“设置”等主界面底部常驻悬浮微型播放条，可快捷控制播放与一键向上展开全屏播放器。
* **USB DAC 硬件独占（Bit-Perfect 独占输出）**：支持直接接管 USB Audio Class（UAC1 / UAC2）硬件接口，绕过 Android 系统音频混音与采样率重采样，提供纯净声音输出。
* **DAC 硬件音量控制与实体音量条**：通过 USB 控制传输指令（`SET_CUR` / `GET_RANGE`）直控 DAC 耳放硬件音量，拦截实体音量键，并提供超薄悬浮实体音量状态条。
* **章节抽屉与进度同步**：点按章节即可无缝跳转对应时段，章节高亮状态与音轨进度条实时联动。
* **0ms 即时响应播放列表抽屉**：经过预缓存的响应式歌单，支持平滑拖拽重排与快速打开。
* **连续倍速调节弹窗**：0.25x 至 2.0x 无级微调滑动条，搭配清晰的 2x3 常用倍速快捷预设网格。
* **快退 10 秒 / 快进 10 秒**：方便随时重听关键对白与台词。
* **时间显示模式记忆**：点击可在“已播放时间”与“剩余时间（-mm:ss）”之间自由切换，重启后依然保存偏好。
* **严苛的物理文件有效性校验**：自动隐藏已被外部文件管理器删除的失效音轨，并在文件恢复时即时自动刷新。

### 声音库与元数据管理
* **120 FPS 高性能历史播放库**：零主线程磁盘 I/O 阻塞，采用轻量 UI 模型与稳定键值，确保列表以 120 FPS 满帧丝滑滚动。
* **实时本地存储同步（StorageSyncManager）**：启动及从后台切回时自动核对物理存储，让新增、移动或删除的音频与本地 Room 数据库实时保持一致。
* **多维度智能排序**：支持按收录时间（最新 / 最旧，毫秒级绝对时间戳）及标题（A-Z / Z-A）瞬间重排。
* **统一日期显示格式**：全应用统一遵循清晰明了的 `dd MMM yyyy` 日期展示。
* **完整元数据标签写入**：将高分辨率封面插图、声优（CV）、社团/作者、作品标题与标签类别直接内嵌写入音频文件（ID3v2 / MP4 atom）。
* **快速检索与离线库筛选**：支持输入 RJ 号、标题关键字、声优或社团名即时筛选离线已下载作品。

### 现代化 Material Design 3 界面与导航
* **4 大独立导航页面**：播放器（主页）、下载队列、历史库以及应用设置。
* **智能页面记忆**：冷启动时固定展示主页播放器；在后台切换任务时则精准保持当前所在页面。
* **Material You 动态取色（Monet 引擎）**：支持提取 Android 12+ 系统壁纸的主色调生成个性化主题。
* **36 款精美预设调色板**：提供丰富的自选主题色彩方案，附带三段圆环色彩预览与平滑滚动的轮播选择器。
* **0ms 启动快速缓存**：同步读取主题与语言偏好，彻底消除冷启动瞬间的屏幕闪白或主题闪烁。
* **0ms 瞬间无缝切页**：底层采用常驻 GPU RenderNode 渲染层架构，页面切换毫无卡顿与空白。
* **系统、浅色与深色模式**：完整支持白天/黑夜主题自适应及平滑切换。

---

## 架构设计与技术栈

JapaneseASMR Downloader 严格遵照现代 Android 架构指南（Modern Android Architecture），秉持关注点分离与单向数据流原则：

| 架构层级 | 核心组件 | 职责与技术说明 |
| :--- | :--- | :--- |
| **UI 表现层** | Jetpack Compose + Material 3 | 声明式组件、平滑微动效、Material You 动态主题 |
| **状态管理** | Kotlin Coroutines + StateFlow | 响应式、生命周期感知型单向数据流（UDF） |
| **媒体播放引擎** | AndroidX Media3 (ExoPlayer) | 原生硬件解码、MediaSession 媒体会话、前台播放服务 |
| **本地持久化** | Room Database v4 (SQLite) | 历史作品归档、持久化翻译缓存（`translation_cache`）、内置章节索引 |
| **音频重封装** | FastRemuxEngine | 零拷贝内核级 MP3 拼接、顺序 M4A 封装重构、CUE 轨道单输出 |
| **翻译引擎** | TranslationService | Google Web / MyMemory 双接口容灾、ASMR 语境自然化、伏字去混淆 |
| **配置存储** | AndroidX DataStore + SharedPreferences | 类型安全偏好存储，辅以冷启动 0ms 同步高速缓存 |
| **网络通信** | OkHttp 4 | HTTP/2 连接池复用、多线程分块并发下载 |
| **音频标签写入** | Jaudiotagger | ID3v2 标签（`CHAP`/`CTOC`）、MP4 atom 及元数据封装 |
| **图像加载** | Coil Compose | 异步图片流式解码、内存与硬件位图缓存 |

---

## 下载安装

您可以在 GitHub Releases 页面下载由官方密钥签名的最新通用正式版 APK 安装包：

[**前往下载最新正式版（GitHub Releases）**](https://github.com/SayMaven/japaneseasmr-android/releases/latest)

---

## 源码构建

### 环境要求
* JDK 17 或更高版本
* 配置好 Build Tools 35.0.0+ 的 Android SDK
* Gradle 8.7+（项目已通过 Gradle Wrapper 包含）

### 克隆仓库与编译
```bash
# 克隆仓库
git clone https://github.com/SayMaven/japaneseasmr-android.git
cd japaneseasmr-android

# 编译 Debug APK
./gradlew assembleDebug
```

编译生成的 APK 文件将位于：
```
app/build/outputs/apk/debug/JapaneseASMR-v1.3.1-debug.apk
```

---

## 免责声明

本软件仅供个人学习、研究及合法备份归档使用。开发者不提供任何受版权保护的音视频文件存储、分发或推广服务。用户在使用本应用时，需自行承担一切法律责任，并确保符合所在国家或地区的著作权法律法规。

---

## 开源协议

本项目采用 [GNU 通用公共许可证 v3.0 (GPL-3.0)](LICENSE) 开源。
