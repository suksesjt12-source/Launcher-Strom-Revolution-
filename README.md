# Storm Revolution Launcher — Storm Engine Core

Standalone Android launcher with an in-progress native C++17 / ARM64 engine core.

## Engine Core v0.5.0

- Kotlin launcher and JNI bridge.
- Native Android Vulkan surface and swapchain presentation.
- Fixed-step 60 Hz simulation loop with bounded catch-up.
- Scene/entity state with a sandbox actor transform.
- Multi-touch input: left-side virtual analog stick; upper-right jump; lower-right attack.
- App-private binary asset cache with relative-path validation and a 128 MiB memory limit.
- GitHub Actions builds the APK and verifies the native ARM64 library is packaged.

The sandbox actor is updated by the simulation, but is **not yet drawn** as a 3D model. The Vulkan renderer still presents a diagnostic clear pass only.

## Not included

This project contains no Winlator, Wine, Box64, DXVK, or other Windows compatibility runtime. It does not execute `NSUNSR.exe`.

It is not the original Naruto Storm Revolution engine. No proprietary gameplay code, game asset readers, character rigs, animations, sounds, or generated game recompilation output are included. A native port still requires legally usable source code or a clean-room implementation and legally usable game data. The Windows PE32 executable cannot be directly converted into a runnable Android ARM64 game by this launcher.

## Build

Open **Actions** and run **Build Storm Revolution Native Launcher**. After a successful run, download the artifact named `storm-revolution-native-launcher-debug`.
