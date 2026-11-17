# Storm Engine Native — Status 0.6.1

## Implemented native modules

- **Runtime:** 60 Hz fixed-step simulation, bounded catch-up after frame stalls, render-frame and simulation-update counters.
- **Scene/entity:** sandbox actor transform; actor world position is exposed safely to the renderer.
- **Input:** thread-safe multi-touch input. Left-side analog input moves the test actor.
- **Asset store:** app-private relative-path binary loader/cache; rejects absolute paths, parent traversal, backslashes and drive prefixes; 128 MiB memory cap.
- **Vulkan surface:** Android surface, swapchain, command submission and presentation.
- **Graphics pipeline:** compiled vertex/fragment SPIR-V shaders, vertex buffer, procedural sphere mesh, push-constant transforms, perspective camera, depth image/render pass and depth testing.
- **Test scene:** low-poly placeholder actor and circular floor assembled from procedural sphere geometry; input moves the actor in the preview.
- **File picker:** obtains the filename using Android Storage Access Framework `OpenableColumns.DISPLAY_NAME`, accepts any `.exe` filename, and persists the selected URI/name.

## Prototype limits

- The actor is a temporary procedural test model, not a production character asset.
- No skeletal rig, imported 3D model format, material/texture system, animation graph, physics or game combat system yet.
- No game audio backend, sound-bank decoding, save compatibility or proprietary resource parser.
- No loading or execution of Windows `NSUNSR.exe`.
- Device runtime rendering still requires testing on physical Android Vulkan hardware; GitHub Actions verifies compilation and APK packaging, not GPU visual output or frame rate.

## Source and rights

The engine core is original platform infrastructure and procedural test geometry. It does not contain the original commercial game's source or assets. A real game port requires legally usable original source and its build system, or a clean-room implementation and legal access to the game data.
