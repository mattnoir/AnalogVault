# Analog Vault — Android

Kotlin + Jetpack Compose rewrite of the FilmTrackerApp React PWA.

## Stack

| Feature | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Local storage | Room (SQLite) |
| Camera / Light meter | CameraX |
| Weather | Retrofit → OpenWeatherMap API |
| DI | Hilt |
| Images | Coil |
| Location | Google Play Services FusedLocationProvider |

## Setup

### 1. Clone & open in Android Studio Hedgehog or newer

### 2. Google Maps (for shot map view)
- Get a Maps API key from Google Cloud Console
- Replace `YOUR_MAPS_API_KEY_HERE` in `AndroidManifest.xml`
- Or remove the `<meta-data>` entry and the maps-compose dependency if you don't need it

### 3. OpenWeatherMap
- Free key at https://openweathermap.org/api
- Enter it in the app's Weather tab at runtime — stored in Room, never hardcoded

### 4. Build
```bash
./gradlew assembleDebug
```

## Architecture

```
com.analogvault/
├── data/
│   ├── db/          # Room DAOs + AppDatabase
│   ├── model/       # Entity data classes + TypeConverters
│   ├── network/     # Retrofit WeatherApi
│   └── repo/        # VaultRepository (single source of truth)
├── ui/
│   ├── theme/       # Dark amber Material 3 ColorScheme
│   ├── components/  # Shared composables (VaultCard, VaultSheet, etc.)
│   ├── screens/
│   │   ├── StashScreen.kt     # Film / Camera / Lens / Accessory CRUD
│   │   ├── ActiveScreen.kt    # Roll list → Roll detail → Shot log (CameraX capture, GPS)
│   │   ├── DarkroomScreen.kt  # Chemical tracker + dev time calc
│   │   ├── MeterScreen.kt     # Live CameraX viewfinder EV meter
│   │   ├── WeatherScreen.kt   # OWM weather with photography note
│   │   └── StatsScreen.kt     # Charts + rankings
│   └── MainViewModel.kt       # All state, DB ops, weather fetch
├── AppModule.kt    # Hilt DI bindings
└── MainActivity.kt # Entry point + bottom nav
```

## Features (parity with original PWA)

- **Stash tab** — Film stocks (name autocomplete from 38-stock DB, ISO, expiry tracking with colour-coded status, storage type, quantity), Cameras (mount system, adapter compatibility matrix identical to JS version), Lenses (mount compatibility), Accessories
- **Active tab** — Load rolls, shot log with shutter/aperture/ISO/lens/GPS/weather/photo thumbnail (CameraX native capture or gallery picker), dev log, scan log, progress bar, roll lifecycle (Shooting → Finished → Developed → Scanned)
- **Darkroom tab** — Chemical tracker, roll count (auto from dev logs or manual override), exhaustion alert at 80%/100%, adjusted dev time calculation per roll
- **Meter tab** — CameraX live viewfinder with real ImageAnalysis EV calculation, four metering modes (Evaluative, Center-Weighted, Spot, Highlight-Weighted), ISO + shutter → aperture solver, nearby exposure table, manual EV slider, configurable zoom level shortcuts
- **Weather tab** — OWM current conditions, photography lighting note, API key stored locally
- **Stats tab** — Total rolls/shots/developed/shooting, bar chart by month, film stock ranking, camera ranking, dev process breakdown

## Colour palette

Exactly mirrors the original CSS variables:
- `--bg` `#0E0C0A` → `Bg`
- `--amber` `#D4935A` → `Amber`
- `--ab` `#F0B06A` → `AmberBright`
- `--green` `#7EC982` → `GreenOk`
- `--blue` `#6AB0D4` → `BlueInfo`
- `--red` `#C45050` → `RedErr`
