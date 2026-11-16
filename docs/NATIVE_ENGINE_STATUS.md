# Native Engine Bootstrap — Status

This branch now builds a small C++17 JNI library named `storm_engine` for
`arm64-v8a`. The library initializes a Vulkan instance and enumerates Vulkan
physical devices so the launcher can report the GPU and Vulkan API version.

## What this is

- Native Android NDK/C++ code.
- Vulkan loader and physical-device discovery.
- Kotlin/JNI bridge used by the launcher.
- No Wine, Box64, DXVK, or Winlator dependency.

## What this is not

- It is not the Naruto Storm Revolution game engine.
- It does not parse or execute `NSUNSR.exe`.
- It does not contain the game's recompilation output, game-specific renderer,
  asset loaders, audio implementation, or input/gameplay runtime.
- A successful APK build does not mean the game is playable.

## Required next source material

The public Storm Revolution native-port repository documents an Xbox 360
`default.xex`-based ARM64 port, but its repository says it contains
documentation rather than the port's implementation source. The Windows
`NSUNSR.exe` file is a different executable format and is not a drop-in input
for that port.

A real native game engine integration requires legally usable source code and
the matching game-specific recompilation output/runtime, plus Vulkan renderer,
audio, input, assets and platform integration. This bootstrap is only the
first native diagnostic layer.
