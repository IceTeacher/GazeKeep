# v1.1.8

## Changelog

### Added

- Added an "Ignore limit while charging" toggle (off by default): bypasses the maximum keep-awake cap while plugged in for long desktop-stand reading sessions; looking away or uncertain results still let the screen sleep normally, and unplugging immediately restores the limit starting from the first subsequent extension (automatically grayed out while preserving your stored preference when maximum keep-awake time is set to Unlimited).
- Added a "Pause when face down" toggle (on by default): skips checks while the phone rests screen-down on a desk; turn it off if you frequently read lying flat holding the phone overhead (requires sufficient ambient light and upright forward gaze), while proximity and dark-scene checks continue to enforce sleep when truly covered or placed down.

### Improved

- Expanded multi-ROM background operation guidance: beyond opening the Xiaomi / HyperOS battery saver screen directly, added per-ROM guidance for OPPO / OnePlus / realme (ColorOS), vivo / iQOO (OriginOS), Samsung (One UI), and Huawei / Honor (HarmonyOS / MagicOS), opening system App Info with tailored path hints and zero extra permissions.
- Skipping accelerometer registration when "Pause when face down" is turned off avoids wasted 300 ms face-down hold waits during overhead reading.
- Local settings backup (export and import) now includes both "Ignore limit while charging" and "Pause when face down" while remaining backward-compatible with older backup files.

### Fixed

- Tightened battery charging detection so a residual `BATTERY_STATUS_FULL` state at the moment an unplugged phone hits 100% no longer triggers a false charging exemption.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness. On Android 13 and later, if the Accessibility toggle is grayed out with "Restricted setting", open App Info, tap the top-right menu to allow restricted settings, then return to Accessibility settings.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose an app scope and select whitelist/blacklist apps in Settings (or restore a backup via "Settings → Import settings"), then turn on the main toggle (or use the Quick Settings tile).
- Background battery policy: on Xiaomi / HyperOS, tap "Background operation" in Settings and choose "No restrictions" (the system force-stops GazeKeep when changing this policy; reopen the app once afterward). On OPPO / OnePlus / realme, vivo / iQOO, Samsung, and Huawei / Honor, tap "Background operation" to open App Info and allow unrestricted background usage per the on-screen hint.

### Tuning Tips

| Setting                     | Default                      | Recommendation                                                                                                       |
| :-------------------------- | :--------------------------- | :------------------------------------------------------------------------------------------------------------------- |
| Detection lead time         | 8 s                          | Choose 5 s, 8 s, or custom 0–60 s. Increase on devices with slower camera startup or quick dimming                   |
| Sensitivity                 | Standard: forward, eyes open | Standard requires facing forward with eyes open; switch to Relaxed if reflective glasses make eye detection unstable |
| Maximum keep-awake time     | 30 min                       | Recommended as a safeguard if you fall asleep; customizable or set to unlimited                                      |
| Ignore limit while charging | Off                          | Enable for long plugged-in desktop reading; unplugging immediately restores the cap and looking away still sleeps    |
| App scope                   | Whitelist only               | Best battery life by checking only selected apps; choose "All apps" for global use (camera apps still skipped)       |
| Pause on Battery Saver      | On                           | Automatically yields camera and keep-awake while system Battery Saver is active                                      |
| Low battery threshold       | 15% or below                 | Pauses keep-awake while unplugged at or below the threshold; resumes automatically when charging                     |
| Pause when face down        | On                           | Keep on for normal use; turn off in sufficient light if you often read lying flat holding the phone overhead         |
| Detailed logs               | Off                          | Enable only while troubleshooting; keeps the last 24 hours and clears immediately when turned off                    |

# v1.1.7

## Changelog

### Added

- Zero-permission local settings export and import: export your preferences and whitelist/blacklist app lists to a local JSON backup or restore them via the system file picker (SAF) for effortless reinstall or device migration, with zero storage or network permissions. Imported files are validated and sanitized (capped at 500 packages per list and 512 KB per file) with an overwrite confirmation when existing lists are non-empty; backups never export or restore the main switch state, self-test completion record, or Shizuku timeout recovery state.

### Improved

