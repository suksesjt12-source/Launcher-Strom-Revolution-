# Storm Revolution Launcher — Storm Engine Native 3D

## Engine Core v0.10.0

- Adds a direct CPK file picker for inspecting large `data.cpk`, `stage.cpk`, and other CRI archives without extracting their payloads.
- Expands the bounded CPK TOC sample from 12 to 64 rows, while retaining metadata size limits and offset validation.
- CPK inspection runs on a background thread and reads through Android's Storage Access Framework; the source archive is never modified.


Standalone Android launcher with an in-progress native C++17 / ARM64 engine.

## Engine Core v0.8.0

- Kotlin launcher and JNI bridge.
- Native Android Vulkan surface, swapchain and presentation.
- Vulkan graphics pipeline with vertex and fragment SPIR-V shaders.
- Perspective camera, color material tint, depth attachment and depth testing.
- Procedural low-poly sphere mesh used to assemble a temporary test actor and platform.
- Fixed-step 60 Hz simulation with bounded catch-up.
- Scene/entity state; left-side virtual analog input moves the test actor.
- App-private shader/asset loading with relative-path validation and a 128 MiB cache.
- Android Storage Access Framework folder picker for the user's game installation directory.
- Read-only recursive asset inventory with extension counts and file-header previews.
- Metadata reader for CRI CPK/UTF headers and TOC sample entries.
- NUCC/XFBIN page/chunk map reader and bounded NUD/NUT payload extraction into app-private storage.
- GitHub Actions compiles GLSL to SPIR-V, builds the APK and verifies ARM64 library/shader assets.

The CPK reader parses the container's UTF metadata and a bounded number of TOC rows. The XFBIN reader reports index structures and chunk headers. Both are read-only and validate offsets and sizes before reading.

## Important current limits

- The CPK inspector does not extract archive payloads or decompress CRILAYLA file data.
- The XFBIN extractor saves embedded NUD/NDP3 and NUT/NTP3 payloads, but does not decode vertex/index buffers, skeletons, materials, texture pixels, ANM animation, VFX, or scripts.
- The 3D actor is generated from simple engine test geometry. It is not a Naruto character model and does not contain game artwork.
- No game combat system, retail character assets, retail animations, audio backend, save compatibility or original gameplay code is included.
- This project contains no Winlator, Wine, Box64 or DXVK. It does not execute NSUNSR.exe.

A real native game port still requires format validation against the user's actual PC installation, payload importers, and a clean-room implementation of the game runtime. The Windows PE32 executable cannot be converted into Android ARM64 code by ordinary APK compilation.

## Asset inspection flow

1. Choose **PILIH CPK & BACA INDEKS** and select `data/sim/data.cpk` first. The inspector reads the CPK header and a bounded UTF/TOC metadata sample; it does not extract the archive.
2. Repeat with `stage.cpk` to compare the stage archive index.

1. Choose **PILIH FOLDER INSTALASI GAME**.
2. Press **INVENTARIS ASET GAME**.
3. Review extension counts and the **ANALISIS CONTAINER RETAIL** section.
4. Use the actual file names, signatures, CPK TOC and XFBIN type index to choose an individual `.xfbin` file.
5. Press **PILIH XFBIN & EKSTRAK NUD/NUT**. Extracted payloads are saved only inside the app's private storage.

The scanner and extractor never modify the selected game folder. The XFBIN extractor creates output only inside the app's private storage.

## Build

Open **Actions** and run **Build Storm Revolution Native Launcher**. The workflow installs glslang-tools, compiles the Vulkan shaders, then builds the APK. Download the artifact named storm-revolution-native-launcher-debug.

## XFBIN format reference

The page/chunk reader was independently written using published CyberConnect2 NUCC/XFBIN structure research. A MIT-licensed reference implementation used for format cross-checking is `spawner1145/Blender-XFBIN-Importer` (`xfbin_lib`); its license is available at https://github.com/spawner1145/Blender-XFBIN-Importer/tree/main/xfbin_lib. This project does not bundle that Python library.
