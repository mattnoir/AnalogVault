# Refactor plan

How to get from `master` today to the design in
[ARCHITECTURE.md](./ARCHITECTURE.md), in steps small enough to review and
release one at a time. Finding IDs (S1, D1, P2, …) refer to
[CODE_REVIEW.md](./CODE_REVIEW.md).

## Ground rules

- **One step, one pull request.** Each leaves `master` releasable.
- **Moves and changes are separate commits.** A commit that moves a file
  changes nothing in it, so `git log --follow` and review both stay readable.
- **No behaviour change without a test that pins the old behaviour first.**
- **Formats are sacred.** A step that touches the Room schema, a `settings`
  key or the backup format says so in its title and ships with a migration
  test and a fixture (CONTRIBUTING.md already asks for the first half).
- Steps inside a phase can usually be reordered. Phases 0 and 1 come first;
  phase 5 needs phases 2 and 3.

Sizes: **S** under a day, **M** a few days, **L** a week or more of evenings.

## Overview

| Phase | Goal | Fixes | User-visible |
|---|---|---|---|
| 0 | Safety net: CI and tests around what must not break | A5, B1, B3 | no |
| 1 | Security and data-integrity fixes that need no restructuring | S1–S9, D2–D4, D8 | bug fixes |
| 2 | Put code where the architecture says it goes | A2, A6, D7 (part) | no |
| 3 | Break up `MainViewModel`; move the timer out | A1, D1, D6, P2, P4 | timer survives screen-off |
| 4 | Break up the screens; real navigation | A3, A4, P3 | state survives process death |
| 5 | Data model: shots table, keys, typed columns | D1, D5, D7, P1, P5 | faster with large vaults |
| 6 | Performance polish and toolchain | P6–P8, B2, B4 | smoother, smaller |

## Phase 0 — Safety net

Nothing after this phase is safe without it.

**0.1 CI on pull requests (S).** Add `.github/workflows/ci.yml`:
`./gradlew testDebugUnitTest lintDebug assembleDebug` on pull requests and
pushes to `master`, with Gradle wrapper validation. Create a lint baseline so
existing warnings do not block, and fail on new ones.

**0.2 Migration tests (M).** `MigrationTestHelper` tests for 7→8 through
13→14 against the exported schemas, plus one that opens a seeded v7 database
with the current build and reads every table. Needs `room-testing` and either
Robolectric or an instrumented job. Schemas 1–6 are not in the repo; recover
them from the tagged releases if possible (check out the tag, run the build
with `exportSchema` on), otherwise document that upgrades from before 0.4 are
untested.

**0.3 Backup fixtures and round-trip test (M).** Commit one real backup per
format the reader claims to support — v1 JSON, v2 JSON with embedded photos,
v2 with R8-renamed keys, v3 ZIP — with personal data replaced. Tests:
import each into an empty in-memory database and assert the row counts;
export then import and assert equality; import twice and assert nothing is
duplicated. This is the test that makes phases 1 and 5 safe.

**0.4 Characterisation tests for the maths (M).** Table-driven JVM tests for
`Exposure`, `GearClamp`, `ApertureScale`, `DevTime` (temperature scaling,
per-roll extension, agitation windows) and `Habits`. Where a published value
exists (EV tables, manufacturer reciprocity data, the Ilford
time/temperature chart) test against it; otherwise pin current output.

**0.5 Static analysis (S).** detekt with the Compose rule set, on a baseline.

*Done when:* a pull request that breaks a migration, the backup reader or the
exposure maths goes red.

## Phase 1 — Security and integrity fixes

Small, independent, each a `fix:` with a changelog entry. Order by risk.

**1.1 Photo paths (S1) (M).** Introduce `PhotoStore` in `data/photo` as the
only code that maps a stored value to a `File`:

- `resolve(stored: String): File?` accepts a bare name or a legacy absolute
  path, takes the file name, and returns it only if it canonicalises inside
  `photoDir`.
- All readers (`FramePhoto`, `RollExporter`, `BackupManager.export`) go
  through it. Writers store the bare name.
- A one-time pass rewrites existing absolute paths to names (same shape as
  `migratePhotosFromCache`). No schema change: the column stays `TEXT`.
- Backup format: v3 readers already match by file name; exported paths become
  names. Old app versions importing a new backup still work, because they
  remap by `File(path).name`.

**1.2 Bounded, staged ZIP import (S2, S8) (M).** Size-capped streams for
`backup.json` and photos, an entry-count cap, a file-name allow-list, a
version check in the ZIP path, extraction to `cacheDir/import-<uuid>` and a
move into place only after the JSON validates. Pass a `Reader` to the legacy
importer instead of a `String`. Tests: oversized entry, too many entries,
`photos/..`, `photos/../../databases/x`, a version-99 file.

