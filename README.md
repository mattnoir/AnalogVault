# Analog Vault (Android)

A film-photography companion app for Android built with **Kotlin + Jetpack Compose**.

Analog Vault helps you manage film stocks, gear, active rolls, darkroom chemistry, metering, weather notes, and backups in one offline-first app.

---

## Table of Contents

- [Overview](#overview)
- [Core Features](#core-features)
- [Screens and Workflow](#screens-and-workflow)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Data Model](#data-model)
- [Backup & Restore](#backup--restore)
- [Weather Integration](#weather-integration)
- [Permissions](#permissions)
- [Requirements](#requirements)
- [Quick Install (Recommended)](#quick-install-recommended)
- [Getting Started (For Developers)](#getting-started-for-developers)
- [Build & Run (Developer)](#build--run-developer)
- [Release Build / Signing](#release-build--signing)
- [Project Structure](#project-structure)
- [Roadmap / Known Issues](#roadmap--known-issues)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Analog Vault is designed for photographers who shoot film and want a practical mobile workflow:

- Track stash inventory (film, cameras, lenses, accessories)
- Load and manage active rolls
- Log shots with exposure + notes + optional location/photo
- Track chemistry and development capacity
- Use a built-in CameraX light meter
- Capture weather context for sessions
- Export/import your vault as JSON backup (optionally with photos)

The app uses a local Room database and works offline for core functionality.

---

## Core Features

### 1) Stash Management

- Film stocks with ISO, format/shot count, expiry, storage, quantity, notes
- Cameras with mount system + adapter compatibility fields
- Lenses with focal length / max aperture / mount
- Accessories inventory
- Filtering support for stash lists

### 2) Active Rolls

- Load film into camera
- Track roll lifecycle: **Shooting → Finished → Developed → Scanned**
- Shot logging per roll with:
    - shutter, aperture, ISO
    - lens
    - location
    - notes
    - weather text
    - optional thumbnail path from captured/selected image
- Development and scan logs attached to each roll

### 3) Darkroom

- Chemical inventory and usage tracking
- Auto/manual roll counts per chemical
- Exhaustion awareness (based on max-roll setup)
- Development-time adjustment fields (base time + per-roll adjustment)

### 4) Light Meter

- Live CameraX-based metering workflow
- Exposure helpers (ISO/shutter/aperture workflows)
- Zoom level presets stored in database

### 5) Weather

- OpenWeatherMap current-weather fetch flow
- API key is entered in-app and saved locally in settings
- Useful for contextual shooting notes

### 6) Statistics

- Summary metrics across rolls/shots and statuses
- Rankings / breakdown views for film and camera usage

### 7) Backup & Restore

- Export full vault data to JSON
- Import from JSON
- Optional photo embedding in backup as Base64 (size-limited per image)

---

## Screens and Workflow

Main navigation uses a bottom bar for high-frequency tabs plus a drawer for secondary tabs:

- **Bottom tabs:** Home, Rolls, Darkroom, Meter, Stash
- **Drawer / More:** Weather, Stats, Backup (and full tab list)

Typical usage flow:

1. Add stash items (film/camera/lens)
2. Load a roll
3. Log shots while shooting
4. Mark roll finished, then developed/scanned
5. Track chemistry usage in darkroom
6. Review stats and export backup periodically

---

## Tech Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Database:** Room (SQLite)
- **Camera / metering:** CameraX
- **Networking:** Retrofit + Gson + OkHttp logging
- **Images:** Coil
- **Location:** Google Play Services Fused Location
- **Map tiles:** osmdroid (OpenStreetMap)
- **Concurrency:** Kotlin Coroutines + Flow

---

## Architecture

The app follows a practical layered approach:

- **UI layer:** Compose screens/components
- **State layer:** `MainViewModel` + `BackupViewModel`
- **Data layer:** `VaultRepository`
- **Persistence/network:** Room DAOs + Retrofit API service

Dependency injection is centralized in `AppModule`.

---

## Data Model

Room entities include:

- `FilmStock`
- `Camera`
- `Lens`
- `Accessory`
- `Roll` (with embedded shot/dev/scan structures via converters)
- `Chemical`
- `BulkRoll`
- `ZoomLevel`
- `Setting`

JSON/Gson converters are used for list/object fields (e.g., roll shots and logs).

---

## Backup & Restore

Backup payload includes:

- films, cameras, lenses, accessories
- rolls, chemicals, zoom levels
- weather API key (`owm_key` setting)
- optional photo map (`path -> base64`)

Backup format is versioned (`version = 2` currently), with compatibility handling for older format versions.

---

## Weather Integration

The app consumes OpenWeatherMap via Retrofit.

- You provide your own OWM API key in-app
- Key is stored in local settings table
- Weather is used for context in photography logging

Get an API key: <https://openweathermap.org/api>

---

## Permissions

Declared permissions include:

- `INTERNET`
- `CAMERA`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `READ_MEDIA_IMAGES` (modern Android)
- `READ_EXTERNAL_STORAGE` (maxSdk 32)
- `WRITE_EXTERNAL_STORAGE` (maxSdk 29, for osmdroid tile cache compatibility)

Camera hardware is marked as optional so the app can still install on devices without a camera.

---

## Requirements

- **Android minSdk:** 26
- **targetSdk / compileSdk:** 35
- **JDK:** 17
- **Android Studio:** recent stable (Hedgehog+ recommended)

---

## Quick Install (Recommended)

The primary distribution channel for Analog Vault is the prebuilt **release APK** included with releases.

1. Download the latest release APK from the project’s GitHub Releases page.
2. On Android, allow install from your browser/file manager if prompted.
3. Open the downloaded `.apk` and install.

If you only want to use the app (not develop it), this is the recommended path.

---

## Getting Started (For Developers)

1. Clone the repository:

   ```bash
   git clone <your-fork-or-repo-url>
   cd AnalogVault
   ```

2. Open in Android Studio.

3. Let Gradle sync complete.

4. Run on emulator or device.

---

## Build & Run (Developer)

### Debug APK

```bash
./gradlew assembleDebug
```

Generated APK (typical path):

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on connected device

```bash
./gradlew installDebug
```

---

## Release Build / Signing

Release signing can be configured via environment variables:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Then run:

```bash
./gradlew assembleRelease
```

If `KEYSTORE_PATH` is not set, release build falls back to debug signing configuration in current setup.

---

## Project Structure

```text
com.analogvault/
├── MainActivity.kt               # App entry, nav shell, tab/drawer scaffold
├── AppModule.kt                  # Hilt module bindings/providers
├── data/
│   ├── model/Models.kt           # Room entities + converters
│   ├── db/                       # Database + DAOs
│   ├── repo/VaultRepository.kt   # Data access facade
│   ├── network/WeatherApi.kt     # Retrofit weather API
│   └── backup/BackupManager.kt   # Export/import implementation
└── ui/
    ├── screens/                  # Feature screens (stash, active, darkroom, etc.)
    ├── components/               # Shared Compose components + OSM map wrapper
    └── theme/                    # App theme/colors/typography
```

---

## Roadmap / Known Issues

See `CHANGELOG.md` for version history and known issues.

Current notes include ongoing smoothness/navigation tuning and feature expansion around formats and workflow details.

---

## Contributing

If you plan to contribute:

1. Fork + create a feature branch
2. Keep changes scoped and testable
3. Run a debug build before opening PR
4. Include screenshots for UI-visible changes when relevant

---

## License

This project is licensed under the terms in [`LICENSE`](./LICENSE).