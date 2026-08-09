# Kocoa Beam - Android 上的 Klipper / Kalico

## 名字的由来

**Kocoa Beam** 的名字来源于可可豆——巧克力顺滑、浓郁的核心原料。正如可可豆被加工成温暖美味的巧克力一样，Kocoa Beam 也将 [Beam Klipper](https://github.com/utkabobr/BeamKlipper) 的原始能量提炼成更柔和、更甜美的体验。

"K" 代表 Kotlin 与 Klipper 的传承。"Beam" 则致敬原始的 [Beam Klipper](https://github.com/utkabobr/BeamKlipper)（由 [ProtonKicker](https://github.com/ProtonKicker) 创建）。两者结合，是一个如同热可可一般温暖亲切的名字。

Kocoa Beam 可以让你在任何支持 OTG 的 Android 5.0+ 设备上运行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) 主机软件（Klippy）。

## 为什么选择 Kocoa Beam？

Kocoa Beam 是 Beam Klipper 的全面升级，包含三大改进：

### 1. Kotlin 重写
整个应用已从 Java 迁移到 Kotlin，带来：
- **空安全** — 编译时防止 NullPointerException
- **协程** — 自动清理后台线程，无泄漏
- **不可变数据类** — 线程安全的事件总线消息和数据库实体
- **智能转换和穷举检查** — Bug 在编译时捕获，而非运行时

### 2. 体积大幅减小
Kocoa Beam 比原始 Beam Klipper 小很多（138 MB → 约 36 MB）：

| 组件 | Beam Klipper | Kocoa Beam |
|------|-------------|------------|
| FFmpeg 延时摄影 | 捆绑二进制文件（约 40 MB） | Android MediaCodec API（内置） |
| 应用大小（arm64） | 约 138 MB | 约 36 MB |

FFmpeg 延时摄影组件已被 Android 原生 MediaCodec API 取代，每个架构节省约 40 MB。

### 3. 全新的 UI
Kocoa Beam 具有完全的 UI 重新设计：
- 粗野主义 Bento 风格，「纸/蜜/墨」配色
- 硬质偏移阴影和粗边框
- 现代 Jetpack Compose 实现
- 改进的布局和可用性

### 额外功能
- **10 个并发实例** — 同时运行最多 10 个打印机配置文件（对比 Beam Klipper 的 4 个）
- **双固件支持** — 运行 Klipper 或 Kalico 固件引擎
- **原生延时摄影** — 使用 Android 硬件 MediaCodec 而非捆绑 FFmpeg
- **本地运行** — 无云端连接，所有数据留在设备上（已移除 Beam Cloud 支持）

## 选择正确的安装包

Kocoa Beam 提供两个 APK 版本：

| 架构 | 包名称 | 适用场景 |
|------|--------|----------|
| arm64 | `KocoaBeam_*_arm64.apk` | 现代 64 位设备（推荐） |
| armv7 | `KocoaBeam_*_armv7.apk` | 旧式 32 位设备 |

**如何检查设备架构：**
- 前往「设置」>「关于手机」>「架构」或「内核架构」
- 或安装 CPU 信息 App 如「CPU-Z」或「AIDA64」
- 如有疑问，先尝试 arm64 — 2015 年后发布的设备大多支持

# 快速开始

1. 从[这里](https://github.com/utkabobr/klipper/tree/prebuilt-v0.12.0)下载并安装 `firmware.bin`（或从[此仓库](https://github.com/utkabobr/klipper)自行构建以确保版本兼容）
2. 从 [Releases 页面](https://github.com/ProtonKicker/Cream/releases/latest) 安装 APK
3. 允许所有需要的权限
4. 添加打印机实例（列表中没有你的打印机时，选择 generic-***.cfg）
5. 点击启动按钮
6. 访问 Web 服务器地址 `http://IP:8888/`
7. 在 Web 编辑器的「设备」标签页中配置串口（单打印机设置下会自动配置）
8. 大功告成！

# 安装 Kocoa Beam 之后，设备还能当普通设备用吗？

**当然可以！**

Kocoa Beam 不会对 Android 系统做任何改动，它以普通 Android 应用的形式运行在用户空间。

# IP:端口是什么？

任意实例运行时，主页面都会显示该地址。

Web 服务器地址：`http://IP:8888/`

摄像头地址：
- /webcam/?action=stream => `http://IP:8889/`
- /webcam/?action=snapshot => `http://IP:8889/snapshot`

Fluidd 推荐使用 mjpeg-**stream**（非 adaptive mjpeg）摄像头配置，Mainsail 推荐 UV4L-MJPEG。

# 内置了什么？

Kocoa Beam 内置了：
- [Klipper](https://github.com/KevinOConnor/klipper)
- [Kalico](https://github.com/KalicoDTU/kalico)
- [Moonraker](https://github.com/Arksine/moonraker)
- [Fluidd](https://github.com/fluidd-core/fluidd)
- [Mainsail](https://github.com/mainsail-crew/mainsail)
- [Happy Hare](https://github.com/moggieuk/Happy-Hare)
- [Klipper TMC Autotune](https://github.com/andrewmcgr/klipper_tmc_autotune)
- [Moonraker-timelapse](https://github.com/mainsail-crew/moonraker-timelapse)

# Android 扩展功能

Kocoa Beam 提供了一些附加扩展功能，用于控制内置功能。

### 摄像头

在 printer.cfg 中加入 `[kocoa_camera]`

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - 开关闪光灯

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - 设置摄像头自动对焦状态；关闭自动对焦时可设置焦距。`FOCUS_DISTANCE` 单位为屈光度，因设备而异。

### 蜂鸣器

在 printer.cfg 中加入 `[include kocoa_beeper.cfg]`

使用[文档中定义](https://marlinfw.org/docs/gcode/M300.html)的 `M300` 宏。

# 自动启动

将需要的打印机设置为自动启动，**并将应用设为默认桌面**，即可实现开机自启。

如果设备已加密（大多数设备默认开启），你**必须**移除锁屏 PIN 码。

# 后台活动说明

部分厂商可能会限制应用的后台进程或性能。
可以将应用设为默认桌面并允许所有后台任务来规避。

# 支持 Android TV 吗？

支持，应该可以正常工作。但请注意，部分廉价电视盒子不支持直接将 Kocoa Beam 设为桌面，需要先用 ADB 或 root 禁用系统桌面。

# 用哪种 USB 集线器？

作者使用的是绿联（UGREEN）Type-C 集线器（非广告，只是在等绿联来合作 :D），只要能同时充电且与你的设备兼容，任何集线器都可以。

# 限制

- Web 服务器无法使用默认端口，因为 Android/Linux 不允许用户空间应用绑定 1024 以下的端口，而默认的 `http://IP` 需要 80 端口
- 部分设备在固件重启后会重置设备路径，这种情况下请使用 VID/PID 命名
- 不支持 SSH（也因此无法在设备上编译固件或运行额外的自启服务）
- 部分设备不支持同时 OTG 和充电，这种情况只能直接焊接到电池引脚（或者换一台设备，随你）
- 仅支持 250000 波特率（不想把这个设置转发到 Android USB 驱动，几乎所有配置都用 250000 而已）

# 构建

- 先拉取全部子模块！（使用 `git clone --recursive`，不要以压缩包形式下载）
- 用 Android Studio 导入项目并点击运行

# 贡献

欢迎提交 Pull Request！
