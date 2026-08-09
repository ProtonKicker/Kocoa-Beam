# Kocoa Beam - Android 上的 Klipper / Kalico

## 名字的由來

**Kocoa Beam** 的名字来源于可可豆——巧克力順滑、濃郁的核心原料。正如可可豆被加工成溫暖美味的巧克力一樣，Kocoa Beam 也將 [Beam Klipper](https://github.com/utkabobr/BeamKlipper) 的原始能量提煉成更柔和、更甜美的體驗。

"K" 代表 Kotlin 與 Klipper 的傳承。"Beam" 則致敬原始的 [Beam Klipper](https://github.com/utkabobr/BeamKlipper)（由 [ProtonKicker](https://github.com/ProtonKicker) 創建）。兩者結合，是一個如同熱可可一般溫暖親切的名字。

Kocoa Beam 可以讓你在任何支援 OTG 的 Android 5.0+ 裝置上執行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) 主機軟體（Klippy）。

## 為什麼選擇 Kocoa Beam？

Kocoa Beam 是 Beam Klipper 的全面升級，包含三大改進：

### 1. Kotlin 重寫
整個應用已從 Java 遷移到 Kotlin，帶來：
- **空安全** — 編譯時防止 NullPointerException
- **協程** — 自動清理後台線程，無洩漏
- **不可變資料類別** — 線程安全的事件匯流排訊息和資料庫實體
- **智慧轉換和窮舉檢查** — Bug 在編譯時捕獲，而非執行時

### 2. 體積大幅減小
Kocoa Beam 比原始 Beam Klipper 小很多（138 MB → 約 36 MB）：

| 組件 | Beam Klipper | Kocoa Beam |
|------|-------------|------------|
| FFmpeg 延時攝影 | 捆綁二進位檔案（約 40 MB） | Android MediaCodec API（內建） |
| 應用大小（arm64） | 約 138 MB | 約 36 MB |

FFmpeg 延時攝影組件已被 Android 原生 MediaCodec API 取代，每個架構節省約 40 MB。

### 3. 全新的 UI
Kocoa Beam 具有完全的 UI 重新設計：
- 野獸主義 Bento 風格，「紙/蜜/墨」配色
- 硬質偏移陰影和粗邊框
- 現代 Jetpack Compose 實現
- 改進的版面配置和可用性

### 額外功能
- **10 個並發執行個體** — 同時執行最多 10 個印表機設定檔（對比 Beam Klipper 的 4 個）
- **雙固件支援** — 執行 Klipper 或 Kalico 固件引擎
- **原生延時攝影** — 使用 Android 硬體 MediaCodec 而非捆綁 FFmpeg
- **本地運行** — 無雲端連接，所有資料留在設備上（已移除 Beam Cloud 支援）

## 選擇正確的安裝包

Kocoa Beam 提供兩個 APK 版本：

| 架構 | 套件名稱 | 適用場景 |
|------|----------|----------|
| arm64 | `KocoaBeam_*_arm64.apk` | 現代 64 位元設備（推薦） |
| armv7 | `KocoaBeam_*_armv7.apk` | 舊式 32 位元設備 |

**如何檢查設備架構：**
- 前往「設定」>「關於手機」>「架構」或「核心架構」
- 或安裝 CPU 資訊 App 如「CPU-Z」或「AIDA64」
- 如有疑問，先嘗試 arm64 — 2015 年後發布的設備大多支援

# 快速入門

1. 從[這裡](https://github.com/utkabobr/klipper/tree/prebuilt-v0.12.0)下載並安裝 `firmware.bin`（或從[此儲存庫](https://github.com/utkabobr/klipper)自行建置以確保版本相容）
2. 從 [Releases 頁面](https://github.com/ProtonKicker/Cream/releases/latest) 安裝 APK
3. 允許所有需要的權限
4. 新增印表機執行個體（清單中沒有你的印表機時，選擇 generic-***.cfg）
5. 點擊啟動按鈕
6. 存取 Web 伺服器位址 `http://IP:8888/`
7. 在 Web 編輯器的「裝置」分頁中設定序列埠（單一印表機設定下會自動設定）
8. 大功告成！

# 安裝 Kocoa Beam 之後，裝置還能當一般裝置用嗎？

**當然可以！**

Kocoa Beam 不會對 Android 系統做任何更動，它以一般 Android 應用程式的形式執行在使用者空間。

# IP:連接埠是什麼？

任何執行個體執行時，主頁面都會顯示該位址。

Web 伺服器位址：`http://IP:8888/`

相機位址：
- /webcam/?action=stream => `http://IP:8889/`
- /webcam/?action=snapshot => `http://IP:8889/snapshot`

Fluidd 建議使用 mjpeg-**stream**（非 adaptive mjpeg）相機設定，Mainsail 建議使用 UV4L-MJPEG。

# 內建了什麼？

Kocoa Beam 內建了：
- [Klipper](https://github.com/KevinOConnor/klipper)
- [Kalico](https://github.com/KalicoDTU/kalico)
- [Moonraker](https://github.com/Arksine/moonraker)
- [Fluidd](https://github.com/fluidd-core/fluidd)
- [Mainsail](https://github.com/mainsail-crew/mainsail)
- [Happy Hare](https://github.com/moggieuk/Happy-Hare)
- [Klipper TMC Autotune](https://github.com/andrewmcgr/klipper_tmc_autotune)
- [Moonraker-timelapse](https://github.com/mainsail-crew/moonraker-timelapse)

# Android 擴充功能

Kocoa Beam 提供了一些附加擴充功能，用於控制內建功能。

### 相機

在 printer.cfg 中加入 `[kocoa_camera]`

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - 開關閃光燈

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - 設定相機自動對焦狀態；關閉自動對焦時可設定焦距。`FOCUS_DISTANCE` 單位為屈光度，因裝置而異。

### 蜂鳴器

在 printer.cfg 中加入 `[include kocoa_beeper.cfg]`

使用[文件中定義](https://marlinfw.org/docs/gcode/M300.html)的 `M300` 巨集。

# 自動啟動

將需要的印表機設定為自動啟動，**並將應用程式設為預設桌面**，即可實現開機自啟。

如果裝置已加密（大多數裝置預設開啟），你**必須**移除鎖定畫面 PIN 碼。

# 背景活動說明

部分廠商可能會限制應用程式的背景程序或效能。
可以將應用程式設為預設桌面並允許所有背景工作來規避。

# 支援 Android TV 嗎？

支援，應該可以正常運作。但請注意，部分廉價電視盒不支援直接將 Kocoa Beam 設為桌面，需要先用 ADB 或 root 停用系統桌面。

# 用哪種 USB 集線器？

作者使用的是綠聯（UGREEN）Type-C 集線器（非廣告，只是在等綠聯來合作 :D），只要能同時充電且與你的裝置相容，任何集線器都可以。

# 限制

- Web 伺服器無法使用預設連接埠，因為 Android/Linux 不允許使用者空間應用程式繫結 1024 以下的連接埠，而預設的 `http://IP` 需要 80 連接埠
- 部分裝置在韌體重新啟動後會重設裝置路徑，這種情況下請使用 VID/PID 命名
- 不支援 SSH（也因此無法在裝置上編譯韌體或執行額外的自啟服務）
- 部分裝置不支援同時 OTG 和充電，這種情況只能直接焊接到電池引腳（或者換一台裝置，隨你）
- 僅支援 250000 鮑率（不想把這個設定轉送到 Android USB 驅動程式，幾乎所有設定都用 250000 而已）

# 建置

- 先拉取全部子模組！（使用 `git clone --recursive`，不要以壓縮檔形式下載）
- 用 Android Studio 匯入專案並點擊執行

# 貢獻

歡迎提交 Pull Request！
