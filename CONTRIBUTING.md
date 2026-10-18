# Contributing to Analog Vault

These are the naming and versioning conventions for the repo. They are kept
deliberately small: they decide what a change is called, how the version number
moves, and what goes in the changelog. Nothing here is enforced by a bot.

## Branches

Work happens on **`redesign`**; pull requests target it. Branch off a freshly
fetched copy and name the branch after the kind of change:

| Prefix | For | Example |
|---|---|---|
| `feature/` | something new a user can do | `feature/custom-agitation` |
| `fix/` | something broken that now works | `fix/backup-restore-crash` |
| `chore/` | build, CI, docs, refactors, dependencies — nothing a user sees | `chore/versioning-conventions` |

Use short lowercase words joined by hyphens after the prefix. One feature or fix
per branch.

## Commit messages and PR titles

Titles use [Conventional Commits](https://www.conventionalcommits.org) types,
followed by a plain imperative sentence that says what the user gets:

```
feat: let each darkroom step carry its own agitation rhythm
fix: keep the meter running after a tab switch
chore: derive versionCode from the app version
```

| Type | Use it when | Version effect at the next release |
|---|---|---|
| `feat:` | a user gains a new capability | minor (`0.6.0` → `0.7.0`) |
| `fix:` | something that was broken now works | patch (`0.6.0` → `0.6.1`) |
| `feat!:` / `fix!:` | an existing behaviour changes in a way users must relearn, or old backups stop importing | minor while below 1.0, major from 1.0 on |
| `chore:` `refactor:` `docs:` `build:` `ci:` `test:` `style:` | nothing a user would notice | none by itself |

Pick the type by what changed for the user, not by the size of the diff. A
scope is optional and, when used, names the screen or area:
`feat(darkroom): …`, `fix(backup): …`.

The title is lowercase after the colon, imperative ("let", "keep", "add"), no
trailing period, around 50–70 characters. The body is prose wrapped at ~72
columns: the problem first, then what changed, then why. Always say whether the
Room schema, a `settings` key or the backup format changed, and how restore
treats the new data.

The **PR title** follows the same rule and is usually the same as the main
commit's title. GitHub puts it in the merge commit, so it is what the release
history is read from.

## Versions

Analog Vault uses [Semantic Versioning](https://semver.org): always three
numbers, `MAJOR.MINOR.PATCH`.

- The version lives in **one place**: `val appVersion` at the top of
  `app/build.gradle.kts`. `versionName` is that string, and `versionCode` is
  derived from it (`major × 1 000 000 + minor × 1 000 + patch`), so the two can't
  drift apart and the code only ever goes up.
- Between releases `appVersion` is the **last released** version. Feature and
  fix PRs do not touch it — only a release commit does.
- Debug builds add `-debug` to the version name automatically.
- Pre-releases, if ever needed, are `X.Y.Z-beta.N`. Letter suffixes such as
  `0.2.5a` and two-part versions such as `0.3` are not used any more.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com). Every
user-visible PR (`feat`, `fix`) adds a line under `## [Unreleased]`, in one of
`### Added`, `### Changed`, `### Fixed` or `### Removed`. Write it for someone
using the app, not for someone reading the code. `chore` PRs don't need an entry.

## Releases

How releases are cut, tagged and built is not settled yet: the existing
"Build & Release APK" workflow is tied to `master`, while work now lands on
`redesign`. Until that is sorted out, the only rule is the one above — a
version bump (`appVersion` plus dating the `[Unreleased]` changelog section)
happens in its own commit, never inside a feature or fix PR.

## Before opening a PR

- Run a debug build (`./gradlew :app:assembleDebug`) and try the change on a device.
- Include screenshots for visible UI changes.
- In the PR description, list what was tested and leave unchecked anything that
  wasn't.
