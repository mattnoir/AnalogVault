# Conventions

How things are named in this repo: files, packages, types, functions, state,
stored keys, database objects, resources, tests, branches, commits and pull
requests.

Most of this writes down what the code already does. Where the code does two
things, one is picked and marked **(pick)**; where a rule is new, it is marked
**(new)**. Existing code is brought into line when it is next touched, or in
the refactor step named beside the rule — not in a rename-everything pull
request.

Branches, commits and the changelog are already covered by
[CONTRIBUTING.md](../CONTRIBUTING.md), which stays the authority for them.
This document repeats those rules in short and lists
[proposed amendments](#proposed-amendments-to-contributingmd). Where code
goes is in [ARCHITECTURE.md](./ARCHITECTURE.md); how versions move is in
[VERSIONING.md](./VERSIONING.md).

## Contents

- [Language](#language)
- [Packages and files](#packages-and-files)
- [Types](#types)
- [Functions](#functions)
- [Composables](#composables)
- [State and view models](#state-and-view-models)
- [Constants and stored keys](#constants-and-stored-keys)
- [Database](#database)
- [Resources](#resources)
- [Tests](#tests)
- [Comments](#comments)
- [Branches](#branches)
- [Commits and pull requests](#commits-and-pull-requests)
- [Changelog entries](#changelog-entries)
- [Tags and releases](#tags-and-releases)
- [Proposed amendments to CONTRIBUTING.md](#proposed-amendments-to-contributingmd)

## Language

- Code, comments, commits and documentation are in **British English**, as
  the UI already is: *colour*, *centre*, *stabiliser*, *normalise*.
  Identifiers that mirror an API keep its spelling (`Color`, `colorFilter`).
- Photographic terms are used as a photographer would: *stock* (a kind of
  film), *roll* (one loaded film), *frame* in the UI and *shot* in code for a
  logged exposure, *stash*, *bulk*, *rebate*, *stop*.
- Kotlin style is the official one (`kotlin.code.style=official`), with a
  120-column limit.

## Packages and files

**Packages** are lowercase single words, no underscores:
`domain.exposure`, `ui.feature.darkroom`, `data.backup`.

**One top-level type per file, named after it** **(pick)**. The code has both
this (`BackupManager.kt`, `FilmChip.kt`) and grab-bag files (`Models.kt`,
`Daos.kt`, `Components.kt`, `Constants.kt`); the grab-bags are split as the
refactor reaches them.

| File holds | Name | Example |
|---|---|---|
| A class, interface or object | the type | `RollRepository.kt` |
| A screen | `<Feature>Screen.kt` | `StashScreen.kt` |
| A screen's entry point with its view model | `<Feature>Route.kt` **(new)** | `StashRoute.kt` |
| A view model and its state | `<Feature>ViewModel.kt`, `<Feature>UiState.kt` | `MeterViewModel.kt` |
| A bottom sheet or dialog | `<Thing>Sheet.kt`, `<Thing>Dialog.kt` | `ShotSheet.kt` |
| One composable | the composable | `FrameCounter.kt` |
| Extension functions on one type | `<Type>Ext.kt` **(new)** | `RollExt.kt` |
| Top-level functions on one topic | the topic, as a noun | `SunTimes.kt` |
| A Room entity | `<Model>Entity.kt` **(new)** | `RollEntity.kt` |
| A DAO | `<Model>Dao.kt` | `RollDao.kt` |
| A migration | `Migration<From>To<To>.kt` **(new)** | `Migration14To15.kt` |
| A Hilt module | `<Area>Module.kt` | `DatabaseModule.kt` |
| A worker / service | `<Job>Worker.kt`, `<Job>Service.kt` | `ReminderWorker.kt` |

Not used: `Utils`, `Helpers`, `Manager` for new types (say what it does:
`BackupReader`, `PhotoStore`), `Impl` except for the single implementation of
a domain interface (`RollRepositoryImpl`).

The design-system prefixes stay: `Film…` for film-themed components
(`FilmChip`, `FilmNavBar`), `Vault…` for the generic kit (`VaultButton`,
`VaultSheet`, `VaultTextField`).

## Types

- `PascalCase`. Acronyms of three letters or more are words: `IsoPicker`,
  `OwmKey`, `PdfExporter`; two-letter ones stay upper: `EVReadout`, `IOError`.
- Nouns for classes and objects; adjectives or capabilities for interfaces
  only when natural (`Clock`, `Logger`, `PhotoStore` — no `I` prefix).
- Enums: `PascalCase` type, `UPPER_SNAKE` entries. An enum that is stored
  carries a stable `key` and a `fromKey` that tolerates unknown values, as
  `MeterPriority` does. The entry name is never what is stored.
- Sealed results end in what they are: `BackupOutcome`, `WeatherState`.
- UI state is `<Feature>UiState`; one-off events are `<Feature>Event`.
- Data transfer types are suffixed by their boundary: `…Entity` (Room),
  `…Dto` (network and backup). Domain models have no suffix.
- Abbreviations already established stay as they are and no new ones are
  added: `Ach…` (achievements), `Dev…` (development), `Recip…` (reciprocity),
  `Owm` (OpenWeatherMap), `Ev`, `Iso`.

## Functions

`camelCase`, starting with a verb. The verb says what kind of function it is.
`MainViewModel` currently uses `save…` 25 times, `set…` 16 and `upsert…` 8 for
overlapping meanings; the picks are:

| Verb | Meaning | Returns |
|---|---|---|
| `observe…` | a stream that keeps emitting | `Flow<T>` |
| `get…` | one read from storage or network | `suspend … : T` |
| `find…` | a lookup that may miss | `T?` |
| `set…` **(pick)** | change one stored setting or one piece of state | `Unit` / `Job` |
| `add…` / `update…` / `delete…` | repository operations on one record | `suspend` |
| `upsert…` | DAO methods only | `suspend` |
| `toggle…` | flip a boolean | |
| `mark…` | move a record to a stage (`markDeveloped`) | |
| `on…` | a callback or event handler (`onMeterReading`, `onDismiss`) | |
| `is…` / `has…` / `can…` | a boolean question | `Boolean` |
| `to…` | convert to another type, new object (`toUiState`, `toEntity`) | |
| `as…` | a view of the same object | |
| `parse…` / `format…` | text to value and back; `parse…` returns null on bad input | |
| `encode…` / `decode…` | a value to its stored form and back | |
| `…OrNull` | suffix when a non-null variant also exists | |

So `saveMeterIso` becomes `setMeterIso`, and `save…` is kept for the user
pressing Save on something they built (`saveCustomTimer`). Applied as each
feature view model is extracted (refactor plan, phase 3).

- A function that launches a coroutine and returns immediately returns `Job`
  and is not `suspend`; a function that does the work is `suspend` and does
  not launch. Not both.
- Boolean parameters are named at the call site: `export(includePhotos = true)`.
- Units go in the name when the type does not carry them: `durationSec`,
  `devTempC`, `equivMm`, `calibThirds`, `timerEndElapsedMs`.

## Composables

- A composable that emits UI is a `PascalCase` noun: `RollCard`, not
  `ShowRollCard` or `rollCard`.
- A composable that returns a value is `camelCase`, and `remember…` if it
  remembers: `rememberShutterController()`.
- Parameter order: required data, then `modifier: Modifier = Modifier`, then
  optional data, then event lambdas, with a trailing content lambda last.
- Events are `on<Thing><PastOrVerb>`: `onDismiss`, `onSave`, `onOpenRoll`.
  They are named for what happened, not for what the parent will do.
- Every composable that emits layout takes a `modifier` and applies it to its
  root.
- A screen never takes a view model; a route does (see ARCHITECTURE.md).
- `CompositionLocal`s are `Local<Thing>`; design tokens are read through
  `FilmTheme.colors`, `FilmTheme.type`.
- Previews are private, named `<Composable>Preview`, and live at the bottom of
  the file.

## State and view models

- A private mutable flow has a leading underscore and its public read-only
  twin has none: `_weatherState` / `weatherState`. This is the only use of a
  leading underscore.
- Flows are named for what they hold, not for being flows: `rolls`, not
  `rollsFlow`.
- Booleans read as statements: `isMetric`, `hasBulb`, `evLocked`,
  `themeReady`. No negatives (`notReady`).
- Collections are plural; maps are `<value>By<Key>`: `filmsById`.
- IDs are `<thing>Id`, always `String` (UUID). A nullable ID means "none";
  blank strings are not used for "none" in new code.

## Constants and stored keys

- Compile-time constants and immutable top-level values holding plain data are
  `UPPER_SNAKE`: `MAX_THUMB_DIM`, `EXPIRY_WINDOW_DAYS`, `LOOK_SETTINGS`.
- Top-level values that are objects used like types — colours, shapes, text
  styles, icon specs — are `PascalCase`, as Compose does: `DyeCyan`,
  `FilmShapes`. Nothing else is.
- **Settings keys** are `lower_snake`, prefixed by area: `meter_iso`,
  `remind_expiry`, `home_order`, `ach_notify`. Each is declared once as a
  `SettingKey` (ARCHITECTURE.md) and never typed as a string literal anywhere
  else. A key is never renamed or reused for a different meaning; a changed
  meaning gets a new key (as `meter_priority` replaced `meter_mode`).
- **JSON field names** in anything stored or exported (`settings` values,
  backup files) are `lowerCamel` literals written in the codec, never derived
  from a Kotlin property by reflection.
- Notification channels, WorkManager names and intent extras are
  `lower_snake` constants on the type that owns them; unique work names are
  prefixed `analogvault_`.

## Database

- Tables: plural `lower_snake` — `films`, `bulk_rolls`, `zoom_levels`.
- Columns: existing ones are `lowerCamel` (`startDate`, `filmId`) and stay
  that way; renaming a column is a table rebuild for no gain. **New tables**
  use `lower_snake` columns through `@ColumnInfo` **(new)**, with foreign keys
  as `<table_singular>_id`.
- Indices: `index_<table>_<columns>`.
- Migrations: `MIGRATION_<from>_<to>`, one per version step, each with a KDoc
  saying what changed and why the default is what it is — as they have now.
- Dates are stored as ISO-8601 text: `yyyy-MM-dd`, `yyyy-MM-dd'T'HH:mm` for
  new columns (existing shot dates keep `yyyy-MM-dd HH:mm`), and `yyyy-MM`
  only where the day is genuinely unknown (film expiry).
- A column added to an entity gets a default that means "unknown", not a
  plausible guess.

## Resources

| Resource | Pattern | Example |
|---|---|---|
| Launcher art | `ic_launcher[_part][_palette]` | `ic_launcher_foreground_cyanotype` |
| Other drawables | `ic_<what>` icons, `bg_<what>` backgrounds, `img_<what>` pictures | `ic_notification` |
| Strings | `<feature>_<element>_<role>` | `stash_film_title`, `meter_lock_action`, `backup_import_error` |
| Long-form text | `<kind>_<topic>_<title or body>` | `technique_zone_body` |
| Plurals | as strings, with the count as `%1$d` | `rolls_awaiting_dev` |
| Fonts | `<family>_<weight>` | `space_mono_bold` |
| XML config | `<what>_rules` / `<what>_paths` | `data_extraction_rules` |
| Activity aliases | `Icon<Palette>` | `IconCyanotype` |

All `lower_snake`. String roles are `title`, `label`, `hint`, `action`,
`message`, `error`, `description` (for content descriptions). New UI text
goes in `strings.xml`; existing hard-coded text moves there in refactor step
4.5.

## Tests

- A test class is `<Subject>Test` in the same package as the subject, under
  `src/test` (JVM) or `src/androidTest` (device).
- Test names are backticked sentences stating the behaviour, subject first,
  as they are now: `` `full roll climbs its tiers` ``,
  `` `import skips photos outside the photo directory` ``. No `test` prefix.
- One behaviour per test. Table-driven cases are a list of
  `input to expected` inside one test named for the rule.
- Builders for test data are private functions named for what they build
  (`roll(…)`, `shot(…)`), with defaults for everything.
- Fakes are `Fake<Interface>`; no mocking library unless a fake is
  impractical.
- Fixtures live in `src/test/resources/fixtures/<kind>/` and are named for
  the version that wrote them: `backup/v3-0.9.1.avault`,
  `schema/7-seeded.db`. A fixture is never edited after it is committed.

## Comments

The existing style is the convention, and it is a good one:

- Comments say **why**, and what went wrong before — not what the next line
  does.
- KDoc on anything public whose behaviour is not obvious from its name; it
  states units, what null or blank means, and what is persisted.
- No commented-out code, no `TODO` without an issue number
  (`// TODO(#123): …`).
- A comment that refers to another file names the type, not the path, so it
  survives a move.
- No decorative section rules in new files (`// ─── Roll CRUD ───`); if a
  file needs them it needs splitting.

## Branches

From CONTRIBUTING.md: branch from a freshly fetched `master`, name it
`<prefix>/<short-lowercase-hyphenated-words>`, one change per branch.

| Prefix | For |
|---|---|
| `feature/` | something new a user can do |
| `fix/` | something broken that now works |
| `chore/` | build, CI, dependencies |
| `refactor/` **(new)** | restructuring with no behaviour change |
| `docs/` **(new)** | documentation only |
| `release/` **(new)** | a release pull request (`release/0.10.0`) or a maintenance line (`release/0.9.x`) |

An issue number may follow the prefix: `fix/123-backup-restore-crash`.
Branches made by tools (`claude/…`, `dependabot/…`, `renovate/…`) keep the
name the tool gives them. Branches are deleted when their pull request
merges.

## Commits and pull requests

From CONTRIBUTING.md, in short:

```text
<type>[(scope)][!]: <imperative, lowercase, no full stop, ≤ 72 characters>

<body: the problem, then what changed, then why. Wrapped at 72.>
<Say whether the Room schema, a settings key or the backup format changed.>
```

- **Types:** `feat`, `fix`, `refactor`, `perf` **(new)**, `docs`, `test`,
  `build`, `ci`, `chore`, `style`, `revert`. Choose by what changed for the
  user.
- **Scopes** **(new: fixed list)**, so history can be filtered: `home`,
  `stash`, `rolls`, `meter`, `darkroom`, `stats`, `badges`, `map`, `weather`,
  `backup`, `settings`, `theme`, `reminders`, `db`, `deps`, `release`. Omit
  the scope when a change spans several.
- **`!`** marks a change users must relearn or that breaks reading old
  backups; the body then has a `BREAKING CHANGE:` paragraph.
- Title describes what the user gets, not what the code does:
  `fix(meter): keep the picked zoom when coming back to the app`, not
  `fix(meter): reset appliedZoomRatio on camera reopen`.

**Pull requests:**

- The title follows the commit rule and is what lands on `master`.
- **Squash-merge** **(new)**. Today each pull request is merged with a merge
  commit titled the same as its one commit, so every change is in the history
  twice (`2a26c24` and `78b2d8a` are both "chore: release 0.9.1"). Squashing
  gives one commit per change, titled by the pull request.
- The description says what changed and why, what was tested and what was
  not, and has screenshots for UI changes.
- One change per pull request. A refactor that moves files and a change to
  their contents are separate commits (and survive the squash as separate
  pull requests when large).
- Draft until it builds and has been run on a device.

## Changelog entries

From CONTRIBUTING.md: every `feat` and `fix` adds a line under
`## [Unreleased]` in `Added`, `Changed`, `Fixed` or `Removed`, written for
someone using the app.

Additional guidance **(new)**:

- One or two sentences. Say what the user can now do and where to find it;
  the 0.9.0 entries run to 150 words each and read as documentation, which
  belongs in the README.
- Lead with a bold name only for a headline feature.
- Use `Deprecated` and `Security` sections when they apply; a fix for a
  finding that could expose data goes under `Security`.
- Anything that changes what a backup contains, or what an older version can
  import, says so in the entry.

## Tags and releases

- Release tags are `v<MAJOR>.<MINOR>.<PATCH>`, annotated, on a commit on
  `master` whose `appVersion` equals the tag. Pre-releases are
  `v<X.Y.Z>-beta.<N>`.
- The release commit is `chore(release): <X.Y.Z>` **(pick)** — the history
  has both that and `chore: release <X.Y.Z>`.
- The APK is `AnalogVault-v<X.Y.Z>.apk`; the GitHub release is
  `Analog Vault v<X.Y.Z>`.

The process is in [VERSIONING.md](./VERSIONING.md).

## Proposed amendments to CONTRIBUTING.md

None of these is applied yet; each is a small edit once agreed.

1. Add `refactor/`, `docs/` and `release/` branch prefixes.
2. Add `perf` and `revert` to the commit types, and the fixed scope list.
3. State that pull requests are squash-merged, and turn off merge commits in
   the repository settings.
4. Add "run `./gradlew testDebugUnitTest`" to *Before opening a PR* — the
   tests are not mentioned anywhere in the repo's documentation.
5. Replace the *Releases* section ("not settled yet") with the process in
   VERSIONING.md.
6. Replace "Nothing here is enforced by a bot" once the checks in
   VERSIONING.md exist: a pull-request title check and a changelog check are
   two short workflow steps. 46 of the 99 non-merge commits on `master` do
   not follow the title rule. All but two predate it (it was introduced on
   2026-10-18), so the rule is being kept; the check only makes that
   automatic.
7. Link to this document for code naming.
