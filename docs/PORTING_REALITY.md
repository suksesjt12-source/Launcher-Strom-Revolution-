# Native Android Port: Technical Reality

## What the PC executable tells us

The submitted `NSUNSR.exe` was identified in the initial audit as a Windows PE32 / Intel x86 executable. The file is a compiled program, not a project containing editable engine source code.

The PC installation may also contain DLLs, archives, configuration files, and game assets. Those files can help identify dependencies and data formats, but they do not restore the original source code.

## Why direct recompilation is not available

A Windows x86 executable cannot be passed to the Android NDK and turned into an ARM64 game. A native port needs replacement or reimplementation of Windows-specific services and the original engine's platform layer, including graphics, audio, input, threading, file access, and startup/runtime behavior.

A compatibility layer such as Wine plus CPU translation is a different approach. It can attempt to execute the Windows build, but it is not a native recompile and carries its own runtime and GPU-driver constraints.

## Current repository scope

The Android application currently provides a read-only game-folder audit:

- SAF folder selection with persisted read access.
- A bounded directory scan.
- PE header detection for the main executable.
- DLL, CPK, and data-folder inventory.
- Unit tests for PE32 header parsing.

The app does not include copyrighted game data, does not patch the executable, and does not yet execute the game.

## Requirements before a native gameplay port

At least one of these foundations is needed:

1. The original engine/game source code with its build system and dependencies; or
2. A documented, legally usable native reimplementation route with enough technical information to reproduce the required engine behavior.

Any use of game assets must remain within the rights granted to the owner of the game copy. Do not commit the PC installation, executable, DLLs, proprietary archives, or extracted game assets to this repository.

## Verification policy

A successful GitHub Actions run proves that the Android audit app compiles and its unit tests pass. It does not prove that the commercial game runs on Android. Actual game compatibility requires a separate test on the intended device with a lawful, complete installation and working runtime or native implementation.
