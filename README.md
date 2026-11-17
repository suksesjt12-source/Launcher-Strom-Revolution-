# Storm Revolution Launcher Native

Standalone Android launcher built with Kotlin plus an ARM64 C++17 native module.

## Current native milestones

- Kotlin launcher UI and file picker for selecting `NSUNSR.exe`.
- ARM64 JNI library built with the Android NDK.
- Vulkan physical-device and buffer-device-address capability diagnostics.
- Android `SurfaceView` connected to a native Vulkan renderer.
- Native renderer creates an Android Vulkan surface, graphics/present queue,
  swapchain, render pass and framebuffers, then presents a diagnostic
  clear-color frame.

The Vulkan preview is a renderer bring-up test. It is **not** the Naruto game
renderer and does not draw game assets or execute game code.

See [Native engine status](docs/NATIVE_ENGINE_STATUS.md) and
[Native port path](docs/NATIVE_PORT_PATH.md).

## What is not included

This branch contains no Winlator, Wine, Box64, DXVK, or other Windows
compatibility runtime. It does not execute `NSUNSR.exe`. It also does not
contain Naruto Storm Revolution's native ARM64 game engine, generated
recompiled game code, proprietary game assets, or gameplay implementation.

A real native port needs legally usable source code and its build system, or a
substantial clean-room implementation of the platform/runtime, renderer,
asset loading, audio, input, animation and gameplay systems. A Windows x86
PE executable cannot be directly converted to a runnable Android ARM64 game
by the launcher.

## Build

Open **Actions** and run **Build Storm Revolution Native Launcher**. Download
the artifact named `storm-revolution-native-launcher-debug` after a successful
run. The workflow verifies that `lib/arm64-v8a/libstorm_engine.so` is packaged
in the APK.
