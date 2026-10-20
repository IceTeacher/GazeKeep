<div align="center">
  <img src="docs/assets/app-icon.png" width="120" height="120" alt="GazeKeep" />

# GazeKeep

[![GitHub stars](https://img.shields.io/github/stars/IceTeacher/GazeKeep)](https://github.com/IceTeacher/GazeKeep)
[![Website](https://img.shields.io/badge/Website-gazekeep.rainafter.cn-14795e)](https://gazekeep.rainafter.cn)
[![Chinese README](https://img.shields.io/badge/README-简体中文-blue)](README.md)

[🌐 Official Website](https://gazekeep.rainafter.cn) &nbsp;|&nbsp; [📖 简体中文](README.md)

</div>

Have you ever encountered this frustrating moment: reading long articles, researching recipes while cooking with wet hands, only for the screen to dim or turn off right when you need it?
To avoid interruption, you might adjust the system timeout to 10 minutes or even longer. But that brings a new problem—if you fall asleep while reading in bed, the screen stays awake all night, draining battery and risking screen burn-in.

"Short timeouts are annoying; long timeouts risk sleeping with the screen on." GazeKeep was created specifically to solve this dilemma.

Its design philosophy is straightforward: **Bring a secure, seamless, native-grade gaze detection experience to Android devices that lack built-in screen-attention features, giving older devices a fresh and effortless life.**

With GazeKeep, you can confidently set your system screen timeout to an ultra-short 15 or 30 seconds. While you focus on the screen, GazeKeep briefly checks via the front camera just before timeout. As long as you are looking, it quietly extends screen-on time. If you look away or doze off, it never interferes, letting the screen sleep right on schedule.

## Core Features & Experience

- **Brief Checks on Demand, Zero Distraction**: The front camera is never kept continuously active. It samples only 1–3 frames briefly near system dim or screen timeout, closing immediately after. Face detection and inference run completely on-device offline. Keeping the screen awake is silent and unobtrusive with no popups; notifications only appear on service error or backend disconnection.
- **Sleep When Uncertain, Never Stay Awake Blindly**: Under low light, sideways angles, unclear frames ("unclear detection"), multiple faces, or when the camera is occupied by system camera/scanner apps, GazeKeep decisively releases control, letting the screen sleep normally. It also features a built-in single-session maximum keep-awake protection limit (default 30 minutes, customizable or can be set to unlimited) to prevent accidental infinite awake time.
- **Three Backends for Diverse Device Conditions**: Tailored for varying device environments: **LSPosed** (system framework integration), **Shizuku** (ADB / wireless debugging authorization, no root needed), and **Accessibility Service** (zero barrier, works out of the box). Supports automatic priority-based selection or manual pinning.
- **App Whitelist & Visual Camera Self-Test**: Enabled by default, whitelist allows you to choose only reading or browser apps to avoid triggering globally. Includes a built-in 2-step camera self-test wizard, a clean and restrained settings UI, customizable lead times and sensitivity, and on-demand local diagnostic logging.

## Screenshots

|                                     Home Status                                      |                                   Settings                                   |                                     Backends                                      |                                      App Whitelist                                       |
| :----------------------------------------------------------------------------------: | :--------------------------------------------------------------------------: | :-------------------------------------------------------------------------------: | :--------------------------------------------------------------------------------------: |
| <img src="docs/assets/screenshot-home.png" width="200" alt="Home status overview" /> | <img src="docs/assets/screenshot-settings.png" width="200" alt="Settings" /> | <img src="docs/assets/screenshot-backend.png" width="200" alt="Three backends" /> | <img src="docs/assets/screenshot-apps.png" width="200" alt="App whitelist management" /> |

## Privacy & Security

1. The front camera opens only near screen timeout and closes immediately after checking.
2. Frames are processed in memory to check whether you are looking at the screen. Nothing is saved or uploaded.
3. Unclear results, failed detection or another person in view let the screen sleep normally.

This app does not request Internet (`INTERNET`), storage/photos, installed package queries (`QUERY_ALL_PACKAGES`), or overlay (`SYSTEM_ALERT_WINDOW`) permissions. It does not integrate any analytics, advertising, or crash reporting SDKs. The accessibility service does not inspect window text content. Camera preview only appears in the user-initiated self-test wizard and releases immediately upon exit. See [Privacy & Security](docs/product/privacy-security.md) for full boundaries.

## Working Modes & Getting Started

### 1. 4-Step Setup Flow

1. Install APK and grant camera permission (notification permission is recommended on Android 13+ for error alerts).
2. Complete the 2-step camera self-test: looking at screen recognizes as "Looking at screen", turning head or looking away recognizes as "Not looking" ("Unclear detection" does not count as passed).
3. Keep app whitelist enabled, selecting reading or browser apps that need gaze-keep.
4. Verify any backend is ready, then turn on the master switch on the home page.

### 2. Backend Comparison & Preparation

| Backend       | Applicable Conditions  | Preparation                                                                                      |
| ------------- | ---------------------- | ------------------------------------------------------------------------------------------------ |
| LSPosed       | Rooted with framework  | Enable module in manager, check "System Framework" scope, and reboot device                      |
| Shizuku       | Non-root (ADB)         | Start Shizuku and grant permission to GazeKeep; reactivate after reboot per Shizuku requirements |
| Accessibility | Non-root, zero barrier | Turn on GazeKeep service in system Accessibility settings, no overlay permission needed          |

Backends default to **Auto** mode, picking ready backends in order of `LSPosed → Shizuku → Accessibility`. If a backend is manually locked, it will not silently switch to other backends on failure. Foreground app identification uses accessibility events, Shizuku shell queries, or LSPosed system queries respectively. See [Usage Guide](docs/product/usage.md) for details.

## Current Status & Development

- **Compatibility & Progress**: On-device detection, three backends, UI screens, parameter configuration, and 2-step self-test are fully implemented. Android 8.0 (API 26) is the minimum supported version; actual performance depends on system version, ROM power management, and backend capabilities. Sensor gating is wired. Full physical device regression and formal release signatures remain in progress; see [Product Overview](docs/product/overview.md) and [Task List](docs/plans/todo.md).
- **Building from Source**: Build Debug APK from source (artifacts output to `apps/app/android/build/outputs/apk/debug/`):

```bash
pnpm --filter @gazekeep/app exec ./gradlew :android:assembleDebug
```

- **Documentation**: See [Contributing Guide](docs/development/contributing.md) for toolchain requirements, [Testing Matrix](docs/development/testing.md) for test matrices, and [Documentation Navigation](docs/index.md) for all documents.

## License

This project is licensed under the [MIT License](LICENSE) (third-party runtime notices can be found in the app's "About" page or `THIRD_PARTY_NOTICES.txt`).