- Steady-state single-frame fast path (cont. #14): after two consecutive `Looking` rounds, if the first frame already confirms gaze, GazeKeep finishes immediately and releases the front camera. Any non-`Looking` first frame (such as a quick blink or momentary miss) automatically falls back to the full 3-frame 2-vote rule instead of misclassifying as `Away` under the old 2-of-2 steady-state cap, while sustained closed eyes or head turns still let the screen sleep normally.

  Alternating steady-state comparison between builds (Xiaomi 12 Pro, HyperOS 2 / Android 14, LSPosed backend, `640×480`, ML Kit `FAST` + `ALL`, tested in A-B-B-A order at `15 s` timeout and fixed `120/255` manual brightness):

  | Steady-state metric                    | v1.1.6 (2-frame)      | v1.1.7 (single-frame fast path) | Delta                       |
  | -------------------------------------- | --------------------- | ------------------------------- | --------------------------- |
  | Camera active time / round (`elapsed`) | 604 ms                | **454 ms**                      | **−25%** (~150 ms shorter)  |
  | ML Kit classification / round          | 2 frames (79 ms)      | **1 frame (42 ms)**             | **−50% frames / −48% time** |
  | App CPU time (47 s / 7 rounds)         | 5.05 s                | **3.60 s**                      | **−29%**                    |
  | Single-frame blink / miss tolerance    | 2-of-2 prone to sleep | **Falls back to 2-of-3**        | Fewer false screen-offs     |

  Savings primarily reduce app inference CPU time and camera-on duration per round; total device battery power remains dominated by display and system background variance and varies by device and timeout settings.

### Fixed

- Fixed backend switching races: serialized the backend switch queue and commit the new state only after transition completes, quiescing the previous detection round and cancelling in-flight extensions before releasing the old backend; serialized Shizuku `UserService` unbind/rebind under lock isolation.
- Fixed alert notification freshness and rate-limit boundaries: automatically dismisses stale failure alerts once keep-awake recovers or the service stops, while preserving the 10-minute per-alert rate-limit window across alert dismissal and service reconfiguration.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness. On Android 13 and later, if the Accessibility toggle is grayed out with "Restricted setting", open App Info, tap the top-right menu to allow restricted settings, then return to Accessibility settings.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose an app scope and select whitelist/blacklist apps in Settings (or restore a backup via "Settings → Import settings"), then turn on the main toggle (or use the Quick Settings tile).
- Xiaomi / HyperOS: Set the battery saver policy to "No restrictions" under Background operation in Settings. The system force-stops GazeKeep when changing this policy; reopen the app once afterward.

### Tuning Tips

| Setting                     | Default                      | Recommendation                                                                                                       |
| :-------------------------- | :--------------------------- | :------------------------------------------------------------------------------------------------------------------- |
| Detection lead time         | 8 s                          | Choose 5 s, 8 s, or custom 0–60 s. Increase on devices with slower camera startup or quick dimming                   |
| Sensitivity                 | Standard: forward, eyes open | Standard requires facing forward with eyes open; switch to Relaxed if reflective glasses make eye detection unstable |
| Maximum keep-awake time     | 30 min                       | Recommended as a safeguard if you fall asleep; customizable or set to unlimited                                      |
| App scope                   | Whitelist only               | Best battery life by checking only selected apps; choose "All apps" for global use (camera apps still skipped)       |
| Pause in battery saver      | On                           | Automatically yields camera and keep-awake while system Battery Saver is active                                      |
| Low battery pause threshold | Below 15%                    | Pauses keep-awake while unplugged at or below the threshold; resumes automatically when charging                     |
| Detailed logs               | Off                          | Enable only while troubleshooting; keeps the last 24 hours and clears immediately when turned off                    |

# v1.1.6

## Changelog

### Added

- System Quick Settings tile: add the "GazeKeep" tile to Quick Settings to pause or resume with one tap. Tapping while active stops the service; tapping while paused resumes it (on Android 14 and later, briefly launches the app to satisfy foreground camera restrictions). Automatically guides you to any missing permission, self-test, or backend setup step.
- Power gating settings:
  - **Pause in battery saver** (on by default): skips detection and follows system sleep when Battery Saver is enabled.
  - **Low battery pause threshold** (default: below 15% while unplugged; options: Off or 5%–20%): pauses detection and keep-awake when unplugged and at or below the threshold.
- Enhanced log viewer: supports live streaming of new entries; pauses auto-scroll when you scroll away from the top and shows an "N new logs" pill to jump to the latest entry; supports tapping or long-pressing a log card to copy text; adds a confirmation dialog showing the entry count before "Clear logs now".

### Improved

- Home and setup guidance loop: the Home status card displays the latest detection or skip/pause reason; selecting "Auto" with no ready backend links directly to the Backend screen; selecting "Whitelist only" with an empty list provides a shortcut straight to the App list.
- Log filter interaction and typography: opening the top filter or "More options" menu while the search field is focused now dismisses the keyboard first so the IME candidate bar does not obscure the menu; also refined value label and dropdown layout at larger font sizes.
- Added system camera package names across more OEMs to automatically yield the camera when the system camera is open.

### Fixed

- Fixed the Shizuku backend missing updated system activity time after video players or other `ON_AFTER_RELEASE` wake locks release naturally, preventing false near-timeout triggers right after lock release while preserving accumulated keep-awake duration.
- Fixed Accessibility readiness state not refreshing immediately upon returning to the app after toggling the Accessibility service in system settings.
- Fixed switching the in-app language to `English` occasionally reverting to Chinese on cold start and similar lifecycle paths.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness. On Android 13 and later, if the Accessibility toggle is grayed out with "Restricted setting", open App Info, tap the top-right menu to allow restricted settings, then return to Accessibility settings.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose an app scope and select whitelist/blacklist apps in Settings, then turn on the main toggle (or use the Quick Settings tile).
- Xiaomi / HyperOS: Set the battery saver policy to "No restrictions" under Background operation in Settings. The system force-stops GazeKeep when changing this policy; reopen the app once afterward.

### Tuning Tips

| Setting                     | Default                      | Recommendation                                                                                                       |
| :-------------------------- | :--------------------------- | :------------------------------------------------------------------------------------------------------------------- |
| Detection lead time         | 8 s                          | Choose 5 s, 8 s, or custom 0–60 s. Increase on devices with slower camera startup or quick dimming                   |
| Sensitivity                 | Standard: forward, eyes open | Standard requires facing forward with eyes open; switch to Relaxed if reflective glasses make eye detection unstable |
| Maximum keep-awake time     | 30 min                       | Recommended as a safeguard if you fall asleep; customizable or set to unlimited                                      |
| App scope                   | Whitelist only               | Best battery life by checking only selected apps; choose "All apps" for global use (camera apps still skipped)       |
| Pause in battery saver      | On                           | Automatically yields camera and keep-awake while system Battery Saver is active                                      |
| Low battery pause threshold | Below 15%                    | Pauses keep-awake while unplugged at or below the threshold; resumes automatically when charging                     |
| Detailed logs               | Off                          | Enable only while troubleshooting; keeps the last 24 hours and clears immediately when turned off                    |

# v1.1.5

## Changelog

### Added

- Feedback entry points: added one-tap GitHub feedback links below the privacy card on Home and under "Settings → About", backed by user-friendly issue templates and a pinned feedback thread.

### Improved

- Lower battery usage: proximity, ambient light, and accelerometer sensors are no longer monitored continuously while the screen is on. They are sampled briefly (~1 second) only near timeout after all other checks pass, then closed immediately; pocket, face-down, and low-light checks continue to work as before.
- Lower battery usage: under the Shizuku backend, system power state polling is stopped while the screen is off and reduced when other apps (such as video players) hold their own wake locks.

  Alternating power comparison between builds (Xiaomi 12 Pro, HyperOS 2 / Android 14, Shizuku backend; tested in A-B-B-A order, reading battery current and voltage every second at fixed 24/255 manual brightness with wireless debugging connected):

  | Scenario (per round)           | Avg power 1.1.5 (2 rounds) | Avg power 1.1.4 (2 rounds) | Delta | App CPU 1.1.5 / 1.1.4 (avg) | Shizuku CPU 1.1.5 / 1.1.4 (avg) | Sensor on 1.1.5 / 1.1.4 |
  | ------------------------------ | -------------------------- | -------------------------- | ----- | --------------------------- | ------------------------------- | ----------------------- |
  | Looking 3 min, continuous gaze | 1624 / 1464 mW (avg 1544)  | 1650 / 1838 mW (avg 1744)  | ~−11% | 4.7 s / 8.4 s               | 1.2 s / 1.1 s                   | 7 s / 452 s             |
  | Video wake lock 2 min          | 1821 / 1961 mW (avg 1891)  | 2059 mW (1 valid round)    | ~−8%  | 1.4 s / 4.4 s               | 0.8 s / 3.6 s                   | 1 s / 245 s             |
  | Screen off 4 min               | 691 / 661 mW (avg 676)     | 874 / 812 mW (avg 843)     | ~−20% | 1.4 s / 2.7 s               | 0.1 s / 7.9 s                   | 0 s / 7 s               |

  Across all three scenarios, average power in 1.1.5 is lower by ~11%, ~8%, and ~20%, with every round of 1.1.5 outperforming every round of 1.1.4. One 1.1.4 video round was excluded because the player did not keep the screen on. Each group ran only 2 rounds and wireless debugging increases screen-off draw; figures vary by device, backend, and environment.

- Removed the duplicate "Privacy" entry from Settings; privacy commitments remain accessible from the Home privacy card and "About".

### Fixed

- In persistently dark environments, "Pause in low light" previously reset its counter on every touch or screen wake and rarely triggered. Two consecutive dark uncertain checks under low ambient light now pause camera sampling until ambient light recovers.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness. On Android 13 and later, if the Accessibility toggle is grayed out with "Restricted setting", open App Info, tap the top-right menu to allow restricted settings, then return to Accessibility settings.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose an app scope and select whitelist/blacklist apps in Settings, then turn on the main toggle.
- Xiaomi / HyperOS: Set the battery saver policy to "No restrictions" under Background operation in Settings. The system force-stops GazeKeep when changing this policy; reopen the app once afterward.

### Tuning Tips

| Setting                 | Default        | Recommendation                                                                                                 |
| :---------------------- | :------------- | :------------------------------------------------------------------------------------------------------------- |
| Detection lead time     | 8 s            | Choose 5 s, 8 s, or custom 0–60 s. Increase on devices with slower camera startup or quick dimming             |
| Sensitivity             | Standard       | Standard requires facing forward with eyes open; switch to Relaxed if glasses glare causes missed blinks       |
| Maximum keep-awake time | 30 min         | Recommended as a safeguard if you fall asleep; customizable or set to unlimited                                |
| App scope               | Whitelist only | Best battery life by checking only selected apps; choose "All apps" for global use (camera apps still skipped) |
| Detailed logs           | Off            | Enable only while troubleshooting; keeps the last 24 hours and clears immediately when turned off              |

# v1.1.4

## Changelog

### Added

- Blacklist app scope mode: "App scope" in Settings is now a dropdown switching between "All apps", "Whitelist only", and "Blacklist". Blacklist mode suits global keep-awake while excluding video, navigation, or similar apps. Whitelist and blacklist selections are saved separately without overwriting each other. Just like whitelist mode, GazeKeep lets the screen sleep if the foreground app cannot be confirmed, and all three modes skip system camera apps when recognized.
- "More options" menu in App list:
  - Added "Show system apps" toggle (hidden by default and persisted across restarts). Preinstalled apps updated via app stores appear as regular apps, and already-selected or recently toggled system apps remain visible even when hidden. When search matches only hidden system apps, a hint displays the count with a one-tap reveal button.
  - Added "Select search results" to add all currently visible matches to the list at once.
  - Added "Clear selection" when the list is non-empty, with a confirmation dialog explaining the impact for whitelist or blacklist mode.

### Improved

- Seamless upgrade migration: enabling whitelist in older versions maps to "Whitelist only" with your list intact; disabling whitelist maps to "All apps".

### Fixed

- Under the Accessibility backend, overlays, popups, or soft keyboards could cause the foreground app to be misidentified and break app scope filtering (such as keeping blacklisted apps awake); only confirmed application window transitions now update the foreground app.
- Fixed singular/plural phrasing issues in English counters such as "1 apps selected" and "1 seconds".

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness. On Android 13 and later, if the Accessibility toggle is grayed out with "Restricted setting", open App Info, tap the top-right menu to allow restricted settings, then return to Accessibility settings.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose an app scope and select whitelist/blacklist apps in Settings, then turn on the main toggle.
- Xiaomi / HyperOS: Set the battery saver policy to "No restrictions" under Background operation in Settings. The system force-stops GazeKeep when changing this policy; reopen the app once afterward.

# v1.1.3

## Changelog

### Added

- In-app language switcher in Settings: choose between System default, 简体中文, and English (defaults to System). On Android 13 and later, this stays in sync with the system per-app language setting.
- Xiaomi / HyperOS background operation guide: adds a shortcut in Settings to open system app power-saving settings and switch from the default "Smart" policy (which freezes background services after camera usage) to "No restrictions".
- Automatic screen timeout restoration on boot: adds `RECEIVE_BOOT_COMPLETED` permission; runs once on boot only if the device rebooted while a temporary Shizuku timeout extension was pending restoration, writing back the original timeout without starting the detection service or camera.
- Official website entry on the About screen for quick access.

### Improved

- Automatic service recovery after re-granting camera permission: if a previously revoked camera permission is granted again while conditions are met, returning to the foreground resumes GazeKeep automatically without toggling the main switch.
- Detailed log viewer and performance: log entries and exported files are now ordered newest-first, and exports use the `.txt` extension for direct viewing on mobile. Logs now append incrementally so large histories are no longer lost or re-read on every write; Settings also notes that logs include foreground app package names.

### Fixed

- Fixed a potential crash when checking framework readiness concurrently under the LSPosed backend.
- Fixed the home screen still showing "Working" or inaccurate status messages after a backend disconnected or camera permission was revoked.
- Fixed the Shizuku backend failing to automatically reconnect after the Shizuku service exited or a binding attempt failed; it now retries on a backoff schedule.
- Fixed multiple issues in the Shizuku temporary-timeout fallback: prevented sleeping after ~60 s of continuous gaze, avoided stale restoration or delayed countdown updates causing sudden sleep, stopped overwriting manual user changes to system screen timeout during extension, and ensured timely restoration at the protection limit or when switching backends.
- Fixed unnecessary detection loops and battery drain when the screen woke on the lock screen due to minor deadline drift.
- Fixed detailed log timestamps recording write time instead of actual event time when queued.
- Fixed missing cancellation reasons in detailed logs when face capture was interrupted by screen touch.
- Fixed engine state names in detailed logs and debug output being obfuscated (e.g. `zn0`) in R8 release builds; keep rules now preserve readable state names.
- Fixed persistent notification and notification channel names not updating immediately when switching the in-app language while the service is running.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose whitelisted apps, and turn on the main toggle.
- Xiaomi / HyperOS: Set the battery saver policy to "No restrictions" under Background operation in Settings. The system force-stops GazeKeep when changing this policy; reopen the app once afterward.

# v1.1.2

## Changelog

### Signing Change

- Starting from this version, the official release signing key is used. Devices with earlier development-signed builds (`1.0.0`, `1.1.0`, or `1.1.2` candidates) have an incompatible signature and cannot upgrade in place; uninstall the old build first before installing. Uninstalling clears GazeKeep settings, so permissions and self-test must be completed again after installation. Future releases use the same signing key for in-place upgrades.

### Added

- Split APKs by CPU architecture: `arm64-v8a`, `armeabi-v7a`, and `x86_64`, plus a `universal` APK. Most phones should choose `arm64-v8a`; choose `universal` if unsure. All variants share the same version and signing key and can be installed over one another.

### Improved

- Smaller APK size: native libraries are stored compressed and 32-bit x86 libraries are removed. Per-ABI APKs shrink from ~41 MB to ~10–11 MB, and the universal APK is ~17 MB.
- Camera capture ownership and cancellation cleanup: handoffs between self-test and the background service go through a shared capture coordinator so the next round does not start until cleanup drains.
- Self-test retains completed steps when returning from the background and supports manual retry.

### Fixed

- After canceling capture or self-test, late detection results and backend success callbacks no longer update state or feed into keep-awake decisions; results from an old self-test session do not affect a new session.
- Release builds no longer crash on startup due to R8 stripping parameterless constructors of ML Kit registrars; keep rules have been added.
- Sticky foreground service recovery no longer crashes if camera permission is revoked.
- Release builds no longer strip modern LSPosed entry callbacks; named interceptors protected by keep rules are now used.

## Setup

- Root environment: Enable the module in LSPosed, select the System Framework in scope, reboot, then return to the app to confirm the bridge is ready.
- Non-root environment: Enable the GazeKeep service in system Accessibility settings, or start Shizuku and grant GazeKeep permission, then return to the app to confirm backend readiness.
- For both environments: grant camera permission, complete the self-test (looking forward shows "Looking at screen"; looking away shows "Not looking"), choose whitelisted apps, and turn on the main toggle.
