# Documentation review

A review of everything in the repo that documents the app or the project, at
`master` `90db310`: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `LICENSE`
and `app/licenses/`, the comments in the build and workflow files, code
comments and KDoc, and the in-app technique cards. Each claim in the README
was checked against the code.

Nothing was edited. Fixes are listed at the end in the order worth doing
them; most are a few lines each.

## Contents

- [Summary](#summary)
- [README.md](#readmemd)
- [CONTRIBUTING.md](#contributingmd)
- [CHANGELOG.md](#changelogmd)
- [Licences and attribution](#licences-and-attribution)
- [Comments in build and workflow files](#comments-in-build-and-workflow-files)
- [Code comments and KDoc](#code-comments-and-kdoc)
- [In-app text](#in-app-text)
- [What is missing](#what-is-missing)
- [Fix list](#fix-list)

## Summary

| Document | State | Main issue |
|---|---|---|
| `README.md` | Good, clear, slightly behind | Does not mention anything added in 0.9.0; three statements disagree with the code |
| `CONTRIBUTING.md` | Good | Release process undecided; tests not mentioned |
| `CHANGELOG.md` | Good since 0.6.5, unstructured before | Two released versions missing; entries too long; a joke section |
| `LICENSE`, `app/licenses/` | Present | Font licences and map attribution do not reach the user |
| Code comments | Unusually good | A handful are stale or detached |
| In-app technique cards | Good | — |

The writing is consistently plain and specific. The problems are drift (the
documents have not followed the last few releases) and gaps (things a user or
contributor needs that are written nowhere).

## README.md

### Out of date

| Where | Says | Code |
|---|---|---|
| Features | No mention of achievements, badges, palettes, app icons, grain and iris styles, edge print, technique cards or badge sharing | All shipped in 0.9.0; a whole Stats tab |
| Features → Other | "Safelight (red) colour scheme" | Six palettes plus Safelight, a saturation control |
| Navigation table, Stats | "Tabs: Numbers, Habits" | Numbers, Habits, Badges |
| Navigation, More list | Stats: "numbers, habits, shot map" | also badges |
| Features → Light meter | Zoom not mentioned | Focal presets, a zoom slider across the phone's lenses, per-lens aperture clicks, in-between apertures |
| Project structure | `MainActivity.kt   Entry point…` | The file is `VaultActivity.kt`; `MainActivity` is only a launcher alias |
| Project structure | `work/ReminderWorker.kt` | also `AchievementNotifier.kt` |
| Project structure | `util/  Exposure maths, development time, gear limits, constants` | also achievements, rewards, sun times, photos |
| Project structure | — | `data/camera/`, `ui/share/` and `app/src/test/` are not listed |

### Disagrees with the code

1. **Permissions → "Photos / media: Selecting an existing photo for a
   shot".** The photo is picked with `GetContent`, which needs no permission,
   and the app never requests one. The manifest declares
   `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`
   without using them (CODE_REVIEW S6). Remove the row when the permissions
   go; until then the table also omits the storage-write permission it does
   declare.
2. **Reminders → "Finished roll not developed: 3 weeks or more".**
   `ReminderWorker.checkUndeveloped` counts 21 days from the roll's *load*
   date (`startDate`), not from when it was finished. A roll loaded a month
   ago and finished yesterday is reported at once. Either the code should use
   `finishedAt` (it exists since schema 14) or the README should say "loaded
   3 weeks ago or more". The code should change.
3. **Permissions → "Notifications: Reminders".** Also used for achievements
   earned in the background.

### Incomplete

4. **Data storage and backup** says what a backup is but not what is in it.
   It contains the inventory, rolls, chemistry, zoom presets, saved timers
   and agitation patterns, achievements, the chosen look and the API key. It
   does **not** contain the other settings: currency, units, meter
   calibration, custom ISOs, home layout, reminder choices, pinned place,
   development temperature. Someone moving to a new phone finds their meter
   uncalibrated. Document it — and consider it a product gap: calibration at
   least belongs in the backup, perhaps behind a "this device only" note.
5. **Privacy.** "Network requests are made to OpenWeatherMap … and to
   OpenStreetMap" is accurate but thin. Worth one short section: what is
   sent (coordinates and the API key to OpenWeatherMap; tile requests, and so
   roughly where the map is looking, to OpenStreetMap's servers; location
   through Google Play Services), that exports contain GPS coordinates and
   the API key, and that Android's cloud backup holds the same.
6. **Building from source** does not mention the unit tests
   (`./gradlew testDebugUnitTest`) or lint.
7. **Installation** gives no way to verify the APK (see VERSIONING.md, V6).
8. **Release build** documents that an unset `KEYSTORE_PATH` signs with the
   debug key. Accurate; the behaviour should change (CODE_REVIEW S4) and this
   sentence with it.
9. **No screenshots.** For an app whose appeal is largely how it looks, one
   row of four images near the top would do more than any paragraph.
   `store-assets/` has only the icon.

### Fine as it is

Installation, basic usage (the seven steps match the app), optional
configuration, the reminder thresholds and re-notify interval, SDK levels,
the stack list, the debug application ID and label, and the More menu order
all match the code.

## CONTRIBUTING.md

Clear and proportionate. Points to change, most already listed under
*Proposed amendments* in [CONVENTIONS.md](./CONVENTIONS.md):

1. **Releases** is explicitly unfinished ("not settled yet"). The cost has
   been real: four of the last six tags were built with the wrong
   `appVersion` ([VERSIONING.md](./VERSIONING.md), V1). Replace the section
   with the agreed process.
2. **Before opening a PR** does not say to run the tests. Seven test classes
   exist and nothing in the repo's documentation mentions them.
3. **Branches** has three prefixes while the commit types allow eight; a
   documentation-only or refactor branch has to be called `chore/`. Either
   say so outright or add `docs/` and `refactor/`.
4. **Merge method** is not stated. Merge commits are in use and each repeats
   its pull request's title, so history lists every change twice.
5. **"Nothing here is enforced by a bot."** True, and since the rules were
   introduced on 2026-10-18 all but two commit titles follow them — but the
   version and changelog rules, which are harder to get right by hand, have
   not held.
6. **Schema and backup changes.** The rule to state them in the commit body
   is good. Add what must accompany them: a migration, the exported schema
   file, and (once they exist) a migration test and a backup fixture.
7. No mention of where the architecture and naming rules are; link `docs/`.
8. No guidance on reporting a security problem (see
   [What is missing](#what-is-missing)).

## CHANGELOG.md

1. **Two released versions have no section**: `v0.6.7` and `v0.7.0` are
   tagged; the file goes 0.6.6 → 0.6.8 and 0.6.9 → 0.7.1.
2. **Patch releases that add features**: `[0.6.6]`, `[0.6.8]` and `[0.6.9]`
   have `Added` sections, against the versioning rule the preamble points to.
   History cannot be changed, but it is worth a note so the pattern is not
   copied.
3. **The first 180 lines of history have no version headings.** Below
   `[0.6.5]` the file switches to bold topic headings ("Light meter
   overhaul", "Code rework and fixes") with nested bullets and no dates. The
   release-notes script cannot use them, and a reader cannot tell which
   version anything arrived in. Where the old tags' release notes allow, give
   them `## [x.y.z] - date` headings; otherwise put the lot under one
   `## Before 0.6.5` heading.
4. **The file ends with "Known bugs — now I really don't know, make an issue
   if you find any".** Remove it; known bugs belong in the issue tracker.
5. **Entries are too long.** Several 0.9.0 entries are 100–200 words in a
   single bullet (the Achievements entry explains the whole feature). A
   changelog entry should say what changed in a sentence or two; the
   explanation belongs in the README, which currently says nothing about the
   same feature.
6. **Developer-facing entries in older sections** ("Set `isShrinkResources =
   true`", "Removed unused variables … and a stale version-catalog alias",
   "Added migration to schema v7"), contrary to "write it for someone using
   the app". Leave the history; do not repeat it.
7. **No compare links** at the foot (`[0.9.1]: …/compare/v0.9.0...v0.9.1`),
   which Keep a Changelog recommends and which make each heading clickable.
8. **Sections share dates**: `[0.6.5]`, `[0.6.6]` and `[0.6.8]` carry one
   date and `[0.6.9]` and `[0.7.1]` another, which suggests they were dated
   after the fact. No action; it is one more reason to automate the dating.

The `[Unreleased]` section and everything from `[0.7.1]` up is well formed.

## Licences and attribution

1. **`LICENSE`** is MIT, © 2026 mattnoir. Fine.
2. **Font licences.** The three OFL texts are in `app/licenses/`, which is
   not packaged into the APK. The SIL Open Font License asks that the
   copyright notice and licence accompany each copy of the fonts; an APK
   that bundles them should carry the text, normally on an in-app "Licences"
   screen. There is none.
3. **OpenStreetMap attribution.** The tile usage policy and the ODbL require
   "© OpenStreetMap contributors" to be visible on the map. No such text or
   `CopyrightOverlay` was found in `ShotMapScreen.kt`. Check on a device; if
   it is absent, add it — this is the one item here with a compliance edge.
4. **Third-party libraries.** Retrofit, OkHttp, Gson, Coil, Hilt, osmdroid
   and AndroidX are Apache-2.0, which asks for the licence and notices to be
   included in redistributions. The same Licences screen covers it; the
   `oss-licenses` or AboutLibraries Gradle plugins generate the list.
5. **OpenWeatherMap** asks for attribution where its data is shown. The
   Weather screen should say "Weather data by OpenWeather".
6. The README mentions the font licences; it should mention the map and
   weather data sources' terms in the same place.

## Comments in build and workflow files

| File | Comment | Problem |
|---|---|---|
| `gradle.properties` | "The actual Exynos/Mali fix lives in MainActivity (preferredRefreshRate)" | The class is `VaultActivity` |
| `gradle.properties` | "Enabled parallel sync for Gradle 9.4+" | The wrapper is Gradle 8.14.4, so by the comment's own account the flag does nothing yet |
| `ui/components/MapSupport.kt` | `initOsmdroid` "done in VaultActivity.onCreate" | It is called from `AnalogVaultApp.onCreate` |
| `.github/workflows/release.yml` | "To publish: git tag v1.0.0 && git push --tags" | Omits the two steps that have been missed in practice: bump `appVersion`, date the changelog |
| `.github/workflows/release.yml` | Step comment "always available, even without a tag" | On a manual run `GITHUB_REF_NAME` is a branch name, so the APK is named `AnalogVault-master.apk` and is still release-signed |
| `.gitignore` | `CHAGNELOG.md` | A typo kept as an ignore rule; delete the line |
| `app/build.gradle.kts` | `// needed for Theme.AppCompat`, `// For LocalLifecycleOwner (moved here in newer versions)` | Fine, but version-specific; re-check at the next dependency upgrade |

The long keystore how-to inside `release.yml` is useful and belongs in
CONTRIBUTING.md or a `docs/RELEASING.md`, where someone would look for it.

## Code comments and KDoc

The comments are a strength of this codebase: they explain intent, record the
bug that led to the code, and state what blank, null and zero mean for stored
fields. `AppDatabase.kt`'s migration KDoc and `Models.kt`'s field
documentation are the model to follow.

Problems found:

1. **Detached KDoc in `MainViewModel.kt`.** Around line 790 the KDoc for
   `devTempC` ("Working temperature of the chemistry…") sits directly above
   the KDoc for `safelight`, a hundred lines from the property it describes.
   Around line 133, "Thirds grid over the meter's preview. On unless switched
   off." is followed by the camera-rig comment and property; `_meterGrid` is
   fifteen lines further down.
2. **Stale description.** `rolledCount`: "match by developer name or just
   total developed" describes an older implementation; it now delegates to
   `Achievements.chemicalRollCount`.
3. **A comment that describes what the code does not do.**
   `BackupManager.importLegacyJson` has a long comment on why the JSON must be
   streamed rather than loaded whole; the caller reads the whole file into a
   `String` first (CODE_REVIEW S2).
4. **Comments standing in for structure.** `// ─── Roll CRUD ───` rules
   divide 1 400-line files into what should be separate files. They go as the
   files are split.
5. **"Non-private: also used by MainViewModel.quickLogShot"** on functions in
   `ActiveScreen.kt` — an accurate note of a wrong dependency (a view model
   calling into a screen file); fixed by refactor step 2.2.
6. **No KDoc on the two most-used public types.** `VaultRepository` and
   `MainViewModel` have no class-level description. They are being broken up;
   their successors should each say in two lines what they own.
7. **About 120 fully qualified names inline** make signatures hard to read
   and hide what a file depends on. Not a comment problem, but it is the
   same readability cost.

## In-app text

- **Technique cards** (`res/values/technique_cards.xml`) are accurate,
  practical and well written. They are the only strings in resources.
- **All other UI text is hard-coded** in composables, so it cannot be
  reviewed in one place, checked by lint for consistency, or translated.
  Terms vary slightly across screens ("Rolls" / "Loaded" / "Active" for the
  same tab in code and comments; "Develop log" / "Dev log"). A pass through
  `strings.xml` (refactor step 4.5) is where a glossary would be enforced.
- **Error messages show raw exception text**: `"Import failed: ${e.message}"`
  and `e.message ?: "Network error"` put strings like
  `Unable to resolve host "api.openweathermap.org"` or a Gson parser message
  in front of the user. Map failures to written messages.
- The More screen's Settings subtitle ("OWM key, currency, units, safelight,
  reminders") predates palettes and achievements.

## What is missing

| Document | Why | Size |
|---|---|---|
| `SECURITY.md` | Where to report a vulnerability privately; which versions get fixes. GitHub surfaces it automatically. | 15 lines |
| `.github/pull_request_template.md` | CONTRIBUTING.md asks for what was tested, screenshots, and schema/settings/backup changes; a template makes that the default. | 15 lines |
| `.github/ISSUE_TEMPLATE/` (bug, feature) | A bug report needs app version, Android version, device and — for meter bugs — phone model. The app is device-sensitive (Exynos/Mali, lens data). | 2 short files |
| Privacy note (README section or `PRIVACY.md`) | Required by Google Play if the app is ever listed, since it uses camera and location; useful regardless. | 20 lines |
| Backup format specification (`docs/BACKUP_FORMAT.md`) | The `.avault` format is a public interface with three historical versions and a reader for each; it exists only as code. Writing it down is also the test plan for refactor step 0.3. | 1 page |
| Release instructions (`docs/RELEASING.md`) | The steps, the secrets and the keystore procedure now split between CONTRIBUTING.md and comments in `release.yml`. | half a page |
| In-app Licences screen | Fonts, libraries, map and weather data. | — |
| User guide | The README's "Basic usage" is enough for now. The meter (calibration, zones, reciprocity, gear clamping) and the darkroom (temperature scaling, kits, agitation) have enough depth to justify a page each once the README outgrows them. | later |
| Device compatibility notes | Which phones report lens data, known camera quirks. Lives well in a GitHub Discussion or wiki rather than the repo. | later |

Documents added by this pull request, for completeness:
[CODE_REVIEW.md](./CODE_REVIEW.md), [ARCHITECTURE.md](./ARCHITECTURE.md),
[REFACTOR_PLAN.md](./REFACTOR_PLAN.md), [CONVENTIONS.md](./CONVENTIONS.md),
[VERSIONING.md](./VERSIONING.md). `CODE_REVIEW.md` and this file are
snapshots of one commit and will go stale; once their findings are filed as
issues they can be deleted or moved to `docs/archive/`. The other three are
meant to be kept current.

## Fix list

In order of value for effort.

1. **Check the map for "© OpenStreetMap contributors"** and add it if absent.
2. **README, ten minutes:** `MainActivity.kt` → `VaultActivity.kt`; Stats
   tabs; one Features block for achievements and palettes; the two
   permissions rows; add the test command.
3. **README, backup contents:** say what a backup does and does not include.
4. **Reminders:** age undeveloped rolls from `finishedAt`, falling back to
   the last shot's date and then `startDate`. (A code fix that makes the
   README true.)
5. **CHANGELOG:** add `[0.7.0]` and `[0.6.7]`, remove "Known bugs", add
   compare links.
6. **CONTRIBUTING:** tests before a pull request; merge method; link `docs/`.
7. **Add** `SECURITY.md`, the pull request template and two issue templates.
8. **Fix the stale comments** in the table above and the three in
   `MainViewModel.kt`.
9. **Licences screen** in Settings, with fonts, libraries and data sources.
10. **Screenshots** in the README.
11. **Write `docs/BACKUP_FORMAT.md`** alongside the backup fixtures.
12. **Decide the release process** and replace CONTRIBUTING's *Releases*
    section.
