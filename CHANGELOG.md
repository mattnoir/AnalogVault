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