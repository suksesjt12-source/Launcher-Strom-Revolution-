# Playable Android route: Winlator handoff

## Current architecture

The PC executable supplied for the initial audit is Windows PE32 / Intel x86. An EXE/DLL/data directory is compiled output, not the original source tree, so it cannot be directly rebuilt by the Android NDK into ARM64 native game code.

This branch therefore uses a companion-launcher architecture:

1. The user installs and initializes Winlator Mali.
2. Winlator owns the Wine/Box64 runtime, graphics backend, audio, and game process.
3. The user selects the actual `NSUNSR.exe` from shared storage or an exported `.desktop` shortcut.
4. The user supplies the Winlator container ID. If a shortcut already includes a `container_id:` line, the app extracts it.
5. The companion app starts Winlator's exported game-display activity with the container ID, file path, and shortcut name.

## Winlator integration verified from source

The Winlator Mali Ludashi build declares package ID `com.ludashi.benchmark`; its standard flavor declares `com.winlator.mali`. Its source manifest marks `com.winlator.cmod.XServerDisplayActivity` as exported. The shortcut list launches that activity with the extras `container_id`, `shortcut_path`, and `shortcut_name`. Winlator's `ContainerManager` associates a container ID with its container directory, and a default container is named `Container-<ID>`.

These details are implementation-specific, not a stable public Android API. The launcher checks that the activity is exported before attempting the handoff. A future Winlator release may change its package ID, class name, or extras.

## What this does not do

- It does not bundle Wine, Box64, DXVK, the game executable, or game data.
- It does not recompile the game to ARM64.
- It does not bypass a missing Winlator container or missing game files.
- It cannot guarantee the game will start or perform well; the final verification must happen on the target phone.

## Validation plan

Automated tests cover parsing a direct EXE selection and a Winlator-exported shortcut, plus rejection of invalid storage paths and file types. A successful APK build validates the companion app only. Actual gameplay must be verified on the device after Winlator has a working container and the game files are accessible to it.
