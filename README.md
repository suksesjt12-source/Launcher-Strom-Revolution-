# Storm Revolution Launcher — Android companion

A small Android companion app for a user-owned PC installation of *NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution*.

## What this APK does

The app can:
- Inspect a selected PC game folder without modifying it.
- Read a Winlator-exported `.desktop` shortcut from shared internal storage.
- Pass the shortcut's container ID and file path to the exported Winlator Mali game-display activity.
- Open Winlator Mali directly so the user can create a container or export a game shortcut.

The game itself is still executed by the user's installed Winlator runtime. This APK is **not** a standalone emulator and is **not** a native ARM64 recompilation of the commercial game.

## Required setup

1. Install and initialize Winlator Mali (Ludashi flavor or standard flavor) on the Android device.
2. In Winlator, create a container and add the user's own `NSUNSR.exe` as a game shortcut.
3. In the Winlator shortcut menu, choose **Export Shortcut**. The default export folder is `/storage/emulated/0/Winlator/Shortcuts`.
4. Open this companion app and choose **PILIH SHORTCUT GAME (.DESKTOP)**.
5. Select the exported `.desktop` file, then tap **MAIN KAN DI WINLATOR**.

The shortcut file must be on primary shared internal storage. The app does not read another app's private container directory.

## Compatibility and limits

- The launcher integration targets the Winlator Mali source variants using package IDs `com.ludashi.benchmark` and `com.winlator.mali`, with the exported activity `com.winlator.cmod.XServerDisplayActivity`.
- That external activity interface is version-specific and may change in later Winlator releases.
- A successful handoff only means Android started the Winlator activity. It does not guarantee the game will boot, render correctly, or reach playable performance.
- Game compatibility remains dependent on the Winlator version, Wine/Box64 build, graphics wrapper/driver, game files, and device GPU.
- The supplied `NSUNSR.exe` was identified in the initial audit as Windows PE32 / Intel x86. Windows x86 machine code cannot be directly recompiled into Android ARM64 machine code from the EXE alone.
- Do not commit or upload proprietary executables, DLLs, archives, or extracted game assets to this repository.

## Device target

Initial target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, 8 GB RAM, Android 14. A real game launch still needs to be tested on the phone.

## Build

GitHub Actions runs unit tests and creates a debug APK on pushes, pull requests, or manual workflow runs.

1. Open the repository's **Actions** tab.
2. Select **Storm Android APK Build**.
3. Open the latest successful run.
4. Download the `storm-revolution-launcher-debug` artifact.

See [docs/PORTING_REALITY.md](docs/PORTING_REALITY.md) for architecture and verification details.
