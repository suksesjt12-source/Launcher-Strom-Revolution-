# Storm Revolution — Native Android Engine

## Engine Core v0.12.0

- Extracts `.xfbin` entries from named-TOC CRI CPK archives through Android Storage Access Framework.
- Decompresses CRILAYLA members with output-size validation and bounded per-file/total output sizes.
- Writes extracted data to app-private storage; the selected CPK remains read-only.
- Adds JVM unit tests for the CRILAYLA literal-stream path and malformed sizes; CI runs those tests before packaging the APK.
- ITOC-only archives are reported as unsupported rather than guessing their index layout.


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

The CPK reader parses UTF metadata and extracts a bounded number of named-TOC XFBIN members, decompressing CRILAYLA entries. The XFBIN reader reports index structures and extracts raw NUD/NUT payloads. All source archives are read-only and offsets/sizes are validated before reading.

## Important current limits

- CPK extraction currently supports named TOC XFBIN entries only. ITOC-only archives and non-XFBIN entries are not extracted.
- The XFBIN extractor saves embedded NUD/NDP3 and NUT/NTP3 payloads, but does not decode vertex/index buffers, skeletons, materials, texture pixels, ANM animation, VFX, or scripts.
- The 3D actor is generated from simple engine test geometry. It is not a Naruto character model and does not contain game artwork.
- No game combat system, retail character assets, retail animations, audio backend, save compatibility or original gameplay code is included.
- This project contains no Winlator, Wine, Box64 or DXVK. It does not execute NSUNSR.exe.

A real native game port still requires format validation against the user's actual PC installation, payload importers, and a clean-room implementation of the game runtime. The Windows PE32 executable cannot be converted into Android ARM64 code by ordinary APK compilation.

## Asset inspection flow

1. Choose **PILIH CPK & BACA INDEKS** and select `data/sim/data.cpk` or `stage.cpk`.
2. Review the TOC report, then press **EKSTRAK XFBIN DARI CPK**. Supported XFBIN entries are written to app-private storage. CRILAYLA-compressed entries are decompressed during extraction.

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
