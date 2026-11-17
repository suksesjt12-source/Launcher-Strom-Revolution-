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
- **XFBIN inspector:** reads the NUCC file header, index counts/string tables, validates aggregate table sizes and displays bounded chunk-header locations/sizes. It does not interpret the chunk payload formats.

## Retail payload status

No retail payload importer is complete yet. NUD/NDP3 mesh, NUT/NTP3 texture, ANM animation, materials, VFX, audio, scripts and gameplay parameters are not decoded or sent to the renderer. Container metadata parsing is a first stage, not evidence that retail content is playable.

The CPK and XFBIN readers have been added from format descriptions and public references; they still require validation against actual PC game files. CPK variants, encryption, packing, alignment, or PC-specific differences may result in a clear unsupported-format report.

## Prototype limits

- The actor is a temporary procedural test model, not a production character asset.
- No skeletal rig, imported retail 3D model format, retail material/texture system, animation graph, physics or game combat system yet.
- No game audio backend or save compatibility.
- No loading or execution of Windows NSUNSR.exe.
- Device runtime rendering still requires testing on physical Android Vulkan hardware; GitHub Actions verifies compilation and APK packaging, not GPU visual output or frame rate.

## Source and rights

The engine core is original platform infrastructure and procedural test geometry. No proprietary executable or retail game assets are included in the repository. A real game port requires a clean-room implementation and lawful access to the user's game data.
