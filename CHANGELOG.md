[v0.5.0] - 2026-07-02
-
**Light meter overhaul**
- **Priority modes**: shutter-priority (fix shutter → solve aperture, as before), new **aperture-priority** (fix aperture → solve shutter, snapped to the standard scale with the exact time as footnote), and a full **EV table** mode listing every equivalent shutter/aperture pair — tap a row to select it.
- **Tap-to-meter**: in Spot / Center-Weighted mode, tap the viewfinder to place the AE metering region on the subject; double-tap to recenter. The reticle follows the tap.
- **Zone System placement**: in Spot mode, place the metered area on Zones II–VIII (e.g. Zone III shadow → −2 stops exposure). Mark shadow + highlight readings to see the scene contrast range in stops with an N/N±1 development hint.
- **Reciprocity failure correction**: when the shutter goes to 1 s or longer, a corrected exposure time is shown for a selectable film (Schwarzschild exponents for HP5+, Tri-X, T-Max, Delta, Fomapan, Acros, Portra and more, plus generic fallbacks).
- **Low-light honesty**: below the phone sensor's reliable range the meter now says so instead of showing confident nonsense.
- Meter readings, EV lock and zone marks now survive tab switches (state hoisted to the ViewModel); the exposure math moved into a pure, testable `Exposure` utility.

**Shooting quality-of-life**
- **Quick-log (+1)**: a one-tap frame counter on roll cards and the roll detail screen — logs a shot instantly with the last shot's exposure, current time, cached weather, and fills GPS asynchronously (if permission is already granted). Edit the details later.
- **DOF / hyperfocal calculator** on every lens card: aperture, focus distance and format in → near/far limits, total depth of field and hyperfocal distance out (format-correct circles of confusion, 35mm through 4×5).
- **Agitation cues**: while a darkroom timer runs, a short vibration marks each minute (classic "agitate 10 s per minute" rhythm). Toggle on the timer screen.

**Roll exports**
- **Per-roll CSV** (film/camera/dev header + one row per frame) and a **printable PDF contact sheet** (thumbnails + exposure data, A4, paginated) — from the Export button on each roll's shot log. Archive it with your negatives.
- **All-rolls CSV** export from the Backup screen for spreadsheet analysis.

**Reminders (opt-in, Settings)**
- Daily check with notifications for: film expiring within 60 days (or expired), finished rolls sitting undeveloped for 3+ weeks (latent image fades), and chemistry mixed 60+ days ago.
- Per-category toggles; per-item anti-nag (re-notified at most every 2 weeks); requests the notification permission on Android 13+.
- New dependencies: WorkManager + Hilt work integration.



[v0.4.1] - 2026-07-02
-
**Bug fixes**
- **Push/pull direction was inverted**
    - Shooting film above box speed (e.g. ISO 400 film at EI 800) was labelled "pull"; it is a push. Fixed in the load sheet and roll detail tags.
- **GPS and costs broken on comma-decimal locales**
    - GPS coordinates were formatted with the device locale ("40,416775, -3,703790"), which the shot map could not parse. Coordinates are now locale-invariant.
    - Editing a film/bulk/dev/scan cost on those locales pre-filled "12,50", which silently reset to 0 on save. Cost fields now format and parse locale-safely.
- **Shot photos could be silently deleted by the OS**
    - Photos were stored in the app cache, which Android purges under storage pressure. They now live in permanent app storage; existing photos are migrated (and paths rewritten) on first launch.
- **Backup fixes**
    - Export/import no longer runs file I/O on the main thread (could freeze the UI / ANR on large backups).
    - ZIP import now streams the archive instead of loading it fully into memory (OOM risk on photo-heavy backups).
    - Malformed or hand-edited backup files no longer crash mid-restore — missing fields are sanitised and corrupt records skipped.
- **Camera lifecycle**
    - The camera is now released when leaving the Meter tab or closing the photo-capture dialog (it previously kept running — privacy indicator on, battery draining).
    - A failed photo capture no longer crashes the app.
- **Darkroom timer rework**
    - The countdown is now wall-clock based (it used to drift and stall when the screen slept — ruinous for development timing).
    - A running timer survives switching tabs, the screen stays on while it runs, and the phone vibrates when a step completes.
- **Bulk film footage math was ~4× off**
    - "Frames per foot/metre" now uses realistic yields (≈6.5 frames/ft, ≈21/m — 100 ft ≈ 18×36exp rolls).
- **Wind speed in imperial mode**
    - Was labelled mph but showed the raw m/s value; now converted.

**Light meter improvements**
- Calibration offset now moves in **1/3-stop increments** (classic camera convention, was 1/5) and is displayed camera-style (+1⅓); the manual EV slider uses 1/3 steps too.
- Calibration offset, ISO, shutter and metering mode are now **persisted** — the comment said "persists" but they reset on every tab switch.
- The metering mode selector now actually applies an AE region for Spot / Center-Weighted (it previously only changed the overlay graphics); falls back to full-frame on unsupported devices.
- Suggested aperture snaps to the nearest standard third-stop f-number (what a lens ring can be set to), with the exact value shown as a footnote. Equivalent-exposure table snaps too.
- Live readings are throttled (~4 Hz) instead of recomposing the screen on every camera frame.
- Camera permission is no longer re-requested on every tab visit when already granted.

