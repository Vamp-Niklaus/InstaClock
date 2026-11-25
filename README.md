# InstaClock — Android Alarm Clock & Instagram Reels Wake-Up Mission

[![Build & Release](https://github.com/Vamp-Niklaus/InstaClock/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Vamp-Niklaus/InstaClock/actions/workflows/build-and-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://github.com/Vamp-Niklaus/InstaClock/releases/latest)

**InstaClock** is a minimalist, cheat-resistant **Android alarm clock app** inspired by Alarmy, built to actually wake you up and keep your daily Instagram usage in check. Instead of a snooze button you can ignore in your sleep, InstaClock requires completing a wake-up mission—like watching a custom number of **Instagram Reels**, solving **Math problems**, or **shaking your phone**—before the alarm turns off.

---

## Features

- **Wake-Up Missions**
  - **Instagram Reels Mission**: Launches the native Instagram app with a floating progress HUD (`AccessibilityService` + overlay) and a 5-second anti-cheat watch timer per reel. Customize any reel count (`1–100`).
  - **Math Mission**: Solve arithmetic problems to dismiss the alarm.
  - **Shake Mission**: Shake your phone 20 times using the accelerometer.
  - **Quick Alarm & Repeating Alarms**: Set one-tap quick timers (`+1m`, `+5m`, `+10m`, `+15m`, `+30m`, `+1h`) or recurring weekday/daily wake-up alarms.
- **Instagram Daily Limit & Morning-Only Lock**
  - Track daily foreground usage in Instagram (`15m`, `30m`, `45m`, `60m`) or lock Instagram immediately.
  - **Morning-Only Edit Protection**: Once a limit or lock is active, it can only be relaxed or turned off between **6:00 AM and 10:00 AM**.
- **Cheat Prevention**
  - **Prevent Power-Off**: Detects and blocks the power/shutdown menu while enabled.
  - **Prevent App Uninstall**: Device Administrator protection against accidental or sleepy uninstallation.
- **In-App Auto-Updater**
  - Checks [GitHub Releases](https://github.com/Vamp-Niklaus/InstaClock/releases/latest) automatically (prompts once per new version) or manually from **Setting → Update InstaClock**, downloading and installing updates directly inside the app.

---

## Download & Install

1. Go to the [**Latest Release**](https://github.com/Vamp-Niklaus/InstaClock/releases/latest).
2. Download `InstaClock-vX.Y.Z.apk`.
3. Open the APK on your Android device (Android 8.0 / API 26+) and allow installation.

---

## Permissions Used

| Permission | Purpose |
|---|---|
| Exact Alarms (`SCHEDULE_EXACT_ALARM`) | Fires alarms precisely on time, even in Doze mode |
| Accessibility Service | Tracks Reels watch progress, enforces daily Instagram limits, and blocks power-off menus |
| Display Over Other Apps | Shows the floating Reels HUD during wake-up missions |
| Device Admin *(optional)* | Prevents uninstalling InstaClock while protection is enabled |
| Install Packages | Allows in-app updates from GitHub Releases |

---

## Building from Source

```bash
git clone https://github.com/Vamp-Niklaus/InstaClock.git
cd InstaClock/android_app
gradle assembleDebug
```

For signed release builds, set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` environment variables and run `gradle assembleRelease`.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

**Keywords:** android alarm clock, alarmy alternative, instagram reels alarm, wake up mission alarm, anti-cheat alarm clock, prevent power off alarm, prevent uninstall alarm, instagram usage limiter, instagram app blocker, screen time control, open source android alarm
