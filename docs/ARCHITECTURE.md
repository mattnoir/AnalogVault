# Architecture

The design to build toward. It describes where code goes and which way
dependencies point, so that a new feature has an obvious home and a change in
one area cannot break another by accident.

It is a target, not a description of `master` today. The gaps are listed in
[CODE_REVIEW.md](./CODE_REVIEW.md), and the order to close them in is in
[REFACTOR_PLAN.md](./REFACTOR_PLAN.md). New code should follow this document
from now on, even where the code around it does not yet.

## Contents

- [Principles](#principles)
- [Layers](#layers)
- [Package layout](#package-layout)
- [Domain layer](#domain-layer)
- [Data layer](#data-layer)
- [UI layer](#ui-layer)
- [Long-running work](#long-running-work)
- [Navigation](#navigation)
- [Storage formats](#storage-formats)
- [Errors and logging](#errors-and-logging)
- [Testing](#testing)
- [Rules for new code](#rules-for-new-code)
- [Decisions deliberately not taken](#decisions-deliberately-not-taken)

## Principles

1. **The vault is the product.** Nothing a user logged may be lost: writes are
   atomic, migrations are tested, formats are versioned.
2. **Offline first.** The database is the single source of truth. The network
   only ever fills a cache.
3. **Pure logic stays pure.** Exposure maths, development times, gear limits
   and achievements have no Android imports and run in JVM tests.
4. **One owner per piece of state.** A value is written in one place and
   observed everywhere else.
5. **Proportionate.** This is a single-developer app with one Gradle module.
   The design uses packages and conventions before it uses modules and
   frameworks.

## Layers

```text
┌──────────────────────────────────────────────────────────────┐
│ ui        screens, view models, navigation, theme            │
│           knows: domain                                      │
├──────────────────────────────────────────────────────────────┤
│ domain    models, pure logic, repository interfaces,         │
│           use cases                                          │
│           knows: nothing (Kotlin + coroutines only)          │
├──────────────────────────────────────────────────────────────┤
│ data      Room, settings, photos, network, backup, export    │
│           knows: domain                                      │
├──────────────────────────────────────────────────────────────┤
│ platform  services, workers, notifications, haptics, camera  │
│           knows: domain                                      │
└──────────────────────────────────────────────────────────────┘
          di/ wires the implementations to the interfaces
```

The one rule that matters: **`domain` imports nothing from `ui`, `data` or
`platform`, and `ui` imports nothing from `data` or `platform`.** Everything
else follows from it.

## Package layout

One Gradle module, `:app`, for now. Packages are laid out so that `domain`
can later be lifted into a pure Kotlin module without moving a file twice.

```text
com.analogvault
├── app/                    AnalogVaultApp, VaultActivity, VaultApp (nav host)
├── di/                     Hilt modules, dispatcher and scope qualifiers
│
├── domain/
│   ├── model/              FilmStock, Camera, Lens, Roll, Shot, Chemical,
│   │                       DevTimer, DevStep, Agitation, ZoomPreset, …
│   │                       value types: FilmFormat, LensSystem, Process
│   ├── exposure/           Exposure, GearClamp, ApertureScale, Reciprocity
│   ├── darkroom/           DevTime, timer state machine
│   ├── achievements/       Achievements, Rewards, AchievementRecords
│   ├── catalog/            reference data now in Constants.kt
│   ├── repository/         interfaces: InventoryRepository, RollRepository,
│   │                       SettingsRepository, WeatherRepository, PhotoStore
│   └── usecase/            LogShot, LoadRoll, UnloadRoll, CutFromBulk, …
│
├── data/
│   ├── db/                 AppDatabase, entities, DAOs, migrations, mappers
│   ├── settings/           SettingsRepositoryImpl, SettingKey definitions
│   ├── photo/              PhotoStoreImpl (files/camera_photos)
│   ├── weather/            WeatherApi, DTOs, WeatherRepositoryImpl
│   ├── backup/             BackupManager, backup DTOs, legacy readers
│   └── export/             CSV and PDF writers
│
├── platform/
│   ├── timer/              DarkroomTimerService, TimerController
│   ├── reminders/          ReminderWorker, scheduling
│   ├── notifications/      channels, AchievementNotifier
│   ├── camera/             CameraRig, CameraOptics
│   ├── location/           LocationProvider
│   └── haptics/            Haptics
│
└── ui/
    ├── designsystem/       today's ui/film, ui/components, ui/theme
    ├── navigation/         destinations, VaultNavHost
    └── feature/
        ├── home/           HomeScreen, HomeViewModel, HomeUiState, layout/
        ├── stash/          StashScreen, StashViewModel, sheets/
        ├── rolls/          RollsScreen, RollDetail, ShotSheet, contact sheet
        ├── meter/          MeterScreen, MeterViewModel, viewfinder/, ladder/
        ├── darkroom/       timers, chemistry, logs
        ├── stats/          stats, badges, share
        ├── map/
        ├── weather/
        ├── backup/
        └── settings/
```

A feature package holds one screen (or a small family), its view model, its
`UiState`, and the composables only it uses. Anything used by two features
moves to `designsystem` (if it draws) or `domain` (if it decides).

**File size.** No hard limit, but a file over about 500 lines is a prompt to
ask what else is in it. A screen file holds the screen; each sheet, dialog and
non-trivial section is its own file in the feature package.

## Domain layer

Plain Kotlin. No `android.*`, no `androidx.*`, no Room annotations, no Gson.

**Models** are immutable data classes. They are *not* the Room entities: the
database, the backup file and the UI each have their own reasons to change,
and tying them together is what makes a column rename a backup format break
today.

**Value types instead of strings** for anything the code branches on:

```kotlin
enum class LensSystem(val key: String) { FIXED("fixed"), INTERCHANGEABLE("interchangeable") }

enum class FilmFormat(val key: String, val label: String) {
    F135("135", "135 (35mm)"), F120("120", "120"), F220("220", "220"), …
    companion object { fun fromStored(raw: String): FilmFormat = … }  // tolerant of old labels
}
```

The stored `key` is a format and never changes; the `label` is UI and can.
Dates are `LocalDate` / `LocalDateTime` in the domain, converted at the
database boundary.

**Repository interfaces** are declared here and implemented in `data`. They
expose `Flow`s for reading and `suspend` functions for writing:

```kotlin
interface RollRepository {
    fun observeRolls(): Flow<List<RollSummary>>
    fun observeRoll(id: String): Flow<Roll?>
    suspend fun addShot(rollId: String, shot: Shot)        // atomic
    suspend fun updateShot(rollId: String, shot: Shot)
    suspend fun setStage(rollId: String, stage: RollStage, on: Boolean, today: LocalDate)
}
```

Writes are *operations*, not "upsert this whole object". That is what removes
the lost-update races (CODE_REVIEW D1): the repository performs the read and
the write inside one transaction.

**Use cases** exist only where an action spans repositories or carries rules
worth testing: `LogShot` (roll + photo + weather + location + achievement
counters), `UnloadRoll`, `CutFromBulk`, `RefitRollFrames`. A screen that only
lists and edits one entity calls the repository directly; do not wrap every
call in a use case.

**Time** is injected (`java.time.Clock`), never read with `Date()` or
`LocalDate.now()` inside logic, so that expiry, reminders and achievements can
be tested on a fixed day.

## Data layer

### Database

- Entities live in `data/db` with mappers to and from domain models.
- DAOs offer the queries the screens need, not only `getAll()`: by id, by
  stage, and summary projections that leave large columns out.
- Every operation touching more than one row is a `@Transaction` method or
  runs in `db.withTransaction { }`.
- Foreign keys are declared, with an explicit `onDelete` per relation, and the
  columns they use are indexed.
- A migration is a file per version in `data/db/migrations`, with a
  `MigrationTestHelper` test that runs it against the exported schema.
- A database the app cannot open is never deleted. Downgrade is an error
  shown to the user.

Target schema for frames, replacing the JSON column:

```text
rolls (id PK, film_id FK, camera_id FK, lens_id FK NULL, start_date, stage flags, costs, …)
shots (id PK, roll_id FK → rolls ON DELETE CASCADE, position INT,
       shutter, aperture, iso, lens_id FK NULL, lens_name,
       lat REAL NULL, lon REAL NULL, place TEXT,
       taken_at, weather, notes, photo_file TEXT, mono_contrast REAL NULL)
INDEX shots(roll_id, position)
```

`lens_name` stays beside `lens_id` so history survives a deleted lens.
`photo_file` is a file name inside the photo directory, never a path.

### Settings

One `SettingsRepository` over the existing `settings` table (no need to move
to DataStore: the table is already backed up, migrated and observed). Keys are
declared once, typed:

```kotlin
sealed class SettingKey<T>(val name: String, val default: T, val codec: Codec<T>)

object Settings {
    val Currency      = StringKey("currency", "€")
    val IsMetric      = BoolKey("is_metric", true)
    val MeterIso      = IntKey("meter_iso", 400)
    val MeterPriority = EnumKey("meter_priority", MeterPriority.APERTURE) { it.key }
    …
}

interface SettingsRepository {
    fun <T> observe(key: SettingKey<T>): Flow<T>
    suspend fun <T> set(key: SettingKey<T>, value: T)
    suspend fun <T> update(key: SettingKey<T>, transform: (T) -> T)   // atomic
}
```

The implementation loads the table once, keeps it as a single
`StateFlow<Map<String, String>>`, and derives each key from that. A view model
then exposes `settings.observe(Settings.Currency)` directly; the four-line
pattern per setting disappears. Secrets (the weather API key) are stored
through a separate `SecretStore`, outside cloud backup.

### Photos

`PhotoStore` owns `files/camera_photos`. It is the only code that turns a
stored name into a `File`, and it refuses names that resolve outside its
directory. It saves (to a temp file, then renames), deletes, lists, and sweeps
orphans.

### Network

`WeatherRepository` wraps the API: it holds the last result per place, applies
a freshness window, and returns a sealed result type. DTOs stay in `data`;
the domain sees a `Weather` model. OkHttp gets a disk cache and explicit
timeouts.

### Backup

Backup has its own DTOs with explicit field names (`@SerializedName`, or
kotlinx.serialization once adopted), independent of entities and of R8.
Reading is a chain of version readers (`v1`, `v2` obfuscated, `v3`) that all
produce the same in-memory `VaultSnapshot`, which is validated (sizes, ids,
photo names) and then applied in one transaction.

## UI layer

**One view model per feature**, injected with the repositories and use cases
it needs. No view model takes a `Context`; anything that needs one is behind
an interface in `platform`.

**One immutable `UiState` per screen**, produced off the main thread:

```kotlin
@Immutable
data class RollsUiState(
    val loading: Boolean = true,
    val stages: List<StageColumn> = emptyList(),   // already joined with film and camera names
)

@HiltViewModel
class RollsViewModel @Inject constructor(rolls: RollRepository, inventory: InventoryRepository) : ViewModel() {
    val state: StateFlow<RollsUiState> =
        combine(rolls.observeRolls(), inventory.observeFilms(), inventory.observeCameras(), ::toUiState)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RollsUiState())
}
```

- Joins and lookups happen in the view model. A composable never calls
  `.find { it.id == … }`.
- Screens collect with `collectAsStateWithLifecycle()`.
- `SharingStarted.WhileSubscribed(5_000)` unless there is a stated reason for
  `Eagerly`.
- **Stateful / stateless split.** `RollsRoute(viewModel)` collects state and
  passes it to `RollsScreen(state, onEvent)`, which takes no view model and
  can be previewed and screenshot-tested.
- One-off events (a toast, "open the share sheet") go through a `Channel`
  exposed as a `Flow`, collected in the route. View models do not show UI.
- Form state the user would mind losing uses `rememberSaveable` or lives in
  the view model with `SavedStateHandle`.
- User-visible text is in `strings.xml`.

**App-wide state** — theme, safelight, edge print, unlock banners — belongs to
an `AppViewModel` scoped to the activity, small enough to read in one sitting.

## Long-running work

Work that must outlive a screen does not live in a view model.

| Work | Owner | Scope |
|---|---|---|
| Darkroom timer | `TimerController` (singleton) + `DarkroomTimerService` (foreground) | process, survives restart from persisted state |
| Achievement reconciliation | `AchievementTracker` | application scope |
| Reminders | `ReminderWorker` | WorkManager |
| Photo orphan sweep | `PhotoSweepWorker` | WorkManager, weekly |
| Backup import / export | `BackupManager` | application scope, so leaving the screen does not cancel a half-written file |

`TimerController` holds the timer state machine (pure, in `domain/darkroom`)
and exposes a `StateFlow`. The service keeps the process alive while a timer
runs, shows the ongoing notification, and fires step-end alerts as
notifications so they work with the screen off. The state is persisted on each
transition (timer id, step index, end time as wall clock) and rebuilt on start.

An `@ApplicationScope CoroutineScope` and dispatcher qualifiers are provided by
`di/`; nothing constructs its own scope or hard-codes `Dispatchers.IO`.

## Navigation

Navigation Compose with type-safe routes. Destinations are serializable
objects, and what one screen hands another is a route argument, not a `var` in
the host:

```kotlin
@Serializable data object Home
@Serializable data class Rolls(val stage: Int = 0, val openRollId: String? = null)
@Serializable data class Meter(val viaShutter: Boolean = false)
@Serializable data class ShotMap(val rollId: String? = null)
@Serializable data class Stats(val page: Int = 0)
```

This gives a real back stack (the map returns to whichever screen opened it
without `tabBeforeMap`), state restoration after process death, predictive
back, and deep links for the notification that opens Badges. The shutter
iris and the directional tab slide are transition specs on the host and are
kept as they are.

The bottom bar's four tabs are top-level destinations with saved state;
"More" entries are ordinary destinations pushed on top.

## Storage formats

Three things are formats and are changed only deliberately, with a version
bump and a reader for the old shape:

1. **The Room schema** — exported, migration-tested.
2. **The `.avault` backup** — `version` field, a reader per version, a newer
   version than the app knows is refused with a clear message.
3. **JSON inside `settings` values** (saved timers, agitations, achievement
   records, counters) — literal keys, tolerant decoders, covered by tests with
   fixtures from released versions.

Fixtures for all three live under `app/src/test/resources/fixtures/` and are
added to at each release, never edited.

## Errors and logging

- Expected failures are values: `sealed interface Outcome<T>` (or
  `kotlin.Result`) from repositories and managers, with a typed reason the UI
  can turn into a sentence.
- `catch (_: Exception) {}` is not used. Either the failure is handled, or it
  is logged through one `Logger` interface (Logcat in debug, no-op in
  release) and then handled.
- `CancellationException` is always rethrown. `runCatching` around suspending
  code swallows it; use a helper that does not.
- `Error` subclasses (`OutOfMemoryError`) are prevented by bounding input, not
  caught.

## Testing

| Level | What | Tooling |
|---|---|---|
| Unit (JVM) | everything in `domain`; view models with fake repositories | JUnit, kotlinx-coroutines-test, Turbine |
| Data (JVM, Robolectric or instrumented) | DAOs on an in-memory database; every migration; backup round trip and legacy fixtures; malformed and oversized archives | Room testing, MigrationTestHelper |
| UI | stateless screens by screenshot, in every palette and safelight | Roborazzi or Paparazzi |
| Device (manual checklist per release) | camera binding, metering against a reference meter, timer with the screen off | — |

`domain` logic that a user stakes a roll of film on — exposure, reciprocity,
development time scaling — has table-driven tests against published values.

CI runs unit tests, lint and a debug build on every pull request.

## Rules for new code

A short list to check a pull request against:

1. Does `domain` still compile without Android? Does `ui` import anything from
   `data` or `platform`?
2. Is each write one repository operation in one transaction?
3. Is any new setting a `SettingKey`, and any new stored JSON written with
   literal keys and a fixture test?
4. Does a schema change come with a migration, an exported schema and a
   migration test? Does the backup reader cope with files that lack the new
   field?
5. Does the screen take a `UiState` and callbacks, not a view model?
6. Is anything computed in a composable that could be computed once in the
   view model?
7. Does work that must finish (a file being written, a timer) run somewhere
   that outlives the screen?
8. Is new logic covered by a JVM test?

## Decisions deliberately not taken

- **Multiple Gradle modules.** Not yet. The package rule above gives most of
  the benefit; build time for 25 000 lines does not justify the ceremony. The
  first module worth extracting, when it is, is `:domain` as a pure Kotlin
  library — it makes rule 1 a compiler error.
- **A use case for every call.** Only where there is a rule to test.
- **MVI framework / Redux-style store.** `StateFlow<UiState>` plus plain
  functions is enough.
- **DataStore for settings.** The `settings` table works, is already in every
  backup, and moving it is a migration with no user-visible gain.
- **Kotlin Multiplatform.** Nothing in the roadmap needs it; keeping `domain`
  pure leaves the door open at no cost.
