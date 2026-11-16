# Storm Revolution Launcher — Android companion

A small Android companion app for a user-owned PC installation of *NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution*.

## What this APK does

The app can:
- Inspect a selected PC game folder without modifying it.
- Select the actual `NSUNSR.exe` from shared internal storage, or read a Winlator-exported `.desktop` shortcut.
- Pass the selected file path and container ID to Winlator Mali's exported game-display activity.
- Open Winlator Mali directly so the user can configure a container.

The game itself is still executed by the user's installed Winlator runtime. This APK is **not** a standalone emulator and is **not** a native ARM64 recompilation of the commercial game.

## Required setup

1. Install and initialize Winlator Mali (Ludashi flavor or standard flavor) on the Android device.
2. In Winlator, create a container and make sure the container can access the folder that stores the user's own game files.
3. In this companion app, choose `NSUNSR.exe` from shared internal storage, or choose an exported `.desktop` shortcut.
4. Enter the ID of the Winlator container that should run the game. If the selected desktop file includes `container_id:`, the app fills this automatically.
5. Tap **MAINKAN DI WINLATOR**.

The Winlator container ID is distinct from the game's filename. For a container still using Winlator's default name (for example, `Container-1`), the trailing number is its ID. If the container was renamed, use the numeric ID that was assigned when it was created.

## Compatibility and limits

- The launcher integration targets Winlator Mali source variants using package IDs `com.ludashi.benchmark` and `com.winlator.mali`, with exported activity `com.winlator.cmod.XServerDisplayActivity`.
- That external activity interface is version-specific and may change in future Winlator releases.
- A successful handoff only means Android started the Winlator activity. It does not guarantee the game will boot, render correctly, or reach playable performance.
- Game compatibility depends on the Winlator version, Wine/Box64 build, graphics wrapper/driver, game files, and device GPU.
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
