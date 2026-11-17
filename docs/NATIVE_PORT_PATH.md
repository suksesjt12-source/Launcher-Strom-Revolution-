# Native Port Path — Storm Engine Core 0.5.0

## Current baseline

The Android project contains a native ARM64 runtime loop, scene/entity data,
multi-touch input, a bounded app-private raw-asset cache, and Vulkan surface
presentation.

The simulation updates a sandbox actor transform from the virtual analog
input. The current Vulkan renderer still clears the screen; it does not draw
that actor as a mesh. This is engine infrastructure, not a Naruto Storm
Revolution implementation.

## Next engine milestones

1. Add a Vulkan graphics pipeline, shader compilation, vertex/index buffers,
   camera, material and mesh components.
2. Add a generic model/texture import path such as glTF 2.0 with clear asset
   provenance and licenses.
3. Add collision/physics, animation state machines and a sample arena using
   original placeholder assets.
4. Add audio output/mixing and asset-backed audio decoding.
5. Only when legally usable game-specific source/data exists, build adapters
   for the game's resources and mechanics.

## Game binary mismatch

The previously audited `NSUNSR.exe` is a Windows PE32 x86 executable. The
public Xbox 360 community port targets a different PowerPC `default.xex`
version and does not publish its engine source. They are not interchangeable.

A Vulkan renderer and generic engine core do not execute a Windows x86 binary.
