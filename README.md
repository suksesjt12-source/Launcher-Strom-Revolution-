# Storm Revolution — Android Port Research

This repository is a **research and Android audit-app workspace** for a user-owned PC installation of *NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution*.

## Important status

The current Android app is a file-audit utility. It is **not a native Android port of the game**, does not contain the game, and does not currently launch or render gameplay.

The supplied `NSUNSR.exe` is a Windows PE32 / Intel x86 executable. An EXE, DLL collection, and data folder are not the original C/C++ source code. They cannot simply be recompiled into an Android ARM64 APK. A true native port would require source code and build dependencies, or a substantial clean-room reimplementation of the engine and platform-specific systems.

## Current Android app

The app is being built for Android ARM64 and provides:

- Android Storage Access Framework folder selection.
- Bounded, read-only scan for `NSUNSR.exe`, DLL files, `.cpk` files, and common data-folder names.
- PE header inspection to distinguish Windows PE32 x86, PE32+ x86-64, and ARM64 images.
- Device ABI and Android API display.
- A GitHub Actions pipeline that runs unit tests and builds a debug APK.

The game installation remains on the device. The app does not upload or commit proprietary game files.

## Device target

Initial test target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, 8 GB RAM, Android 14. Device compatibility still needs to be verified on the physical phone.

## Build

GitHub Actions builds the debug APK on pushes, pull requests, or manual workflow runs.

1. Open the repository's **Actions** tab.
2. Select **Storm Android APK Build**.
3. Open a successful run.
4. Download the `storm-revolution-audit-debug` artifact.

The APK is an audit-tool build, not a playable game port.

## Porting roadmap

1. Verify the full PC installation layout and all required dependencies.
2. Identify whether original engine source code or a legally usable native engine implementation is available.
3. Design and implement an Android ARM64 rendering, audio, input, and file/asset pipeline.
4. Validate graphics support and performance on the target Mali-G57 device.
5. Only claim native gameplay after a real executable build and device test.

See [PORTING_REALITY.md](docs/PORTING_REALITY.md) for the technical constraints.