**Improvements**
- Weather screen and dashboard snapshot now respect the metric/imperial setting (temperature, wind, visibility).
- Shot photos are downscaled to real thumbnails (max 1280 px, EXIF rotation applied) — much smaller storage and backups.
- Films whose last roll was loaded are no longer invisible — a collapsible "Out of stock" section allows restocking or deleting them.
- New Settings toggle to disable the forced 120 Hz refresh rate (battery).
- Backup rules added (`dataExtractionRules`/`fullBackupContent`): user data is deliberately included in device backups, device-specific map prefs excluded.
- Room schema export enabled (schemas land in `app/schemas` on build) to de-risk future migrations.

**Removed / cleanup**
- Removed unused FileProvider declaration and `file_paths.xml`.
- Removed unused `navigation-compose` dependency (navigation is hand-rolled); `exifinterface` is now actually used (thumbnail rotation).
- Removed leftover debug `usesCleartextTraffic` manifest flag.
- Removed duplicate camera entries in the autocomplete database and a duplicate metadata key.
- Merged duplicate GPS helper functions; removed an unused variable in Stats.
- Backup screen copy no longer describes the old JSON-only format ("photos not included" — they are, when toggled).



[v0.4.0] - 2026-06-18
-
**Code rework and fixes**
- **Currency missing in Stats**
    - Fixed Stats tab not passing the user's selected currency.
    - Cost Breakdown now correctly respects the app currency setting instead of always displaying `€`.
    - Removed unreachable tab logic.

- **Progress calculations using legacy shot count**
    - Fixed progress tracking using the deprecated `shots` field instead of `frameCount`.
    - Updated:
        - Dashboard
        - Roll list cards
        - Roll details

- **Incorrect film format detection for shot options**
    - Replaced fragile shot-count string matching with proper `filmFormat` detection.
    - Correct handling added for:
        - 120
        - 220
        - 4×5
        - 110
        - 126
        - 135

- **Weather data race condition**
    - Fixed weather occasionally appearing empty on slower connections.
    - Weather loading now waits for the actual fetch result instead of relying on a fixed delay.


- **Removed**
  - Removed development HTTP logger that sent debug logs to local development endpoints.
  - Removed unused `LocalSetMapScreenActive` CompositionLocal left from removed navigation code.
  - Removed unused hardcoded currency symbol.
  - Removed unused "future use" code and related imports.
  - Removed unused GPS wrapper function.


- **Maintenance**
  - Fixed deprecated Material 3 `Divider` usage.
  - Replaced deprecated tab indicator implementation with the current approach.
  - Removed duplicate `SectionCard` component and switched to the shared component.
  - Limited OkHttp logging to debug builds only.
  - Simplified redundant cost filtering logic.
  - Extracted duplicated weather formatting code into a reusable function.
  - Corrected misleading comments around bottom sheet dismissal behavior.


- **UI Improvements**
  - Added missing borders to roll cards for visual consistency with other app surfaces.
  - Improved card styling consistency across the app.


- **Versioning**
  - Versioning now works and is not stuck at 1.0



**Feature work and further fixes**
- **Build / compilation**
    - Fixed an invalid Kotlin compiler flag and an incorrect `@Suppress` that broke the build.
    - Removed the no-op `ExperimentalCamera2Interop` opt-in (not an opt-in marker in CameraX 1.3.x) and unused CameraX interop imports.

- **Loading film into camera**
    - Unified the stash and Loaded-tab load flows into a single consistent sheet.
    - Load Date is now a date picker instead of free text.
    - Removed the manual exposure count — taken from the film's own frame count.
    - "Shoot at ISO" is now available when loading from stash.
    - Custom ISO values are now selectable (and can be added inline) in "Shoot at ISO".
    - Added an **Unload** button that returns a mistakenly-loaded roll to the stash.

- **Light meter "Use in Shot"**
    - Fixed the prefill being cleared before the shot sheet read it — shutter, aperture and ISO now carry over.
    - Aperture normalised to match the shot form's options (e.g. `f/8`, not `f/8.0`).
    - Fixed a stale meter reading re-opening the shot sheet when later opening the Loaded tab.

- **Costs**
    - Per-roll film cost now shown for bulk-cut rolls (canister price amortised per roll).
    - Fixed Stats cost total double-counting bulk canisters — now counts the uncut remainder.
    - Added a **Purchase Date** field to individual film stocks (previously bulk-only).

- **Date & time pickers**
    - Tap a day / month / year value to jump directly via a dropdown, not just the arrows.
    - Tap the time to open a classic analog clock.
    - Unified the expiry month/year picker to the same style.
    - Consistent year range across pickers (1950 → +30 years); shot log limited to the last 2 years.

- **Navigation**
    - Reworked into a clear hierarchy (Home → tabs → sub-screens); Back walks up toward Home.
    - System Back now closes an open roll before leaving the Loaded tab.
    - Tab transitions slide toward the side the target tab sits on (position-aware), instead of always one direction.
    - Subtle scale animation on the selected bottom-bar icon.

- **Units**
    - Metric / Imperial setting now applies to bulk film, showing canister length in `ft` or `m`.

- **Database**
    - Added migration to schema v7 for the new film purchase-date field.

- **Cleanup / warnings**
    - Set `isShrinkResources = true` on the minified release build (required alongside `isMinifyEnabled`).
    - Removed the dead EXIF reader path from the light meter (`ExifPanel`, `readExif`, unused state).
    - Removed unused variables, repository functions, imports, and a stale version-catalog alias.


**Known bugs**
-
- now I really don't know, make an issue if you find any