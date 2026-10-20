![tabletizer! banner](https://raw.githubusercontent.com/hrdcoreee/tabletizer/refs/heads/main/banner.png)

Turn your Android smartphone or tablet into a high-performance graphics tablet for Windows (specifically optimized for osu! and low-latency drawing).

Unlike legacy solutions, **tabletizer!** connects directly to custom **OpenTabletDriver** build over a lightweight TCP stream — **no other software and zero kernel-mode driver hassles.**

---

## Architecture

- **Android App (`app`)**: Captures touch and stylus inputs at native screen polling rates (60Hz / 90Hz / 120Hz / 240Hz+) and streams raw FMA tablet reports directly over TCP port 3240 using an asynchronous zero-allocation ring buffer.
- **OpenTabletDriver Integration**: An embedded `AndroidTabletHub` inside OpenTabletDriver automatically detects ADB, sets up port forwarding, and exposes the Android device as a native virtual tablet.

---

## Features

- **Direct OpenTabletDriver Support**: Plug in and play. OTD automatically handles connection, filters, and absolute coordinate mapping.
- **Ultra-Low Latency**: Non-blocking touch pipeline with historical batch coordinate sampling and `TCP_NODELAY`.
- **Live Diagnostics**:
  - Live **TPS (Touches Per Second)** counter and packet counter on screen.
  - Connection status chip with mode indicator (`USB (ADB)` vs `Wi-Fi`).
  - Automatic Wi-Fi IP discovery.
  - In-app crash reporter and diagnostic guide.
- **Configurable Work Area**:
  - Aspect ratio presets: `16:9`, `4:3`, `Fullscreen`.
  - Position anchors: `Center`, `Bottom Right`, `Bottom Left`, `Right Center`.
  - Interactive on-screen drag and resize mode.
  - Real-time two-way active area synchronization with OpenTabletDriver.

---

## Quick Start

### 1. Wired Connection (USB / ADB — Recommended)
1. Install client app on your phone.
2. Turn ON **USB Debugging** in Android Developer Options.
3. Connect your phone to PC via USB and accept the "Allow USB debugging" prompt.
4. Download and Launch OpenTabletDriver on PC — it connects automatically!

### 2. Wireless Connection (Wi-Fi)
1. Connect PC and phone to the same Wi-Fi network.
2. Open the tabletizer app on your phone (your local Wi-Fi IP will be displayed on screen).
3. Download and Launch OpenTabletDriver.

---

## Building from Source

### Android APK (Debug)
```bash
./gradlew.bat assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Android APK (Release)
Configure `key.properties` with your keystore credentials and run:
```bash
./gradlew.bat assembleRelease
```
The signed release APK will be generated at `app/build/outputs/apk/release/app-release.apk`.
