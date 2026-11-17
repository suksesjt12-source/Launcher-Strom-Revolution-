# Native Port Path and Compatibility Gate

## Current milestone — launcher v0.4.0

The project now has a native Android Vulkan surface renderer. It creates an
Android surface and swapchain, records a render pass that clears the target,
and presents a test frame. This validates only the graphics presentation
plumbing; it is not game rendering.

The app also checks whether the selected GPU exposes the buffer-device-address
feature described in a public Storm Revolution Xbox 360 ARM64 port README.

## Game executable mismatch

The previously inspected `NSUNSR.exe` is a Windows PE32 x86 executable using
Direct3D 9. A public native-port repository found during research documents a
specific Xbox 360 PowerPC `default.xex` target with an exact NTSC-U build/hash.
Its README says the repository contains documentation rather than the actual
port implementation source.

These inputs are not interchangeable:
- Windows `NSUNSR.exe` is not an Xbox 360 `default.xex`.
- A Vulkan renderer cannot translate Windows x86 instructions into ARM64.
- The external README is not the engine implementation and does not provide
  permission to redistribute game code or proprietary assets.

## Requirements for a real native game port

One of the following is required:

1. Legally usable original game source code and its build system, followed by
   an Android/ARM64 platform port; or
2. A clean-room implementation of the engine/runtime and platform layer, with
   a legal basis for all game assets and data used.

A static-recompilation route additionally requires the exact target binary,
generated recompiled code, runtime support layer, graphics backend and
platform integration. The Windows x86 executable currently identified does
not match the external Xbox 360 recompilation target.

## Hardware checks

Install the current debug APK on the target phone and inspect the Vulkan
diagnostic. A supported buffer-device-address feature is only a capability
gate. It is not proof of renderer performance or game compatibility.

## Next steps

Verify the Vulkan preview on the target Android device, then grow the
independent renderer/platform modules. Do not treat those modules as a port of
Storm Revolution until game-specific code and legal asset access exist.