**1.3 Transactions (D2, P5) (S).** Inject `AppDatabase` into the repository;
wrap `restoreData`, `cutFromBulk` and `unloadRoll` in `withTransaction`. Add
list `@Upsert` overloads and use them in restore.

**1.4 Atomic roll edits (D1, interim) (M).** `@Transaction` DAO methods —
`addShot`, `updateShot`, `deleteShot`, `setStage`, `setShotLocation` — that
read the row and write it in one transaction. Point the view model at them
and delete the `rolls.value.find { }` + `upsertRoll` call sites. This closes
the lost-update window now; phase 5 replaces the implementation without
changing the callers.

**1.5 Never wipe on downgrade (D3) (S).** Remove
`fallbackToDestructiveMigrationOnDowngrade()`. Catch the open failure at
start-up and show a blocking "made by a newer version" screen with an
"export raw database file" escape hatch.

**1.6 API key handling (S3, S9) (M).** Export option "include API key",
default off; do not overwrite an existing key on import. Move the key to a
preferences file excluded from `cloud-backup`. Strip `appid` from debug
logs. State in the Backup screen that exports contain locations. Changes the
backup format additively (key may be absent — already tolerated).

**1.7 Manifest and build (S4, S6) (S).** Remove the three storage
permissions after checking the osmdroid cache on API 26–28. Make an unsigned
release build fail unless `-PallowDebugSigning` is passed.

**1.8 Release workflow (S5) (S).** Pin actions to SHAs, restrict
`workflow_dispatch`, clean up the keystore, add wrapper validation.

**1.9 Export details (S7, D8) (S).** Formula guard on CSV text columns;
`openOutputStream(uri, "wt")`; open the photo before `putNextEntry`.

**1.10 Photo sweep (D4) (S).** A weekly worker that deletes files in
`photoDir` not referenced by any shot and older than 24 hours. Log how many
it removed. Ship it one release after 1.1 so both are not new at once.

*Done when:* every S-finding and D2–D4 is closed or has an issue explaining
why not.

## Phase 2 — Put things where they belong

Mechanical. No behaviour change, so these are `refactor:` and can be large
diffs with trivial review, provided moves and edits are separate commits.

**2.1 Create the package skeleton and move the pure code (M).**

| From | To |
|---|---|
| `util/Exposure`, `GearClamp`, `ApertureScale` | `domain/exposure/` |
| `util/DevTime` | `domain/darkroom/` |
| `util/Achievements`, `Rewards`, `BadgeShare`, `Habits` | `domain/achievements/` |
| `util/SunTimes`, `SunClock` | `domain/sun/` |
| `util/Constants` | split: reference lists → `domain/catalog/*`, settings keys → `data/settings`, the date helper → `domain/time` |
| `util/Photos` | `data/photo/` |
| `ui/film`, `ui/components`, `ui/theme` | `ui/designsystem/…` |
| `data/camera` | `platform/camera/` |
| `work/*` | `platform/reminders/`, `platform/notifications/` |

**2.2 Rescue domain types from screen files (S).** `DevTimer`, `DevStep`,
`StepRole`, `SavedAgitation` and their codecs out of `DarkroomScreen.kt`;
`HomeSection` and its parsers out of `HomeLayoutScreen.kt`; `MeterReading`
out of `MeterScreen.kt`; `getCurrentLatLon` → `platform/location`;
`formatWeatherString`, `formatLatLon` → `domain`. After this the view model
imports nothing from `ui.screens`.

**2.3 Enforce the dependency rule (S).** A test (Konsist, or a ten-line
script in CI that greps imports) that fails if `domain` imports `android`,
`androidx`, `ui`, `data` or `platform`, or `ui` imports `data`. Start with
`domain` only and widen as later phases make the other rules true.

**2.4 Typed settings (M).** `SettingKey` and `SettingsRepository` as in the
architecture document, backed by one cached read of the table. Convert the
37 keys, one area per commit. Stored values do not change; add a test that
every key's default and codec round-trips against a fixture of today's raw
values.

**2.5 Clock and dispatchers (S).** Provide `Clock`, `@IoDispatcher`,
`@DefaultDispatcher` and `@ApplicationScope` from `di/`. Replace
`Date()`/`LocalDate.now()`/`System.currentTimeMillis()` in logic and
hard-coded `Dispatchers.*` in classes. Collapse the 25 `SimpleDateFormat`
sites into one `domain/time` object using `java.time`.

