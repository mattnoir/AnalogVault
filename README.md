# Analog Vault

Android app for film photographers. It tracks film, cameras and lenses, logs shots per roll, meters light with the phone camera, and runs darkroom timers. Data is stored locally on the device and the app works offline, except for weather lookups.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Basic usage](#basic-usage)
- [Navigation](#navigation)
- [Optional configuration](#optional-configuration)
- [Permissions](#permissions)
- [Data storage and backup](#data-storage-and-backup)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

**Stash**
- Film stocks, bulk film, cameras, lenses and accessories
- Film fields: ISO, type, format, expiry, storage, quantity, purchase date, cost
- Formats: 135, 120, 220, 4x5, 8x10, Super 8, 110, 126, instant
- Camera mounts and lens adapters are tracked; only compatible lenses are offered for a camera
- Bulk film can be cut into rolls, with configurable buffer frames
- Depth-of-field and hyperfocal calculator on each lens

**Rolls**
- Load film into a camera, with optional shoot-at-ISO (push/pull)
- Per-shot log: shutter, aperture, ISO, lens, location, notes, weather, photo
- One-tap quick-log (+1) that reuses the last exposure
- Roll stages: In camera, To dev, To scan, Done
- Development log and scan log per roll
- Per-roll CSV export and printable PDF contact sheet

**Light meter**
- Live metering using the phone camera
- Aperture priority, shutter priority and manual modes
- Metering modes: matrix, centre-weighted, spot, highlight
- Zone System placement and scene contrast readout
- Reciprocity failure correction for exposures of 1 s or longer
- Viewfinder crop to the negative shape (3:2, 4:3, 5:4, 1:1, 16:9, XPan, 3:1)
- Black and white preview with contrast ruler
- Calibration offset in 1/3-stop steps
- Exposures outside the range of the selected camera or lens are marked on the scale

**Darkroom**
- Chemistry inventory with mix date, capacity and rolls used
- Development time increases per roll as chemistry is used
- Chemistry kits (for example C-41, E-6) with multiple baths
- Timers generated from chemistry and kits, plus custom timers with per-step agitation
- Developer times entered at 20 °C are scaled to the working temperature
- Optional vibration cue for agitation

**Other**
- Weather and golden-hour information (OpenWeatherMap)
- Statistics: roll and frame counts, cost breakdown, most-used film and camera, aperture and shutter histograms
- Shot map (OpenStreetMap)
- Reminders for expiring film, undeveloped rolls and old chemistry
- Safelight (red) colour scheme
- Metric and imperial units, configurable currency symbol
- Rearrangeable Home screen

## Installation

1. Download the latest `AnalogVault-vX.Y.Z.apk` from the [Releases](https://github.com/mattnoir/AnalogVault/releases) page.
2. Open the file on the device. If prompted, allow installation from the browser or file manager used.
3. Start Analog Vault.

Requires Android 8.0 (API 26) or later.

To build the app yourself, see [Building from source](#building-from-source).

## Basic usage

The steps below follow one roll from purchase to archive.

**1. Add film and a camera.** Open Stash. Use the Film tab to add a stock and the Cameras tab to add a camera. Names autocomplete from a built-in list. Lenses and accessories have their own tabs.

**2. Load the roll.** In Stash, select a film and choose Load roll. Select the camera. Optionally set Shoot at ISO. The frame count is taken from the film and the camera format. Unload returns the roll to the stash.

**3. Meter.** Press the round shutter button in the centre of the bottom bar to open the light meter. Select a mode:
- A: fix the aperture, the meter calculates the shutter speed
- S: fix the shutter speed, the meter calculates the aperture
- M: set both; a needle shows the offset from the metered exposure in stops

Use Log to record the frame with the selected exposure. When the camera preview is on, the viewfinder image is attached to the shot.

**4. Log shots.** Open Rolls and select the roll. Use +1 on the roll card to log a frame with the previous exposure, the current time, cached weather and GPS position (if permission is granted). Open the shot sheet to edit shutter, aperture, ISO, lens, location and notes.

**5. Finish and develop.** Mark the roll Finished, then record development (process, chemistry, cost). To time development, open More > Darkroom > Timers and start a timer.

**6. Scan.** Record the scan method and cost. The roll moves to Done.

**7. Export and back up.** From a roll's detail screen, export a CSV or PDF contact sheet. To back up the whole vault, open More > Backup and choose Export Backup.

## Navigation

```
Bottom bar:   Home | Stash | [Shutter -> Meter] | Rolls | More

More:         Darkroom      timers, chemistry, develop and scan logs
              Stats         numbers, habits, shot map
              Weather       forecast and golden hour
              Backup        export and import
              Home layout   reorder or hide Home sections
              Settings      units, currency, safelight, reminders, API key
```

| Screen | Contents |
|---|---|
| Home | Loaded roll, pipeline counts, weather, light conditions, nudges (latent image, expiring stock, low bulk film) |
| Stash | Tabs: Film, Cameras, Lenses, Accessories. Bulk film is listed on the Film tab. |
| Rolls | Filter chips: In camera, To dev, To scan, Done. Roll detail has shot log, contact sheet and map. |
| Meter | Full-screen light meter, opened from the shutter button. Back returns to the previous screen. |
| Darkroom | Tabs: Chemistry, Timers |
| Stats | Tabs: Numbers, Habits. Shot map opens from here or from a roll. |

Long-pressing the shutter button toggles Safelight.

## Optional configuration

**Weather.** Weather uses OpenWeatherMap. Create an API key at <https://openweathermap.org/api> (the free tier is sufficient), then enter it in More > Settings. Without a key, the rest of the app is unaffected.

**Reminders.** Disabled by default; enable them in More > Settings. A daily check can notify about:

| Condition | Threshold |
|---|---|
| Film expiring | Within 60 days, or already expired |
| Finished roll not developed | 3 weeks or more |
| Chemistry age | Mixed 60 days ago or more |

Each category has its own toggle. An item is not notified again for two weeks. On Android 13 and later the app requests notification permission when reminders are enabled.

## Permissions

| Permission | Used for |
|---|---|
| Camera | Light meter, attaching photos to shots |
| Location (fine, coarse) | GPS on shots, shot map, local weather |
| Photos / media | Selecting an existing photo for a shot |
| Notifications | Reminders |
| Vibrate | Darkroom timer cues |
| Internet | Weather requests, map tiles |

A camera is not required to install the app.

## Data storage and backup

- Data is stored in a local Room (SQLite) database. There are no accounts.
- Network requests are made to OpenWeatherMap (only when a key is set) and to OpenStreetMap for map tiles.
- Backups are `.avault` files: a ZIP containing version-tagged JSON and, optionally, shot photos. The OpenWeatherMap key is included.
- Import is additive: records with the same ID are overwritten, other records are merged in, and nothing is deleted. Older plain `.json` exports can also be imported.
- To move to another device, install the app and import the backup file.
- "Export All Shot Logs" in Backup writes all shots from all rolls to one CSV.
- User data is included in Android device backups; device-specific map preferences are excluded.

## Building from source

Requirements: Android Studio (recent stable), JDK 17, Android SDK 35.

```bash
git clone https://github.com/mattnoir/AnalogVault.git
cd AnalogVault
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install on a connected device or emulator:

```bash
./gradlew installDebug
```

On Windows, use `gradlew.bat`. The project can also be opened and run directly from Android Studio.

Debug builds use the application ID `com.analogvault.debug` and the label "Analog Vault ·debug", so they can be installed alongside the release app.

**Release build.** Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, then run `./gradlew assembleRelease`. If `KEYSTORE_PATH` is not set, the release build is signed with the debug key. Pushing a tag matching `v*` runs the GitHub Actions workflow, which builds the APK and creates a release with notes taken from `CHANGELOG.md`.

**Stack:** Kotlin (JVM 17), Jetpack Compose with Material 3, Hilt, Room, CameraX, Retrofit with Gson and OkHttp, Coil, WorkManager, Google Play Services Location, osmdroid.

**SDK levels:** minSdk 26, targetSdk 35, compileSdk 35.

## Project structure

```text
app/src/main/java/com/analogvault/
├── MainActivity.kt          Entry point, tab scaffold, shutter and meter navigation
├── AppModule.kt             Hilt providers
├── data/
│   ├── model/Models.kt      Room entities and type converters
│   ├── db/                  AppDatabase and DAOs (schemas exported to app/schemas)
│   ├── repo/                VaultRepository
│   ├── network/             OpenWeatherMap API
│   ├── backup/              .avault export and import
│   └── export/              Per-roll CSV and PDF contact sheet
├── ui/
│   ├── MainViewModel.kt     App state (BackupViewModel handles backups)
│   ├── screens/             Home, Stash, Rolls, Meter, Darkroom, Stats, Weather, Backup, Settings, Shot map
│   ├── film/                Film-themed UI components
│   ├── components/          Shared components and map wrapper
│   └── theme/               Colours and typography
├── util/                    Exposure maths, development time, gear limits, constants
└── work/ReminderWorker.kt   Daily reminder check
```

## Contributing

Report bugs and request features through [GitHub Issues](https://github.com/mattnoir/AnalogVault/issues). For code changes, see [CONTRIBUTING.md](./CONTRIBUTING.md) for branch names, commit and PR titles, versioning and the changelog. In summary:

1. Branch from `master` as `feature/…`, `fix/…` or `chore/…`.
2. Title commits and the PR `feat: …`, `fix: …` or `chore: …`.
3. Add an entry under `## [Unreleased]` in `CHANGELOG.md` for user-visible changes.
4. Run a debug build and test on a device. Include screenshots for UI changes.

Version history is in [CHANGELOG.md](./CHANGELOG.md).

The target architecture, the plan for getting there and the latest code review are in [docs/](./docs): [ARCHITECTURE.md](./docs/ARCHITECTURE.md), [REFACTOR_PLAN.md](./docs/REFACTOR_PLAN.md) and [CODE_REVIEW.md](./docs/CODE_REVIEW.md).

Bundled fonts (Big Shoulders Display, Space Grotesk, Space Mono) are licensed under the SIL Open Font License; the license texts are in `app/licenses/`.

## License

MIT. See [LICENSE](./LICENSE).
