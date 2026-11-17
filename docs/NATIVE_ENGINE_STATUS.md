# Storm Engine Native — Status 0.8.0

## Implemented native modules

- **Runtime:** 60 Hz fixed-step simulation, bounded catch-up after frame stalls, render-frame and simulation-update counters.
- **Scene/entity:** sandbox actor transform; actor world position is exposed safely to the renderer.
- **Input:** thread-safe multi-touch input. Left-side analog input moves the test actor.
- **Asset store:** app-private relative-path binary loader/cache; rejects absolute paths, parent traversal, backslashes and drive prefixes; 128 MiB memory cap.
- **Vulkan surface:** Android surface, swapchain, command submission and presentation.
- **Graphics pipeline:** compiled vertex/fragment SPIR-V shaders, vertex buffer, procedural sphere mesh, push-constant transforms, perspective camera, depth image/render pass and depth testing.
- **Test scene:** low-poly placeholder actor and circular floor assembled from procedural sphere geometry; input moves the actor in the preview.
- **File picker:** Android Storage Access Framework reads the provider's actual display name, accepts any .exe filename, and persists the selected URI/name.
- **Folder access:** Android Storage Access Framework directory permission can be persisted across app restarts.
- **Asset inventory:** recursive, read-only scan with a 12,000-entry cap, 18-level depth cap, extension counts, known file sizes and 16-byte signature previews.
- **CPK/UTF inspector:** identifies a CPK frame, parses its UTF header/schema and displays bounded sample TOC rows when present. Supports plain and CRI's simple UTF XOR-obfuscated table header. It does not extract archive entries or decompress CRILAYLA.
- **XFBIN payload extractor:** reads the type/path/name tables, chunk maps, reference tables and page-local map indices; walks NUCC chunks and extracts embedded NDP3/NUD and NTP3/NUT payloads into app-private storage with path and size guards.

## Retail payload status

The XFBIN extractor now saves embedded NUD/NDP3 and NUT/NTP3 payloads, but does not yet decode mesh vertex/index buffers, skeletons, materials, texture pixels or ANM animation, and does not send retail content to the renderer. CPK entry extraction/CRILAYLA decompression also remains unfinished. Container extraction is not evidence that retail content is playable.

The CPK reader and XFBIN page/chunk extractor were written from format documentation and public reference implementations; they still require validation against actual PC game files. CPK variants, encryption, packing, alignment, or PC-specific differences may result in a clear unsupported-format report.

## Prototype limits

- The actor is a temporary procedural test model, not a production character asset.
- No skeletal rig, imported retail 3D model format, retail material/texture system, animation graph, physics or game combat system yet.
- No game audio backend or save compatibility.
- No loading or execution of Windows NSUNSR.exe.
- Device runtime rendering still requires testing on physical Android Vulkan hardware; GitHub Actions verifies compilation and APK packaging, not GPU visual output or frame rate.

## Source and rights

The engine core is original platform infrastructure and procedural test geometry. No proprietary executable or retail game assets are included in the repository. A real game port requires a clean-room implementation and lawful access to the user's game data.

## Extraction output

The user can choose an individual `.xfbin` file from Android's document picker. The extractor writes recognized model NUD and texture NUT payloads below the app-private `files/retail_extract/` directory. It does not export, upload, or redistribute proprietary game assets. No real PC installation has yet been supplied to validate extraction on Storm Revolution data.