**2.6 Logging and error results (S).** A `Logger` interface; replace each
empty `catch` and bare `runCatching` with handle-and-log, rethrowing
`CancellationException`. Merge `BackupResult` and `ExportResult` into one
outcome type with typed reasons.

**2.7 Tidy (S).** Imports instead of fully qualified names; fix the README's
project structure; move inline versions in `libs.versions.toml` to
`[versions]`.

*Done when:* the dependency test passes for `domain`, and
`MainViewModel` no longer imports from `ui.screens`.

## Phase 3 — Break up `MainViewModel`

One feature at a time. The pattern for each: create the feature view model
with the state and functions that belong to it, switch that screen to it,
delete them from `MainViewModel`. `MainViewModel` shrinks until it is the
`AppViewModel` and can be renamed.

Because screens currently take the whole view model, do each extraction in two
commits: first the new view model with `MainViewModel` delegating to it
(nothing else changes), then the screen switched over.

**3.1 Split the repository (M).** `VaultRepository` becomes
`InventoryRepository` (films, cameras, lenses, accessories, bulk),
`RollRepository`, `ChemistryRepository` and `ZoomPresetRepository`, as
interfaces in `domain/repository` with implementations in `data/db`. Each
exposes a shared upstream (`shareIn` in the application scope) so the table
is read once per change however many collectors there are (P1, first half).
Keep `VaultRepository` as a thin façade until the last caller is gone.

**3.2 App-scoped trackers (M).** `AchievementTracker` (the
`achievementProgress` → `reconcile` → announce pipeline) and the zoom-preset
upkeep move out of the view model into application-scoped classes started
from `AnalogVaultApp`. `AppVisibility` becomes an injected
`ProcessLifecycleOwner`-backed interface.

**3.3 Timer controller and service (L, `feat`).** Extract the timer state
machine into `domain/darkroom` as a pure reducer with tests (start, pause,
skip, step end, agitation windows, the skipped-run rule). `TimerController`
drives it; `DarkroomTimerService` keeps it alive in the foreground with an
ongoing notification, persists state on each transition and restores it on
start; step ends are notifications. Fixes D6. Needs the
`FOREGROUND_SERVICE` permissions and a manual test matrix (screen off, Doze,
process kill, reboot). The most valuable user-facing change in this plan.

**3.4 Feature view models (L in total, S–M each).** Suggested order, least
entangled first:

1. `BackupViewModel` — already separate; inject the application context into
   `BackupManager`/`RollExporter` and run them in the application scope.
2. `WeatherViewModel` + `WeatherRepository` (place pinning, search, cache).
3. `SettingsViewModel` — mostly disappears into `SettingsRepository`.
4. `HomeLayoutViewModel`.
5. `StatsViewModel` — `stats` moves here, built from id-keyed maps on
   `Dispatchers.Default`, `WhileSubscribed` (P2).
6. `DarkroomViewModel` — chemistry, saved timers, talks to `TimerController`.
7. `StashViewModel`.
8. `RollsViewModel` + the `LogShot` use case (takes over `quickLogShot`).
9. `MeterViewModel` — last, because it is the most stateful. Live reading,
   lock, zone marks, per-roll overrides, and the exposure solution as one
   `StateFlow<MeterSolution>` computed off the main thread (P3).
10. `HomeViewModel`.

What remains is `AppViewModel`: theme, safelight, look, unlock banner queue,
the "open badges" request.

*Done when:* no composable takes `MainViewModel`; the class is under ~200
lines; `SharingStarted.Eagerly` appears only with a comment saying why.

## Phase 4 — Screens and navigation

**4.1 Route / screen split (M per screen).** For each feature:
`XRoute(viewModel)` collects with `collectAsStateWithLifecycle` and renders
`XScreen(state, onEvent)`. Move each sheet and dialog to its own file. Do the
meter last and split it by concern: `viewfinder/` (camera binding, AE region,
zoom), `ladder/`, `readouts/`, `commit/`.

**4.2 Navigation Compose (M).** Type-safe routes as in the architecture
document. Replace the `Tab` enum, the integer tab constants in
`DashboardScreen`, and the hand-off `var`s with route arguments. Keep the
iris and slide transitions as `enterTransition`/`exitTransition` specs. The
achievement notification becomes a deep link. Check predictive back.

**4.3 State that survives (M).** Sheets with real input (shot, film, camera,
lens, chemistry, custom timer) keep their draft in the view model via
`SavedStateHandle`. Then try removing entries from the manifest's
`configChanges` and test with "Don't keep activities".

**4.4 Screenshot tests (M).** With stateless screens this is cheap: each
screen in each palette plus safelight, with fixed `UiState`s. This is the net
for the design system work that follows.

