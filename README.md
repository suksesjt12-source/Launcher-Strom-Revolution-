# Storm Revolution Launcher — custom Android launcher

This repository builds a **single Android APK** for launching a user's own Windows copy of Naruto Shippuden: Ultimate Ninja Storm Revolution. The APK includes the Winlator Mali compatibility runtime; no separately installed Winlator app is required.

## Storm-specific interface

The build-time patch installs a dedicated Storm home screen as the default screen. It provides:

- a Storm Revolution game panel;
- a file picker for the user's own `NSUNSR.exe`;
- a selector for the Windows/Wine container to use;
- a **Main Sekarang** button that launches the selected executable directly inside the embedded runtime;
- shortcuts to the app's controller, driver/runtime and file-management settings.

The launch action calls the app's internal `XServerDisplayActivity` with the selected container and executable path. It does not send an Intent to another Winlator package.

## Important technical distinction

This is **not** a native ARM64 port of the game. The target executable is a Windows x86 binary, so this build uses Wine and Box64 inside the Storm APK to provide Windows compatibility. A true native Android engine would require the original game source/build dependencies or a major clean-room reimplementation; the EXE alone is insufficient.

## First-run use

1. Build and install the APK from the GitHub Actions artifact.
2. Open the app and complete the runtime's first-run extraction/setup.
3. Copy your own complete PC game folder to shared Android storage. Keep the original folder structure intact.
4. Open the Storm home screen and choose the exact `NSUNSR.exe` inside that folder.
5. Create a Wine/Proton container using **Buat / Atur Container** if none exists. Select that container on the home screen.
6. Press **Main Sekarang**.

The executable picker stores the selected path and checks that it is an accessible `NSUNSR.exe`. The game files are not bundled in the APK. The selected Windows container still needs its normal Wine setup; the launcher does not silently fabricate or auto-create a configured container.

## Device target and validation

Initial target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, 8 GB RAM, Android 14. The APK build is separate from proof of gameplay compatibility. Boot, rendering, audio, controls, stability, temperature and frame rate still require testing on the physical device.

## Build source

GitHub Actions:
- fetches Winlator Mali branch `bionic-mali-1.2` at pinned commit `0411a4b602ed00d71bef52e6a7ccb82a8e709b1a`;
- applies Storm package branding and the custom home/game-launch patch;
- builds the Ludashi/Mali flavor with `:app:assembleLudashiDebug`;
- uploads `storm-revolution-standalone-mali-apk`.

The old Jetpack Compose audit prototype under `app/` is not used by the current standalone runtime workflow.

## Licensing and game files

Winlator Mali is MIT-licensed. Upstream and third-party notices must remain available in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES). Do not commit proprietary game executables, DLLs, archives or extracted assets to this repository.

See [docs/PORTING_REALITY.md](docs/PORTING_REALITY.md) for the porting constraints and validation checklist.
