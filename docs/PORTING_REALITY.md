# Playable Android route: Winlator handoff

## Current architecture

The PC executable supplied for the initial audit is Windows PE32 / Intel x86. An EXE/DLL/data directory is compiled output, not the original source tree, so it cannot be directly rebuilt by the Android NDK into ARM64 native game code.

This branch therefore uses a companion-launcher architecture:

1. The user installs and initializes Winlator Mali.
2. Winlator owns the Wine/Box64 runtime, graphics backend, audio, and game process.
3. The user creates a shortcut for their own `NSUNSR.exe` inside a Winlator container.
4. Winlator exports the shortcut as a `.desktop` file to shared storage.
5. This companion reads the exported shortcut metadata and starts Winlator's exported game-display activity with the container ID, shortcut path, and shortcut name.

## Winlator integration verified from source

The Winlator Mali Ludashi build declares package ID `com.ludashi.benchmark`; its standard flavor declares `com.winlator.mali`. Its source manifest marks `com.winlator.cmod.XServerDisplayActivity` as exported. The shortcut list launches that activity with the extras `container_id`, `shortcut_path`, and `shortcut_name`. The export action copies shortcut files to `/storage/emulated/0/Winlator/Shortcuts`.

These details are implementation-specific, not a stable public Android API. The launcher checks that the activity is exported before attempting the handoff. A future Winlator release may change its package ID, class name, or extras.

## What this does not do

- It does not bundle Wine, Box64, DXVK, the game executable, or the game data.
- It does not recompile the game to ARM64.
- It does not bypass a missing Winlator container or missing game files.
- It cannot guarantee the game will start or perform well; the final verification must happen on the target phone.

## Validation plan

Automated tests cover parsing the exported shortcut metadata and rejecting invalid storage paths/container IDs. A successful APK build validates the companion app only. Actual gameplay must be verified on the device after Winlator has a working container and game shortcut.
