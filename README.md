![tabletizer! banner](https://raw.githubusercontent.com/hrdcoreee/tabletizer/refs/heads/main/banner.png)

Turn your Android phone or tablet into a graphics tablet for Windows — built for **osu!** and anything where low latency matters.

Connects directly to a custom **OpenTabletDriver** build over TCP. 

---

## How it works

- **Android app** — captures touch and stylus input at the screen's native polling rate (60 / 90 / 120 / 240+ Hz) and streams it to your PC over TCP.
- **OpenTabletDriver** — automatically detects the device via ADB, sets up port forwarding, and treats your phone as a real graphics tablet.

---

## Features

- **Plug & Play** — connect, launch OTD, done.
- **Ultra-low latency** — non-blocking touch pipeline, historical batch sampling.
- **Live diagnostics:**
  - TPS (touches per second) and packet counter on screen.
  - Connection status indicator: `USB (ADB)` or `Wi-Fi`.
  - Automatic Wi-Fi IP discovery.
  - Built-in crash reporter.
- **Configurable work area:**
  - Aspect ratio presets: `16:9`, `4:3`, `Fullscreen`.
  - Position anchors: `Center`, `Bottom Right`, `Bottom Left`, `Right Center`.
  - Drag and resize directly on screen.
  - Real-time two-way area sync with OpenTabletDriver.

---

## Quick Start

### Wired (USB / ADB) — recommended
1. Install the app on your phone.
2. Enable **USB Debugging** in Android Developer Options.
3. Connect your phone to the PC via USB and accept the debugging prompt.
4. Launch OpenTabletDriver on your PC — it connects automatically.

### Wireless (Wi-Fi)
1. Connect your PC and phone to the same Wi-Fi network.
2. Open tabletizer on your phone — your local IP will be shown on screen.
3. Launch OpenTabletDriver on your PC.

---

## Building from Source

### APK (Debug)
```bash
./gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### APK (Release)
Set up `key.properties` with your keystore credentials, then:
```bash
./gradlew.bat assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`
