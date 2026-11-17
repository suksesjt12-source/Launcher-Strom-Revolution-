# Native port path and compatibility gate

## Current milestone

Version 0.3.0 adds a native NDK/Vulkan check for the GPU feature used by the
public Storm Revolution Xbox 360 ARM64 static-recompilation project's README:
`VK_KHR_buffer_device_address` (or the Vulkan 1.2 core feature).

This check reports the physical GPU, device Vulkan API version, and whether
buffer device address is exposed and enabled as a supported feature. It is a
hardware capability probe only. It does not load game code, render a game
frame, or establish that the phone can run the port.

## Game executable mismatch

The launcher user's previously inspected `NSUNSR.exe` is a Windows PE32
(x86) executable using Direct3D 9. The public native-port documentation found
during research targets a specific Xbox 360 `default.xex` (PowerPC), requires
one exact NTSC-U executable hash, and says its repository contains
documentation only rather than the port's implementation source.

Those inputs are not interchangeable:
- Windows `NSUNSR.exe` is not an Xbox 360 `default.xex`.
- A Vulkan GPU probe cannot translate PE32 code into ARM64.
- The public port's README is not its game engine source and does not grant
  permission to reuse unpublished implementation code or proprietary assets.

## What is still required for a real native game port

At least one of these routes is required:

1. Obtain legally usable original game engine source and the matching build
   system, then port the platform layer to Android/ARM64; or
2. Undertake a clean-room reimplementation of the game runtime, renderer,
   asset readers, animation, audio, input, and gameplay systems, using only
   material that can lawfully be used.

For a static-recompilation route, the exact target executable, generated
recompiled code, runtime support layer, graphics backend, and platform
integration must all be available. The Windows PE32 executable currently
identified does not match the Xbox 360 recompilation target.

## Next verification on the phone

Install the 0.3.0 debug APK and read the home screen's Vulkan diagnostic.
A "LOLOS pemeriksaan fitur" result means only that the GPU exposes the
buffer-device-address capability used by that port's documented GPU gate.
It is not a performance benchmark or proof that a game build is available.
