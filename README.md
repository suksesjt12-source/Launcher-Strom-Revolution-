# Storm Revolution Launcher Native

Standalone Android launcher prototype built with Kotlin and Android SDK views.

## Included
- Custom dark/orange launcher screen
- Game library with Android document picker for selecting NSUNSR.exe
- Settings and About screens
- Main button explains the native-engine limitation
- GitHub Actions workflow builds a debug APK

## Native Vulkan bootstrap

The Android app now builds an ARM64 C++ JNI module with the Android NDK. It
initializes Vulkan and lists the detected GPU devices in the launcher. This is
a native graphics capability probe and a starting point for future renderer
work; it is **not** the complete Naruto Storm Revolution game engine and does
not render or run the game.

See [NATIVE_ENGINE_STATUS.md](docs/NATIVE_ENGINE_STATUS.md) for scope and
missing components.

## No Windows compatibility runtime
This branch contains no Winlator, Wine, Box64, DXVK, or other Windows compatibility runtime. The launcher does not execute NSUNSR.exe. A real native ARM64 game port requires the game's source code or a substantial clean-room reimplementation and is not included.

## Build
Open **Actions** and run **Build Storm Revolution Native Launcher**. Download the artifact named `storm-revolution-native-launcher-debug` after a successful run.
