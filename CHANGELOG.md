# Changelog: tabletizer v1.3 & OpenTabletDriver Integration

## General
- Deprecated and removed USB/IP drivers and the legacy Python desktop client in favor of a native TCP streaming protocol and an integrated device hub in OpenTabletDriver.
- Package renamed to `com.hrdcoreee.tabletizer`, version bumped to 1.3 (versionCode 4).
- Updated application icon.
- Removed legacy batch scripts, helper files, and obsolete USB/IP bindings.

## Android Client
- Forced maximum display refresh rate (90/120/144 Hz) via the display management API.
- Complete UI redesign:
  - Replaced standard Android system dialogs with a floating glassmorphic settings panel.
  - Added a HUD displaying connection status, real-time TPS counter, and active refresh rate.
  - Added connection cards with step-by-step instructions for USB and Wi-Fi modes on the idle screen.
  - Added an interactive on-screen drag and resize mode for the tablet active area.
- Two-way active area synchronization:
  - Changing aspect ratio presets (16:9, 4:3, Full) or custom area on the phone instantly updates active tablet dimensions in OpenTabletDriver (in millimeters).
  - Modifying the active area within OpenTabletDriver updates the bounding box on the phone screen in real time.
  - Implemented loop-prevention logic to prevent echo updates.
- Removed Aim Only mode: touches now consistently report true physical pressure (0-4095 levels).
- Integrated an unhandled exception handler (CrashHandler) with one-click clipboard export for diagnostics.
- Added support for custom release signing via `key.properties`.

## OpenTabletDriver
- Built a native device hub (`AndroidTabletHub`) and endpoint (`AndroidTabletEndpoint`):
  - Direct binary report stream consumption via TCP.
  - Local network auto-discovery using UDP beacons (port 3242).
- Connection priority and mode switching:
  - Enforced USB priority over Wi-Fi (automatic upgrade to USB when ADB tunnel becomes available).
  - Automated check, download, and configuration of Google Android `platform-tools` (ADB) if missing on the host PC.
- UX Integration:
  - Window title dynamically indicates connection type: `(USB)` or `(Wi-Fi: <IP address>)`.
  - Added a configuration dialog under `Tools -> tabletizer Settings...` allowing users to toggle Wi-Fi, enforce USB priority, or configure manual IP and port settings.

## Bug Fixes & Stability
- Fixed application crash on initial screen touch caused by report parser misalignment and buffer sizing.
- Resolved encoding issues (mojibake) in millimeter dimensions, delimiters, and notification text:
  - Configured Gradle and `javac` compiler tasks to strictly enforce UTF-8.
  - Replaced raw multi-byte characters with ASCII-escaped Unicode sequences in source code.
- Added keystore files (`*.jks`, `*.keystore`) and `key.properties` to `.gitignore`.
