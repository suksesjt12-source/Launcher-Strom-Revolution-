# Storm Revolution Launcher — Storm Engine Native 3D

Standalone Android launcher with an in-progress native C++17 / ARM64 engine.

## Engine Core v0.6.0

- Kotlin launcher and JNI bridge.
- Native Android Vulkan surface, swapchain and presentation.
- Vulkan graphics pipeline with vertex and fragment SPIR-V shaders.
- Perspective camera, color material tint, depth attachment and depth testing.
- Procedural low-poly sphere mesh used to assemble a temporary test actor and platform.
- Fixed-step 60 Hz simulation with bounded catch-up.
- Scene/entity state; left-side virtual analog input moves the test actor.
- App-private shader/asset loading with relative-path validation and a 128 MiB cache.
- GitHub Actions compiles GLSL to SPIR-V, builds the APK and verifies ARM64 library/shader assets.

The 3D actor is generated from simple engine test geometry. It is not a Naruto character model and does not contain game artwork.

## Not included

This project contains no Winlator, Wine, Box64, DXVK or Windows compatibility runtime. It does not execute `NSUNSR.exe`.

It is not the original Naruto Storm Revolution engine. No proprietary gameplay code, game asset readers, character rigs, game animations, sounds or generated game recompilation output are included. A native game port still requires legally usable source code or a clean-room implementation and legally usable game data. The Windows PE32 executable cannot be directly converted into a runnable Android ARM64 game by this launcher.

## Build

Open **Actions** and run **Build Storm Revolution Native Launcher**. The workflow installs `glslang-tools`, compiles the Vulkan shaders, then builds the APK. Download the artifact named `storm-revolution-native-launcher-debug`.
