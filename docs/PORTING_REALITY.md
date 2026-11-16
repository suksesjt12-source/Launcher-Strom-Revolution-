# Storm launcher: porting scope and verification

## Implemented application architecture

The target behavior is one Android app that launches the game without requiring a separately installed Winlator application. The build fetches a pinned Winlator Mali source and packages its Wine/Box64 compatibility runtime inside the Storm Revolution Launcher APK.

The build patch adds a dedicated Storm home screen as the app's default view. The screen allows the user to select the existing `NSUNSR.exe`, select a preconfigured Windows container, and launch the executable. The launch button invokes the embedded app's `XServerDisplayActivity` using the chosen container ID and executable path. It does not launch another installed package.

The custom home screen also links to controller, graphics/runtime and file-management sections. Container configuration remains within the same APK.

## Why this is not a native Android engine

The submitted `NSUNSR.exe` was identified in the earlier file audit as Windows PE32 / Intel x86. A compiled Windows executable and its DLL/data files are not the original C/C++ source code. Android NDK cannot directly convert that binary into a native ARM64 Android game.

A real native game port needs the original source and build dependencies, or a substantial clean-room implementation of the engine/platform layers. Wine/Box64 provides Windows compatibility, not a native recompile of the game's engine.

## User setup still required

- Copy the user's own complete PC game folder to Android shared storage; preserve the folder structure and related data files.
- Select the exact `NSUNSR.exe` through the Storm home screen. The selected path is stored and validated.
- Complete runtime first-run extraction if needed.
- Create and configure a Wine/Proton container within the same app.
- Select that container and press **Main Sekarang**.

The app does not contain the commercial game files. Container creation is not automatically performed because it depends on the installed Wine version and the user's chosen compatibility settings.

## Build and test boundaries

The GitHub Actions workflow fetches Winlator Mali branch `bionic-mali-1.2` at pinned commit `0411a4b602ed00d71bef52e6a7ccb82a8e709b1a`, applies Storm branding and the custom home patch, then builds the ARM64 Mali flavor.

A successful Gradle build verifies packaging and code compilation. It does not prove the game boots or renders correctly on the target device.

Required physical-device validation:
- install the generated APK on an ARM64 Mali phone;
- complete runtime extraction and install/configure Wine;
- create a container with suitable graphics/audio settings;
- select the user's game EXE and launch it;
- test menu and gameplay rendering, audio, touch controls, crash behavior, frame pacing, temperature and extended sessions.

No claim of native Android gameplay or stable FPS is made until those tests pass.
