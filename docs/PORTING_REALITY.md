# Standalone Android runtime: scope and verification

## Architecture selected

The requested behavior is one Android app that starts the game without opening a separately installed Winlator app. The current approach is to build a customized Winlator Mali application as the Storm Revolution Launcher APK.

The app includes its own Wine/Box64 compatibility runtime and graphics/audio environment. Android starts the Storm app itself; the game process is hosted inside that app's runtime. No external Winlator package handoff is used.

## Why this is not a native game port

The submitted `NSUNSR.exe` was identified in the earlier file audit as Windows PE32 / Intel x86. A compiled Windows executable and its DLL/data files are not original C/C++ source code. The Android NDK cannot directly turn this binary into a native ARM64 Android game.

A true native port would still require original source and build dependencies, or a substantial independent reimplementation of the game engine and platform layer. The embedded-runtime route is the practical single-APK option from the available binary.

## Build implementation

The GitHub Actions workflow fetches the MIT-licensed Winlator Mali source branch `bionic-mali-1.2` at pinned commit `0411a4b602ed00d71bef52e6a7ccb82a8e709b1a`, changes the Ludashi flavor's application ID and label, and builds the ARM64 Mali APK.

The workflow build demonstrates that the combined Android application can be packaged. It does not prove that the target game boots or renders correctly on the Infinix Helio G100 / Mali-G57 MC2 device.

## Current user-facing requirements

The first integrated build still uses the Winlator Mali container and shortcut workflow inside the Storm-branded app. The game itself is not bundled. The user must:
- copy their own PC game files to Android storage;
- create a container in the Storm app;
- add `NSUNSR.exe` as a shortcut;
- launch the shortcut inside the same app.

Direct first-run game-folder selection and automatic container/shortcut generation are not implemented or validated yet.

## Remaining validation

- Confirm GitHub Actions can build and package the pinned upstream runtime.
- Install the APK on an ARM64 Mali device.
- Complete first-run runtime extraction and create a clean container.
- Add the user's own game files and shortcut.
- Test boot, graphics, audio, touch controls, stability, and thermal behavior on the target phone.

Only a successful real-device game test can establish that Naruto Storm Revolution is playable through this standalone build.
