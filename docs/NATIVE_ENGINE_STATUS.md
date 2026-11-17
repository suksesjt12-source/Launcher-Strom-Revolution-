# Storm Engine Core — Status 0.5.0

## Implemented native modules

- **Runtime:** 60 Hz fixed-step simulation, bounded catch-up after frame stalls, render-frame and simulation-update counters.
- **Scene/entity:** entity store with a sandbox actor transform updated by movement input.
- **Input:** thread-safe multi-touch input queue. Left half maps to an analog stick; upper-right maps to jump; lower-right maps to attack.
- **Asset store:** app-private relative-path binary loader/cache, rejects absolute and parent-traversal paths, 128 MiB cache budget.
- **Graphics:** native Vulkan Android surface, swapchain, render pass, framebuffers, command submission and presentation.

## Prototype limits

- Vulkan currently clears the swapchain image only; no mesh draw calls, shader/material pipeline, camera or skeletal renderer yet.
- Sandbox actor simulation is not visible as a model yet.
- Asset cache handles generic raw files, not the game's proprietary resource formats.
- No game audio backend, sound-bank decoding, animation runtime, gameplay or save compatibility yet.
- No loading or execution of Windows `NSUNSR.exe`.

## Source and rights

The engine core is original platform infrastructure. It does not contain the original commercial game's source or assets. A real game port requires legally usable original source and its build system, or a clean-room implementation and legal access to the game data used.