**4.5 Strings (M).** Extract UI text to `strings.xml`, feature by feature.
Not for translation yet — for review, consistency and lint.

*Done when:* killing the process on any screen and returning lands on the
same screen with the same draft.

## Phase 5 — Data model

Schema changes. Each is one migration, with a migration test, an updated
backup reader and writer, and a fixture. Ship them in separate releases so a
problem can be pinned to one.

**5.1 Shots table (L, schema 15, backup v4).** Create `shots`, populate it
from each roll's JSON in the migration (parse with the same tolerant reader
the backup uses; a roll whose JSON cannot be parsed is kept, with its raw
JSON preserved in a `legacy_shots` column, never dropped). Split location
into `lat`/`lon`/`place`. Repository operations from step 1.4 keep their
signatures; their bodies become single-row statements. List screens use a
`RollSummary` query with a shot count. Backup v4 writes shots nested under
rolls as before (the file shape need not follow the table shape), so only
the version number and the new fields change; v3 files import through the
existing reader.

**5.2 Foreign keys and indices (M, schema 16).** After a data-cleaning pass
in the migration (null out references to rows that no longer exist). Decide
and document per relation: `rolls.film_id` and `rolls.camera_id` →
`RESTRICT` with a UI message, or `SET NULL` with a denormalised name kept on
the roll.

**5.3 Typed columns (M, schema 17).** Stable keys for format, lens system,
film type, process; ISO-8601 dates checked on write. The migration maps
today's display strings to keys and leaves anything unrecognised as it is,
with the domain `fromStored` staying tolerant.

**5.4 Separate entities from domain and backup models (M).** Mappers at both
boundaries; drop the legacy `FilmStock.shots` column while there. After
this, `-keep class com.analogvault.data.model.**` can be narrowed to the
backup DTOs.

**5.5 (Optional) kotlinx.serialization (M).** Replace Gson for backup DTOs,
network DTOs and settings JSON. Removes the null-into-non-null problem
`sanitized()` exists for, and the reflection R8 has to be told about. Only
worth doing once 5.4 has isolated the DTOs.

*Done when:* logging a frame is a one-row insert, and deleting a camera with
rolls is a decision the app makes rather than an accident.

## Phase 6 — Performance and toolchain

Measure first: a macrobenchmark for cold start, scrolling Rolls with 300
seeded rolls, and opening the meter; Compose compiler reports for the five
largest screens. Keep the numbers in the PR descriptions.

**6.1 Toolchain upgrades (M, one PR each) (B2).** Kotlin + KSP → Compose
BOM → Lifecycle → Room → Hilt → Coil 3. Add Renovate.

**6.2 Compose stability (M) (P3).** `@Immutable` UI models,
`kotlinx.collections.immutable` or stable wrappers for list parameters,
deferred state reads on the meter needle and timer countdown, keys on every
lazy list.

**6.3 Baseline Profile (S) (P8).**

**6.4 Images (S–M) (P6).** Small thumbnail written at capture (or explicit
Coil sizes and a sized cache); atomic photo writes; badge PNG written off the
main thread; grain via `setPixels`.

**6.5 Network (S) (P8).** OkHttp disk cache, timeouts, a freshness window in
`WeatherRepository`.

**6.6 Map (L, optional) (P7).** Move clustering off the main thread and diff
markers; evaluate MapLibre as the replacement for osmdroid.

**6.7 Target SDK 36 (S–M) (B4).**

## What to do first

If only a few evenings are available, these give the most for the least:

1. **0.1** CI — an hour, and everything else gets safer.
2. **0.3** backup fixtures — the vault's escape hatch, currently untested.
3. **1.1 + 1.2** photo paths and bounded import — the two real security
   findings.
4. **1.3 + 1.4** transactions and atomic roll edits — closes the lost-frame
   race.
5. **3.3** timer service — the bug a user is most likely to hit in the dark
   with wet hands.

## Risks

| Risk | Mitigation |
|---|---|
| A migration loses data on a device in the wild | Migration tests on real fixtures (0.2); migrations never delete — unparseable data is carried in a legacy column; one schema change per release |
| A backup written by a new version cannot be read by an old one | Additive changes only within a version; version bump when the shape changes; old readers refuse newer versions clearly (1.2) |
| Behaviour drifts during the view-model split | Delegate-then-switch in two commits (phase 3); characterisation tests (0.4); screenshot tests before the design system moves |
| The refactor stalls half way, leaving two patterns | Each phase has a "done when"; phases 0–1 and step 3.3 stand on their own if nothing else ships |
| Foreground-service policy for the timer | Check the current Play policy before building 3.3; exact alarms per step are the fallback |
