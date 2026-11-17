# Storm Revolution Launcher — Storm Engine Native 3D

Standalone Android launcher with an in-progress native C++17 / ARM64 engine.

## Engine Core v0.7.0

- Kotlin launcher and JNI bridge.
- Native Android Vulkan surface, swapchain and presentation.
- Vulkan graphics pipeline with vertex and fragment SPIR-V shaders.
- Perspective camera, color material tint, depth attachment and depth testing.
- Procedural low-poly sphere mesh used to assemble a temporary test actor and platform.
- Fixed-step 60 Hz simulation with bounded catch-up.
- Scene/entity state; left-side virtual analog input moves the test actor.
- App-private shader/asset loading with relative-path validation and a 128 MiB cache.
- Android Storage Access Framework folder picker for the user's game installation directory.
- Read-only recursive asset inventory with extension counts and 16-byte header previews.
- File picker reads the provider's actual `DISPLAY_NAME`, accepts any `.exe` filename and remembers the selected document URI.
- GitHub Actions compiles GLSL to SPIR-V, builds the APK and verifies ARM64 library/shader assets.

The inventory recognizes basic signatures such as DOS/PE `MZ`, CRI `CPK `, `CRILAYLA`, `NDP3`, and `NTP3` where present. It does not yet decode the proprietary payload formats. The scanner never modifies files.

The 3D actor is generated from simple engine test geometry. It is not a Naruto character model and does not contain game artwork.

## Not included

This project contains no Winlator, Wine, Box64, DXVK or Windows compatibility runtime. It does not execute `NSUNSR.exe`.

It is not the original Naruto Storm Revolution engine. No proprietary gameplay code, game asset readers, character rigs, game animations, sounds or generated game recompilation output are included. A native game port still requires a clean-room implementation and legally usable game data, plus format validation using the user's own installation. The Windows PE32 executable cannot be directly converted into a runnable Android ARM64 game by this launcher.

## Current asset pipeline boundary

The launcher can select and inventory the game folder. It reports filenames, extensions, file sizes and the first 16 bytes of selected files. It does not yet extract CPK archives, decompress CRILAYLA, parse XFBIN chunks, decode NUD/NUT meshes and textures, or load retail animation data. These stages must be added and tested against actual retail files before claiming game asset support.

## Build

Open **Actions** and run **Build Storm Revolution Native Launcher**. The workflow installs `glslang-tools`, compiles the Vulkan shaders, then builds the APK. Download the artifact named `storm-revolution-native-launcher-debug`.
