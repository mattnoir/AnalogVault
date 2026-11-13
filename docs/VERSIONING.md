# Versioning and releases

A review of how versions are set and releases are cut today, and a proposal
to replace the manual steps. CONTRIBUTING.md says the release process "is not
settled yet"; this is a suggestion for settling it. Nothing here is
implemented.

## Contents

- [How it works today](#how-it-works-today)
- [What works](#what-works)
- [Problems](#problems)
- [Proposal](#proposal)
- [Version numbers that are not the app version](#version-numbers-that-are-not-the-app-version)
- [Adoption order](#adoption-order)

## How it works today

1. `val appVersion = "0.9.1"` in `app/build.gradle.kts` is the only place the
   version is written. `versionName` is that string; `versionCode` is
   `major × 1 000 000 + minor × 1 000 + patch`.
2. Feature and fix pull requests leave it alone and add a line to
   `## [Unreleased]` in `CHANGELOG.md`.
3. To release, someone edits `appVersion`, renames `[Unreleased]` to
   `[X.Y.Z] - date`, commits, tags `vX.Y.Z` and pushes the tag.
4. The tag triggers `release.yml`, which builds a signed APK and creates a
   GitHub release with notes taken from the changelog by
   `.github/scripts/release_notes.py`.

## What works

- One source of truth for the version, with `versionCode` derived from it.
- Semantic Versioning with a stated rule for what moves which number.
- Keep a Changelog, written for users, and release notes generated from it.
- Debug builds get their own application ID, label and version suffix.

Keep all of it.

## Problems

Checked against the 37 tags on the remote and the changelog on `master`.

**V1. Nothing ties the tag, `appVersion` and the changelog together.** They
are three hand edits. A tag pushed before the bump produces
`AnalogVault-v0.9.2.apk` containing version 0.9.1, which a device will not
accept as an update over 0.9.1. The release-notes script has a fallback for a
tag cut before its section was dated — a workaround for exactly this.

This has happened on four of the last six releases. `appVersion` in the
build file at each tag:

| Tag | Tagged | `appVersion` in the build | `versionCode` shipped |
|---|---|---|---|
| `v0.7.0` | 2026-10-18 | `0.6.0` | 6 000 |
| `v0.6.9` | 2026-10-25 | `0.6.0` | 6 000 |
| `v0.7.1` | 2026-10-25 | `0.7.0` | 7 000 |
| `v0.8.0` | 2026-11-01 | `0.7.1` | 7 001 |
| `v0.9.0` | 2026-11-08 | `0.9.0` | 9 000 |
| `v0.9.1` | 2026-11-10 | `0.9.1` | 9 001 |

So the APKs named 0.6.9 and 0.7.0 both report themselves as 0.6.0 with the
same `versionCode` — neither installs as an update over the other — and
`v0.6.9` was tagged a week *after* `v0.7.0`. The last two releases are
correct, by care rather than by construction.

**V2. It has already drifted.**

- Tags `v0.6.7` and `v0.7.0` exist; the changelog has no section for either.
- `[0.6.9]` and `[0.7.1]` carry the same date, and `[0.6.9]` adds features
  (meter frame shape, manual mode). Which of those entries shipped in the APK
  tagged `v0.7.0` a week earlier cannot be told from the file.
- `[0.6.6]`, `[0.6.8]` and `[0.6.9]` each have an `Added` section. By the
  rule in CONTRIBUTING.md a release that adds a feature is a minor bump, not
  a patch.
- Everything before `[0.6.5]` (about 180 lines) has no version headings at
  all, and the file ends with a "Known bugs" section that is a joke entry.

**V3. The same release appears twice in history.** `chore: release 0.9.0`,
`chore: release 0.9.1` and `chore(release): 0.7.0` each exist as two commits,
because pull requests are merged with merge commits that repeat the title.
The script's own comment notes that "releases have been cut from more than
one branch". Two spellings of the release commit title are in use.

**V4. A pre-release has the same `versionCode` as its final.** The code is
computed from `appVersion.substringBefore('-')`, so `1.0.0-beta.1`,
`1.0.0-beta.2` and `1.0.0` are all 1 000 000. A device, and Google Play,
treat them as the same build: a beta tester cannot update to the final.

**V5. Releases are not gated.** The workflow runs no tests and no lint, can
be started by hand from any branch, and publishes as soon as the build
succeeds. If the signing secret were missing locally the build would fall
back to the debug key (CODE_REVIEW S4).

**V6. No published way to verify a download.** The APK is distributed from
GitHub releases and side-loaded; there is no checksum in the release notes
and the signing certificate's fingerprint is not documented.

**V7. No stated route for a fix to an old release,** and no statement of what
1.0 means. Both matter once the backup format is something users depend on.

## Proposal

Three levels. Each is useful without the next.

### Level 1 — Checks (an hour)

Make the existing manual process unable to go wrong silently.

**In `release.yml`, before building:**

```bash
VERSION="${GITHUB_REF_NAME#v}"
grep -q "val appVersion = \"$VERSION\"" app/build.gradle.kts \
  || { echo "::error::tag $GITHUB_REF_NAME does not match appVersion"; exit 1; }
grep -q "^## \[$VERSION\] - [0-9-]\{10\}$" CHANGELOG.md \
  || { echo "::error::CHANGELOG.md has no dated section for $VERSION"; exit 1; }
git merge-base --is-ancestor "$GITHUB_SHA" origin/master \
  || { echo "::error::release tags must be on master"; exit 1; }
```

Then run `./gradlew testDebugUnitTest lintRelease` before `assembleRelease`,
and add `sha256sum` of the APK to the release notes. With the first check in
place, the fallback path in `release_notes.py` can be deleted.

**On pull requests (in the CI workflow from the refactor plan, step 0.1):**

- The title matches
  `^(feat|fix|refactor|perf|docs|test|build|ci|chore|style|revert)(\([a-z]+\))?!?: [a-z]`.
- A `feat` or `fix` pull request changes `CHANGELOG.md`.
- A pull request other than a release one does not change `appVersion`.

**Repository settings:** allow squash merging only, default the squash
commit title to the pull request title, protect `master`, and restrict who
can push `v*` tags.

### Level 2 — One command to release (half a day)

Replace the three hand edits with a workflow started from the Actions tab
(`workflow_dispatch`) with one input, `bump`: `auto`, `patch`, `minor` or
`major`. It:

1. Reads `appVersion`.
2. For `auto`, reads the `[Unreleased]` section: any entry under `Added`,
   `Changed` or `Removed` means minor, only `Fixed` or `Security` means
   patch, and an empty section stops with an error. A major bump is always
   chosen by hand.
3. Rewrites `appVersion`, renames `[Unreleased]` to `[X.Y.Z] - <today>`,
   inserts a fresh empty `[Unreleased]`, and updates the compare links at the
   foot of the changelog.
4. Opens a pull request `chore(release): X.Y.Z` from `release/X.Y.Z`.

Merging that pull request triggers a second job that tags the merge commit
`vX.Y.Z` and runs the existing build-and-publish steps. Nobody types a
version number or a tag, the changelog cannot be forgotten, and the release
is reviewed like any other change.

The script is about sixty lines of Python beside `release_notes.py`, and
should have the same kind of unit tests as the app's pure logic.

*Alternative:* [release-please](https://github.com/googleapis/release-please)
does this off the shelf, but it writes the changelog from commit titles. This
project's changelog is hand-written for users and is better for it; keep
that, and keep the small script.

### Level 3 — Details worth settling

**`versionCode` with room for pre-releases (fixes V4).**

```text
versionCode = major × 1 000 000 + minor × 10 000 + patch × 100 + stage

stage:  1–98 for -beta.N (N)    99 for a final release
```

`0.9.1` is 9 001 today; `0.10.0` becomes 100 099 under the new scheme, so the
code still only goes up. `1.0.0-beta.2` is 1 000 002 and `1.0.0` is
1 000 099. Limits: minor and patch up to 99, which the `require` in the build
file should enforce. Make the switch at a minor bump and note it in the
commit.

**Identify debug builds.** Append the short commit hash to the debug
`versionNameSuffix` (`0.9.1-debug+90db310`) and show the version name in
Settings, so a bug report says exactly what was running.

**Annotated, signed tags.** `git tag -s vX.Y.Z -m "Analog Vault X.Y.Z"`, or
let the release job create the tag so that it is always made the same way.

**Verification (fixes V6).** Put the APK's SHA-256 in each release's notes,
and the signing certificate's SHA-256 fingerprint in the README under
*Installation*, with the one-line `apksigner verify --print-certs` command.

**Fixing an old release (V7).** `master` is the only long-lived branch. If a
fix is needed for a release that `master` has moved past, branch
`release/X.Y.x` from the tag, cherry-pick the fix from `master` (fix on
`master` first), and release `X.Y.(Z+1)` from that branch. The tag check in
level 1 then allows `release/*` as well as `master`.

**What 1.0 means.** Suggested: the backup format and the Room schema are
supported for the whole 1.x line — any 1.x can import a backup written by any
earlier 1.x, and upgrade from any 1.x database — and that is what a major
bump is reserved for breaking. Before 1.0, CONTRIBUTING.md's rule (a breaking
change is a minor bump) stands.

**Repair the changelog once (fixes V2).** Add the missing `[0.7.0]` and
`[0.6.7]` sections from their tags' release notes, correct `[0.6.9]` if it is
in fact 0.7.0, give the pre-0.6.5 text version headings where the tags allow
(or fold it under one `## Before 0.6.5` heading), remove "Known bugs", and
add compare links at the foot:

```markdown
[Unreleased]: https://github.com/mattnoir/AnalogVault/compare/v0.9.1...HEAD
[0.9.1]: https://github.com/mattnoir/AnalogVault/compare/v0.9.0...v0.9.1
```

## Version numbers that are not the app version

The app carries three more versions, and they change on their own schedule.
Each release should record them, because "which app version can read this
file" is the question a user with an old backup asks.

| Number | Where | Now | Changes when |
|---|---|---|---|
| App version | `appVersion` in `app/build.gradle.kts` | 0.9.1 | every release |
| Room schema | `@Database(version = …)` | 14 | a table or column changes; needs a migration and a migration test |
| Backup format | `VaultBackup.version` | 3 | the file's shape changes in a way an older reader cannot ignore |
| Settings JSON | implicit, per key | — | a stored value's shape changes; needs a tolerant decoder and a fixture |

Proposed rules:

- Adding an optional field to the backup does **not** bump the backup
  version; older readers ignore it and newer readers default it. Removing or
  re-meaning a field does.
- A reader refuses a backup version higher than it knows, with a message that
  says to update the app (CODE_REVIEW S8).
- A table in the README (or the changelog's preamble) maps them:

  | App | Schema | Backup |
  |---|---|---|
  | 0.9.x | 14 | 3 |
  | 0.6.9 – 0.8.0 | 11 | 3 |

- A changelog entry for a release that bumps the schema or backup version
  says so under `Changed`.

## Adoption order

1. Level 1 checks in `release.yml` and repository settings — before the next
   release.
2. Repair the changelog.
3. Pull-request title and changelog checks, with the CI workflow.
4. Level 2 release workflow.
5. `versionCode` scheme, at the next minor bump.
6. Checksums and certificate fingerprint.
7. Write down the 1.0 promise when 1.0 is in sight.
