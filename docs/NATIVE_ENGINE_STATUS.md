# Storm Engine Native — Status 0.7.0

## Implemented native modules

- **Runtime:** 60 Hz fixed-step simulation, bounded catch-up after frame stalls, render-frame and simulation-update counters.
- **Scene/entity:** sandbox actor transform; actor world position is exposed safely to the renderer.
- **Input:** thread-safe multi-touch input. Left-side analog input moves the test actor.
- **Asset store:** app-private relative-path binary loader/cache; rejects absolute paths, parent traversal, backslashes and drive prefixes; 128 MiB memory cap.
- **Vulkan surface:** Android surface, swapchain, command submission and presentation.
- **Graphics pipeline:** compiled vertex/fragment SPIR-V shaders, vertex buffer, procedural sphere mesh, push-constant transforms, perspective camera, depth image/render pass and depth testing.
- **Test scene:** low-poly placeholder actor and circular floor assembled from procedural sphere geometry; input moves the actor in the preview.
- **File picker:** obtains the filename using Android Storage Access Framework `OpenableColumns.DISPLAY_NAME`, accepts any `.exe` filename, and persists the selected URI/name.
- **Folder access:** Android Storage Access Framework directory permission can be persisted across app restarts.
- **Asset inventory:** recursive, read-only scan with a 12,000-entry cap, 18-level depth cap, extension counts, known file sizes and 16-byte signature previews. It does not modify or unpack the user's files.

## Asset format status

The scanner can identify common outer-container signatures such as `CPK ` or `CRILAYLA` when they occur at the beginning of a file. It reports XFBIN/NUD/NUT/ANM by extension and displays their leading bytes without interpreting them.

No retail CPK extractor, CRILAYLA decoder, XFBIN/NUCC chunk parser, NUD mesh importer, NUT texture decoder or retail animation parser has been integrated yet. The next parser must be validated against the actual files from the user's PC installation; format assumptions from other game versions or platforms are not enough.

## Prototype limits

- The actor is a temporary procedural test model, not a production character asset.
- No skeletal rig, imported retail 3D model format, retail material/texture system, animation graph, physics or game combat system yet.
- No game audio backend, sound-bank decoding, save compatibility or proprietary resource parser.
- No loading or execution of Windows `NSUNSR.exe`.
- Device runtime rendering still requires testing on physical Android Vulkan hardware; GitHub Actions verifies compilation and APK packaging, not GPU visual output or frame rate.

## Source and rights

The engine core is original platform infrastructure and procedural test geometry. It does not contain the original commercial game's source or assets. A real game port requires a clean-room implementation and lawful access to the game data used.
