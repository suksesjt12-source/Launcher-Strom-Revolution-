# Storm Revolution Launcher — standalone Android runtime

This project builds a **single Android APK** containing a customized Winlator Mali runtime. The user does not need to install or open a separate Winlator application.

## What the standalone APK is

The build fetches the pinned Winlator Mali source, applies Storm Revolution Launcher branding, then builds its Ludashi/Mali flavor as one Android app. The APK includes the compatibility runtime required to run Windows software on Android, rather than sending a launch request to another installed app.

The underlying runtime uses Wine and Box64 (with the graphics/audio components supplied by the selected Winlator Mali source). This is an embedded compatibility environment, **not a native ARM64 recompile of Naruto Storm Revolution**.

## What is required to play

1. Install the generated Storm Revolution Launcher APK.
2. Open the app once and allow its initial runtime files to be prepared.
3. Create a container inside the app for the user's own Windows game installation.
4. Add the user's own `NSUNSR.exe` as a game shortcut in that container.
5. Launch the shortcut from the same Storm Revolution Launcher app.

The game files are not included in the APK. The user must copy their legally acquired PC game folder to the Android device. The app's first usable build will provide the integrated Winlator Mali interface; automating first-run container creation and direct game boot is a subsequent step, not yet verified.

## Device target

Initial target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, 8 GB RAM, Android 14. Actual game compatibility, graphical correctness, temperature and frame rate must be tested on the physical phone. No 30 FPS or successful boot claim is made before that test.

## Build source

The GitHub Actions workflow:
- fetches Winlator Mali branch `bionic-mali-1.2` at pinned commit `0411a4b602ed00d71bef52e6a7ccb82a8e709b1a`;
- uses its Ludashi/Mali Android runtime;
- changes the application ID to `com.stormrevolution.launcher` and the app label to `Storm Revolution Launcher`;
- builds `assembleLudashiDebug`;
- uploads the resulting APK as `storm-revolution-standalone-mali-apk`.

The old Jetpack Compose audit prototype source under `app/` in this repository is not the runtime used by this standalone build workflow.

## Licensing and game files

Winlator Mali is MIT-licensed; the project preserves upstream notices and the licenses of included runtime components in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES). Runtime downloads may be provided by the upstream project. Do not commit or upload proprietary game executables, DLLs, archives or extracted assets to this repository.

See [docs/PORTING_REALITY.md](docs/PORTING_REALITY.md) for technical limitations and verification status.
