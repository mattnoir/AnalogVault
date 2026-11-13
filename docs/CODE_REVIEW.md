# Code review: weak points, security, performance

A review of `master` at `90db310` (version 0.9.1, Room schema 14): every Kotlin
file under `data/`, `work/`, `util/` and the two view models was read in full,
along with the manifest, build files and release workflow. The seven large
screen files were read in the parts that touch storage, camera, location,
intents and state, and measured rather than read line by line.

Nothing here was changed in code. Findings carry an ID so the
[refactor plan](./REFACTOR_PLAN.md) can refer to them; the target design is in
[ARCHITECTURE.md](./ARCHITECTURE.md). The app was not built or run for this
review, so performance findings are from reading the code and should be
confirmed with a profiler before and after each fix.

## Contents

- [What is in good shape](#what-is-in-good-shape)
- [Security and privacy](#security-and-privacy)
- [Data integrity](#data-integrity)
- [Structural weak points](#structural-weak-points)
- [Performance](#performance)
- [Build, CI and dependencies](#build-ci-and-dependencies)

## What is in good shape

Worth saying first, because the refactor should keep these:

- **Pure logic is already separated and partly tested.** `Achievements`,
  `Rewards`, `BadgeShare`, `SunTimes`, `Exposure`, `GearClamp`, `DevTime` and
  `ApertureScale` have no Android imports.
- **Migrations are written by hand, never destructive on upgrade,** and schemas
  7–14 are exported.
- **Storage formats that outlive a build use literal keys** (`encodeTimers`,
  `AchievementRecords`), after R8 once renamed backup fields.
- **Import is defensive about Gson nulls** (`VaultBackup.sanitized()`), and ZIP
  entry names are stripped to a bare file name.
- **Blocking I/O is on `Dispatchers.IO`** in backup, export and photo code.
- **No trackers, no accounts, HTTPS only,** `PendingIntent`s are immutable, the
  `FileProvider` exposes one cache subfolder, and the activity itself is not
  exported.
- **Comments explain why**, including the bugs that shaped the code.

## Security and privacy

No finding lets another app or a remote party reach the vault. The realistic
attack surface is a **backup file the user is persuaded to import** and
**where the vault's contents end up** (cloud backup, shared files).

| ID | Severity | Finding |
|---|---|---|
| S1 | Medium | Imported photo paths are trusted |
| S2 | Medium | ZIP import has no size or count limits |
| S3 | Medium | OpenWeatherMap key and GPS history leave the device in plain text |
| S4 | Low | A release build silently falls back to the debug key |
| S5 | Low | Release workflow: unpinned third-party action with write access |
| S6 | Low | Three storage permissions are declared and never used |
| S7 | Low | CSV export does not neutralise formula cells |
| S8 | Low | ZIP backup accepts any version |
| S9 | Info | Debug builds log the API key |

### S1. Imported photo paths are trusted

`Shot.photoThumbPath` is an absolute path, stored in the database and written
into backups. On import (`BackupManager.importZip`, `importLegacyJson`) a path
is rewritten only when a photo of the same file name was in the archive;
otherwise the path from the file is kept as it is. From then on the app treats
it as its own:

- `BackupManager.export` zips `File(path)` into the next backup under
  `photos/<name>`.
- `FramePhoto` hands `File(path)` to Coil; `RollExporter.decodeThumb` decodes
  it.

A crafted `.avault` can therefore name any file the app can read — its own
database, `shared_prefs`, another photo — and have it copied into the next
backup the user exports and shares. The reach is limited to the app's own
sandbox, which is why this is Medium and not High, but the fix is cheap:

- Store a **file name relative to `photoDir`**, never a path
  (see [REFACTOR_PLAN.md](./REFACTOR_PLAN.md), step 1.1).
- On import, drop any path whose file is not inside `photoDir` after
  canonicalisation.
- On export, skip anything outside `photoDir`.

Absolute paths are also a correctness problem: they break when the vault is
restored under another Android user or work profile (`/data/user/10/…`), and
debug and release builds have different package directories.

### S2. ZIP import has no size or count limits

`importZip` reads `backup.json` with `zis.readBytes()` and copies every
`photos/*` entry to disk, with no cap on entry size, total size or entry count.

- A small archive that expands to gigabytes fills the device (zip bomb).
- A large `backup.json` throws `OutOfMemoryError`, which is an `Error`, so the
  `catch (e: Exception)` in `import` does not catch it and the app crashes.
- Photos are written **before** `backup.json` is parsed. A failed import leaves
  them behind as orphans, and a photo whose name matches an existing one
  overwrites it, which contradicts "import is additive, nothing is deleted".
- An entry named `photos/..` survives `File(name).name` as `..`; opening it for
  writing throws and aborts the import half way.

Fix: cap `backup.json` (say 64 MB) and each photo (say 20 MB) with a counting
stream, cap the entry count, reject names that are not `[A-Za-z0-9._-]+` with
an image extension, extract to a temporary directory and move the files into
place only after the JSON has parsed and validated.

`importLegacyJson` has the same memory problem from the other side: its comment
describes streaming, but the caller passes `buffered.reader().readText()`, so a
50 MB legacy backup is a 100 MB `String` before the `JsonReader` starts. Pass
the `Reader` through instead.

### S3. The API key and GPS history leave the device in plain text

- The OpenWeatherMap key is a row in the `settings` table. It is written into
  every `.avault` export (`owmKey`), which users are told to move between
  devices and may well share when asking for help.
- `android:allowBackup="true"` with rules that exclude only the osmdroid
  preferences: the database (key included), every shot's GPS coordinates and
  every photo go to Google's cloud backup and device-to-device transfer.

This is documented in the README, and for a personal logbook it is a defensible
default. Recommended changes:

- Leave the key out of exports by default; offer "include API key" beside
  "include photos". Never restore over a key the user has already set.
- Move the key to its own preferences file excluded from `cloud-backup` in
  `data_extraction_rules.xml` (keep it in `device-transfer`).
- Optionally offer a passphrase for `.avault` files (AES-GCM over the ZIP).
- Say in the Backup screen that exports contain shot locations.

### S4. A release build silently falls back to the debug key

`app/build.gradle.kts` signs `release` with the debug keystore when
`KEYSTORE_PATH` is unset. A locally built "release" APK installs, looks right,
and can never be updated by a properly signed one — the user must uninstall and
lose the vault. Fail the build instead, or produce an explicitly unsigned APK,
unless a `-PallowDebugSigning` flag is passed.

### S5. Release workflow

`.github/workflows/release.yml`:

- `softprops/action-gh-release@v2` and the `actions/*` steps are pinned to
  moving tags. The job has `contents: write` and the signing secrets in its
  environment; pin every action to a commit SHA.
- `workflow_dispatch` lets anyone with write access build a release-signed APK
  from any branch and download it as an artifact. Restrict it to tags, or put
  the signing secrets behind a protected environment with required reviewers.
- The decoded keystore stays in `$RUNNER_TEMP`; add an `if: always()` cleanup.
- The Gradle wrapper jar is committed and never verified; add
  `gradle/actions/wrapper-validation`.
- There is no workflow for pull requests: the 7 test classes and Android lint
  never run in CI (see B1).

### S6. Unused storage permissions

`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are
in the manifest; nothing in the code requests or needs them. Photos are picked
with `GetContent`, which needs no permission, and osmdroid 6.1.x caches under
app-specific storage. Remove all three (verify the tile cache on an API 26–28
device first). Fewer permissions is also one less question at Play review.

### S7. CSV formula cells

`csvEscape` implements RFC 4180 quoting but does not guard cells that start
with `=`, `+`, `-` or `@`. Notes and locations are free text and can arrive
from an imported backup; opened in a spreadsheet they are evaluated as
formulas. Prefix such cells with `'`. Take care not to mangle legitimate
values such as a `-3.70…` longitude: apply the guard to text columns only.

### S8. ZIP backups accept any version

`importLegacyJson` rejects `version !in 0..3`; `importZip` checks nothing. A
backup from a future version with a changed format is merged as if it were v3.
Check the version in both, and refuse newer ones with a message that says to
update the app.

### S9. Debug logging

`HttpLoggingInterceptor` at `BASIC` prints the request URL, and OpenWeatherMap
takes the key as the `appid` query parameter. Debug builds only, so
informational: use `redactQueryParams("appid")` (OkHttp 5) or a small
interceptor that strips it.

## Data integrity

These are the findings most likely to lose something a user logged.

### D1. Shots are a JSON column, updated by read-modify-write

A roll's frames are a Gson-encoded `List<Shot>` in `rolls.shots`. Every change
to one frame rewrites the whole roll, and most writers start from a cached
copy:

```kotlin
fun addShot(rollId: String, shot: Shot) = viewModelScope.launch {
    val roll = rolls.value.find { it.id == rollId } ?: return@launch   // StateFlow snapshot
    repo.upsertRoll(roll.copy(shots = roll.shots + shot))
}
```

`rolls.value` lags the database by one emission. Two writes in quick
succession — a +1 tap while `quickLogShot`'s GPS fix is about to land, a shot
edit while `refitRollFrames` runs, `markFinished` straight after a log — start
from the same roll and the second silently drops the first. `quickLogShot`
re-reads before its GPS write, which narrows the window but does not close it:
nothing serialises the read and the write. The same pattern is in
`updateShot`, `deleteShot`, `markFinished`, `markDeveloped`, `markScanned`,
`setChemicalRolls` and `unloadRoll`.

Short term: a `@Transaction` DAO method per operation that reads the row and
writes it inside one transaction. Long term: a `shots` table (plan step 5.1).

### D2. Multi-row operations are not transactions

- `restoreData` upserts hundreds of rows one at a time. A failure half way
  leaves a partial restore, and the user is told only "Import failed".
- `cutFromBulk` inserts N film rows and then updates the canister.
- `unloadRoll` returns the film to the stash and then deletes the roll.

Wrap each in `db.withTransaction { }`.

### D3. A database downgrade wipes the vault

`fallbackToDestructiveMigrationOnDowngrade()` deletes everything if the app
ever opens a database newer than it understands. Android blocks installing a
lower `versionCode` over a higher one, but a device transfer or cloud restore
onto a phone with an older build, or a side-loaded APK after a reinstall, gets
there. For an app whose whole value is the user's data, fail loudly instead:
catch the open failure, show "this vault was made by a newer version — update
the app", and leave the file alone.

### D4. Photo files are never deleted

Deleting a shot, deleting a roll, replacing a shot's photo, cancelling the shot
sheet after taking a picture, and a failed import all leave files in
`files/camera_photos` for ever. They also ride along in Android's cloud backup,
which has a 25 MB quota per app — once orphans push the app over it, backups
stop silently. Add a sweep that deletes files no shot references (on a worker,
and never files younger than a day, so a photo taken for a sheet still open is
safe).

### D5. No referential integrity

No foreign keys and no indices. Deleting a film or camera leaves its rolls
pointing at nothing (shown as "Unknown"), and a shot's lens is the lens *name*
copied as text, so renaming a lens orphans its history. Decide per relation:
block the delete, or keep a denormalised name on the roll for display.

### D6. The darkroom timer lives in a view model

`MainViewModel` runs the timer in `viewModelScope`. The end time is computed
from `elapsedRealtime`, which is right, but:

- If the process is killed mid-development — likely, with the screen off for
  ten minutes — the timer is gone. The safelight comment in the same file
  names exactly this failure for the theme, and fixes it only for the theme.
- With the screen off the coroutine's `delay` is not guaranteed to run on time
  in Doze, and on Android 12 and later a background app's vibration is ignored
  unless it carries alarm or notification usage attributes. The step-done cue
  is therefore unreliable in the one case it exists for.

A running timer is the textbook case for a foreground service with an ongoing
notification, state persisted so it can be rebuilt, and step-end alerts posted
as notifications. Which foreground service type Play accepts for a timer needs
checking when this is built (`shortService` is capped at about three minutes,
so it does not fit); an exact alarm per step is the alternative. See plan
step 3.3.

### D7. Dates and enums are free text

Dates are `String` in four shapes (`yyyy-MM-dd`, `yyyy-MM`,
`yyyy-MM-dd HH:mm`, blank); there are 25 `SimpleDateFormat` constructions.
Camera format, lens system, film type and process are display strings compared
with `==` and `contains` (`"120 (MF)"`, `"fixed"`, `"Black"`). A relabelled
menu item becomes a data migration. `daysUntilDate` truncates toward zero from
"now", so "expires today" and "expired yesterday evening" both read 0.
`ReminderWorker.checkUndeveloped` ages a roll from `startDate` although
`finishedAt` now exists.

### D8. Smaller items

- `ContentResolver.openOutputStream(uri)` defaults to mode `"w"`, which on some
  providers does not truncate. Exporting over a larger existing file can leave
  trailing bytes and a corrupt ZIP or PDF. Use `"wt"`.
- `BackupManager.export` swallows a per-photo failure after `putNextEntry`,
  which can leave a half-written entry; write to the entry only once the
  source file has been opened.
- `ReminderWorker`'s `notified_*` maps are never pruned and are re-read and
  re-written once per item.
- `keepZoomLevelsInStep` writes to the table it is collecting; it converges,
  but only because of the fresh re-read its own comment explains.
- `getCurrentLatLon` has no timeout; a phone indoors keeps the coroutine
  waiting for a high-accuracy fix indefinitely.

## Structural weak points

### A1. `MainViewModel` is the whole app (1 402 lines)

One class owns the inventory, roll logging, the meter's live state and its
settings, camera detection, zoom presets, weather and geocoding, the darkroom
timer and haptics, the theme, achievements, reminders scheduling, home layout
and exports. It is passed as `vm: MainViewModel` into 25 composables, so every
screen can reach every piece of state and no screen can be previewed or tested
without all of it. It holds the application `Context` and shows `Toast`s.

About fifty settings each follow the same four-line pattern
(`MutableStateFlow`, public `StateFlow`, a line in `init`, a `save…`
function), over 37 distinct string keys spread across three packages. A typo
in a key is a silent default.

### A2. Dependencies point the wrong way

- `ui/MainViewModel` imports from `ui/screens`: `DevTimer`, `SavedAgitation`,
  `HomeSection`, `MeterReading`, `formatWeatherString`, `getCurrentLatLon`.
  Domain types and a location call are defined inside screen files.
- `data/model/Models.kt` imports `util.ApertureScale` and `util.Constants`;
  `data/backup` imports `util.Achievements`.
- `util/` is three things: domain logic (`Achievements`, `GearClamp`,
  `Exposure`, `DevTime`), a 736-line `Constants.kt` of reference data, and
  Android helpers (`Photos.kt`).
- Room entities are also the backup wire format and the UI model. Any column
  rename is a backup format change.

### A3. Screen files are too large to work in

`MeterScreen.kt` 2 371 lines, `DarkroomScreen.kt` 2 052, `StashScreen.kt`
1 890, `ActiveScreen.kt` 1 413, `DashboardScreen.kt` 978. Each mixes screen
state, sheets, dialogs, storage codecs and helpers. `MeterScreen`'s main
composable derives the entire exposure solution inline, between camera binding
effects.

### A4. Navigation is a `var` in a composable

`VaultApp` keeps the current tab, the return tab for the meter and the map,
and six hand-off values (`initialRollId`, `meterShutter`, `statsPageRequest`,
…) in `remember`. Dashboard navigates with integer constants that
`VaultActivity` maps back to tabs. Consequences:

- There is no back stack; "back" is a `when`.
- Nothing survives process death: the app has 253 `remember { mutable… }`
  against 2 `rememberSaveable`. A user filling in a shot sheet who switches to
  the camera app can come back to Home with the sheet gone. The manifest's
  `configChanges` hides this for rotation only.

### A5. Test coverage stops at achievements

Seven JVM test classes, all for achievements, rewards, palettes, icons and sun
times. Not covered: `Exposure`, `GearClamp`, `DevTime` (the maths a user
trusts with a roll of film), `BackupManager` (round trip, legacy formats,
malformed input), and Room migrations — schemas are exported "so migrations
can be tested", and no migration test exists. Schemas 1–6 are not in the repo.

### A6. Smaller items

- All UI text is hard-coded (2 uses of `stringResource`); the app cannot be
  translated and strings cannot be reviewed in one place.
- About 120 fully qualified names inline (`java.text.SimpleDateFormat(…)`,
  `androidx.compose.ui.Alignment.CenterVertically`) where an import would do.
- 20 `catch (_: Exception) {}` and 24 `runCatching` with no logging. Failures
  in import, photo saving and camera binding are invisible; two sites call
  `printStackTrace()`.
- `AppVisibility.foreground` and `AppIconSwitcher.pending` are global mutable
  singletons.
- `BackupViewModel` and `RollExporter` take a `Context` per call from the UI
  instead of having the application context injected.
- `README.md`'s project structure still lists `MainActivity.kt`.

## Performance

Ordered by expected effect. P1–P3 are the ones a user with a few hundred rolls
would feel.

### P1. Every roll is re-parsed on every roll write

`RollDao.getAll()` is `SELECT * FROM rolls`, and each row's shots are decoded
with Gson by `ShotListConverter`. Logging one frame invalidates the query, so
Room reads every roll and re-parses every shot the vault has ever held —
on each of the flow's collectors. `repo.rolls` is collected independently by
`rolls`, `achievementProgress`, and every `repo.rolls.first()` call (eight of
them), so one +1 tap can parse the whole history several times.

- Share one upstream: expose `rolls` from the repository as a `StateFlow`
  /`shareIn` in an application scope, and make the `first()` callers use it.
- Add targeted queries: `rollById(id)`, `activeRolls()`, and a
  `RollSummary` projection (no `shots` column, plus a stored `shotCount`) for
  lists and badges.
- The `shots` table (plan step 5.1) removes the problem at the root.

### P2. Stats and lookups are O(rolls × films) on the main thread

`MainViewModel.stats` is a `combine` with no `flowOn`, started `Eagerly`: it
runs on `Dispatchers.Main.immediate` on every change to rolls, films, cameras
or bulk rolls, whether or not the Stats screen is open. Inside it `f.find { }`
runs per roll four times over, and `r.flatMap { it.shots }` is built twice.
The UI layer has 45 more `.find { it.id == … }` calls inside composables, many
inside list items.

- Build `associateBy { it.id }` maps once per emission and pass them down.
- `flowOn(Dispatchers.Default)` and `SharingStarted.WhileSubscribed(5_000)`.
- Same for `achievementProgress` (already off the main thread, but still
  eager) and `zoomFocalsUnverified`.

### P3. One state object, one recomposition scope

`MeterScreen`'s top-level composable collects about 30 flows and computes the
ladder, clamp and advice inline. A new meter reading (throttled, but several a
second) recomposes that entire function. The same shape, at lower frequency,
is in the other large screens: because they take the whole view model, every
screen reads `rolls`, `films`, `cameras` and `lenses` at the top.

- Give each screen one immutable `UiState` built off the main thread in its
  view model; move the exposure solution out of composition into the
  `MeterViewModel` (`combine` of reading, ISO, limits, priority).
- Read fast-changing values as late as possible: pass `() -> Double` or a
  `State` into the needle and readouts so only they recompose.
- Use `collectAsStateWithLifecycle` (0 uses today, 119 of `collectAsState`) so
  nothing is collected while the app is in the background.
- Turn on Compose compiler reports and mark UI models `@Immutable`;
  `List<…>` parameters make most of the current composables unskippable.
  Strong skipping (default from Kotlin 2.0.20) helps once Kotlin is upgraded.

### P4. Start-up does about 45 sequential queries

`MainViewModel.init` calls `repo.getSetting` once per key, each a separate
query hop, then seven `observeSetting` flows each re-query on any write to
`settings` (the code comments on this). Load the table once
(`SELECT * FROM settings`) into a map, expose it as one `StateFlow<Map>` and
derive typed values from it. First frame waits only on the theme keys, which is
already handled well. `refitRollFrames()` also runs a full read of three
tables on every launch; gate it behind a one-time settings flag.

### P5. Restore writes row by row

`restoreData` issues one upsert per record, each its own transaction and each
an invalidation of the observed queries (hence the `debounce(300)` on
achievements). A single transaction with `@Upsert` on lists is one
invalidation and an order of magnitude faster. Fixes D2 at the same time.

### P6. Images

- Shot photos are stored at up to 1 280 px and shown in grids, the contact
  sheet and map markers through `AsyncImage(File(path))`. Coil downsamples to
  the layout size, but decodes the full JPEG each time a cell is first shown.
  Either write a second ~320 px thumbnail at capture, or make sure Coil's disk
  and memory caches are sized for it and requests carry an explicit `size`.
- `downscalePhotoInPlace` decodes with `inSampleSize` and then scales again
  with a matrix, holding two bitmaps; fine at these sizes, but it also rewrites
  the file in place with no temp file — a crash mid-write loses the photo.
- `BadgeImage.share` compresses a 1080-wide PNG and writes it on the main
  thread (`render` is already on `Default`).
- `BadgeImage.grain` issues `W*H/40` `drawPoint` calls; draw into an
  `IntArray` and `setPixels` once.

### P7. Map

`ShotMapScreen` clusters in Kotlin on each zoom change. Confirm with a few
thousand geotagged frames that clustering is off the main thread and that
markers are diffed, not rebuilt. osmdroid itself is no longer maintained (the
repository was archived in 2024); MapLibre Native is the usual replacement and
renders vector tiles on the GPU.

### P8. Smaller items

- `OkHttpClient` has no cache; weather for the same place is re-fetched on
  every launch. Add a small disk cache and honour a 10-minute freshness window
  in the repository (OpenWeatherMap updates no faster).
- `material-icons-extended` is large; R8 removes unused icons in release, but
  it slows every debug build. The app draws its own `FilmIcons` — check
  whether the dependency is still needed.
- Preferring 120 Hz is on by default (`high_refresh`), which costs battery on
  every screen to fix scrolling on one family of devices. Consider defaulting
  it off, or requesting it only while a list is being flung.
- Add a Baseline Profile; for a Compose app it is the cheapest start-up and
  scroll improvement available.
- Five type converters each construct their own `Gson`.

## Build, CI and dependencies

### B1. No CI on pull requests

The only workflow runs on tags. Add a `ci.yml` for pull requests and `master`:
`./gradlew testDebugUnitTest lintDebug assembleDebug`. Without it, none of the
refactor steps has a safety net.

### B2. Toolchain is about two years behind

Kotlin 2.0.0, KSP 2.0.0-1.0.24, Compose BOM 2024.09, Hilt 2.51.1, Room 2.6.1,
Lifecycle 2.8.6, Coil 2.7, Retrofit 2.11 with Gson, osmdroid 6.1.18. AGP
(8.13.2) and CameraX (1.5.3) are current, so the build is carrying a wide
spread. Upgrade in this order, one PR each, running the app between them:
Kotlin + KSP, Compose BOM, Lifecycle, Room, Hilt, then Coil 3. Add Renovate or
Dependabot so it does not drift again. Two `versions` are written inline in
`libs.versions.toml` (`core-ktx`, `activity-compose`,
`play-services-location`) rather than in `[versions]`.

### B3. Static analysis

No detekt, ktlint or lint baseline. Add Android lint with `warningsAsErrors`
on a baseline, and detekt with the Compose rules; both are cheap once CI
exists and catch most of A6 mechanically.

### B4. `targetSdk` 35

Google Play requires new updates to target a recent API level each August;
plan the move to 36 (predictive back, which the hand-rolled navigation does
not support, becomes the default there).
