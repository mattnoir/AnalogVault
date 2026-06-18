[v0.3.5] - 2026-06-18
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
  - Corrected misleading comments around bottom sheet dismissal behaviour.


- **UI Improvements**
  - Added missing borders to roll cards for visual consistency with other app surfaces.
  - Improved card styling consistency across the app.


- **Versioning**
  - Versioning now works and is not stuck at 1.0
