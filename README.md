# Kocoa Beam - Klipper for Android

**Read this in other languages: [English](README.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

## What's in a Name?

**Kocoa Beam** is named after cocoa beans — the smooth, rich foundation of chocolate. Like cocoa beans transformed into something warm and delightful, Kocoa Beam takes the raw energy of [Beam Klipper](https://github.com/utkabobr/BeamKlipper) and refines it into a softer, sweeter experience.

The "K" honors our Kotlin roots and Klipper heritage. The "Beam" pays tribute to the original [Beam Klipper](https://github.com/utkabobr/BeamKlipper) by [ProtonKicker](https://github.com/ProtonKicker). Together, it's a name that's as warm and approachable as a cup of hot cocoa.

Kocoa Beam allows you to run [Klipper](https://github.com/KevinOConnor/klipper) or [Kalico](https://github.com/KalicoDTU/kalico) host software on any Android 5.0+ device with OTG support.

## Why Kocoa Beam?

Kocoa Beam is a complete overhaul of Beam Klipper with three major improvements:

### 1. Kotlin Rewrite
The entire application has been migrated from Java to Kotlin, bringing:
- **Null safety** — compile-time prevention of NullPointerExceptions
- **Coroutines** — automatic cleanup of background threads, no more leaks
- **Immutable data classes** — thread-safe event bus messages and database entities
- **Smart casts & exhaustiveness checks** — bugs caught at compile time, not runtime

### 2. Dramatically Smaller Size
Kocoa Beam is significantly smaller than the original Beam Klipper (138 MB → ~36 MB):

| Component | Beam Klipper | Kocoa Beam |
|-----------|-------------|------------|
| FFmpeg timelapse | Bundled binary (~40 MB) | Android MediaCodec API (built-in) |
| App size (arm64) | ~138 MB | ~36 MB |

The FFmpeg timelapse component was replaced with Android's native MediaCodec API, saving ~40 MB per architecture.

### 3. Brand New UI
Kocoa Beam features a complete UI redesign with:
- Brutalist bento-box aesthetic with "Paper/Honey/Ink" color palette
- Hard offset shadows and bold borders
- Modern Jetpack Compose implementation
- Improved layout and usability

### Additional Features
- **10 concurrent instances** — run up to 10 printer profiles simultaneously (vs. 4 in Beam Klipper)
- **Dual firmware support** — run Klipper or Kalico firmware engines
- **Native timelapse** — uses Android's hardware MediaCodec instead of bundled FFmpeg
- **Local-only operation** — no cloud connectivity; all data stays on your device (Beam Cloud support removed)

## Choosing the Right Package

Kocoa Beam provides two APK variants:

| Architecture | Package Name | Use Case |
|-------------|--------------|----------|
| arm64 | `KocoaBeam_*_arm64.apk` | Modern 64-bit devices (recommended) |
| armv7 | `KocoaBeam_*_armv7.apk` | Older 32-bit devices |

**How to check your device architecture:**
- **Settings > About Phone > Architecture** or **Kernel Architecture**
- Or install a CPU info app like "CPU-Z" or "AIDA64"
- If unsure, try arm64 first — most devices released after 2015 support it

# Quick Start

1. Download & install firmware.bin from [here](https://github.com/utkabobr/klipper/tree/prebuilt-v0.12.0) (or build your own from [this repo](https://github.com/utkabobr/klipper) to ensure versions compatibility)
2. Install APK from [Releases tab](https://github.com/ProtonKicker/Cream/releases/latest)
3. Allow all the permissions required
4. Add printer instance (Click generic-***.cfg if your printer is not available)
5. Click start
6. Go to web server's url `http://IP:8888/`
7. Configure serial port from "Devices" tab in web editor (1.0.1+ configures automatically if you use single printer setup)
8. You're awesome!

# Can I use device as regular after I install Kocoa Beam to it?

**Yes!** You definitely can!

Kocoa Beam does not do **anything** to your Android system, it runs in user-space as a regular Android app

# What's IP:port?

It's displayed on main page when any of the instances are running.

Web server URL is: `http://IP:8888/`

Camera URL's are:
- /webcam/?action=stream => `http://IP:8889/`
- /webcam/?action=snapshot => `http://IP:8889/snapshot`

Recommended camera config is mjpeg-**stream** (Not adaptive mjpeg) for Fluidd and UV4L-MJPEG for Mainsail

# What's inside?

Kocoa Beam bundles:
- [Klipper](https://github.com/KevinOConnor/klipper)
- [Kalico](https://github.com/KalicoDTU/kalico)
- [Moonraker](https://github.com/Arksine/moonraker)
- [Fluidd](https://github.com/fluidd-core/fluidd)
- [Mainsail](https://github.com/mainsail-crew/mainsail)
- [Happy Hare](https://github.com/moggieuk/Happy-Hare)
- [Klipper TMC Autotune](https://github.com/andrewmcgr/klipper_tmc_autotune)
- [Moonraker-timelapse](https://github.com/mainsail-crew/moonraker-timelapse)

# Android Extensions

Kocoa Beam provides additional extensions to control some built-in features.

### Camera

Include `[kocoa_camera]` into your printer.cfg

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - Toggles flashlight

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - Sets camera autofocus state and focus distance if autofocus is disabled. `FOCUS_DISTANCE` is expressed in dioptres, it may vary from device to device

### Beeper

Include `[include kocoa_beeper.cfg]` into your printer.cfg

Use `M300` macro [as defined in docs](https://marlinfw.org/docs/gcode/M300.html)

# Autostart

You can put the app to autostart by setting needed printers to autostart **AND** setting app as default launcher.

You **must** remove lockscreen pincode if your device is encrypted (Enabled by default on most devices)

# Background Activity Notice

Some manufacturers may restrict app's performance or background process.
You can circumvent this by setting app as default launcher and allowing all the background tasks

# Android TV Support?

Yup. Should be working just fine. But please note that some cheap TV boxes does not support setting Kocoa Beam as launcher without disabling system one first, use ADB or root to disable it.

# What USB Hub to Use?

I'm using UGREEN Type-c hub (Not affiliated, but I'm waiting for your request UGREEN :D), but any should be fine if it works with your device and provides charging at the same time

# Restrictions

- Web server can't run on default port because Android/linux doesn't allow user-space apps to bind to ports less than 1024 and we want 80 for default `http://IP`
- Some devices may reset device path on firmware restart, you should use VID/PID naming in that case
- No SSH (You won't be able to build firmware or run additional autorun services anyway)
- Some devices doesn't support OTG and charging at the same time, you must solder directly to the battery pins in that case (Or use different device, it's up to you)
- Only 250000 baud rate is supported (I don't want to forward this setting into Android USB driver, almost all configurations use 250000 anyway)

# Building

- Fetch all of the submodules first! (`git clone --recursive`, do NOT download project as archive)
- Import project into Android Studio & click run

# Contributing

Pull requests are welcome!
