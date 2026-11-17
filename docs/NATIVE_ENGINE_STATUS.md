# Native Engine Status

## Version 0.4.0 — Vulkan surface renderer bring-up

The app builds an ARM64 C++17 JNI library named `storm_engine` and connects it
to an Android `SurfaceView`.

The native renderer now:
- Creates a Vulkan instance and Android surface.
- Selects a physical device with a graphics queue that can present to the
  Android surface and supports `VK_KHR_swapchain`.
- Creates a swapchain, image views, render pass, framebuffers, command pool,
  command buffer and synchronization primitives.
- Submits a render pass that clears the swapchain image and presents it.
- Stops and releases Vulkan objects when Android destroys the surface.

The preview is currently a clear-color frame, not a triangle, model, scene, or
game renderer. Its purpose is to prove the native Android Vulkan surface and
presentation lifecycle are connected end-to-end.

## Other existing native diagnostics

The launcher also reports:
- Physical GPU name and Vulkan API version.
- Whether buffer device address is exposed as a supported device feature.

A capability result is not a performance benchmark and does not prove a game
will run on the device.

## What is not implemented

- No loading or execution of Windows `NSUNSR.exe`.
- No game-specific ARM64 recompilation output.
- No Storm Revolution asset/XFBIN reader.
- No skeletal animation, materials, effects, or game scene renderer.
- No game audio backend, controller mapping runtime, gameplay, or save system.

## Executable mismatch and source requirement

The previously inspected `NSUNSR.exe` is a Windows PE32 x86 executable that
uses Direct3D 9. The public Xbox 360 native-port documentation found during
research targets a specific PowerPC `default.xex` build instead. They are
different binaries and are not interchangeable. The public documentation repo
also states that it contains documentation rather than the actual engine
implementation.

A complete native port requires legally usable original source and the
matching build system, or a clean-room reimplementation with appropriate
rights to any assets used. The launcher does not create this source from the
Windows executable.

## Next engineering milestone

Build a basic native render graph and shader pipeline after Vulkan surface
presentation is verified on the target phone. Then implement independently
testable platform modules (input, audio, file I/O and resource management).
These components still do not substitute for the unavailable game-specific
engine and assets.
