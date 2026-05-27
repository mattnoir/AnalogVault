[v0.2.5a] - 2026-05-27

- now finally fixed the critical bug crashing app due to DB changes
- some code cleanup



[v0.2.5 - v0.2.1] - 2026-05-27

- fixed critical bug where adding a camera crashed the whole app without option of recovery
- Film ISO override for shooting other than box speed
- added bulk film tracker
- film formats now include number of shots
- Making sub-tabs (like film/camera/lens etc) swipable
- Drawer not swipable currently due to map nav issues (will properly patch it later). Exit drawer by tapping outside or swiping away should work
- App DB migration to v3 also due to issues with camera and adding bulk rolls
- backups can now include photos from shot rolls
- reworked weather to show best roll from stash for shooting in current weather (time of day aware)
- some performance tweaks

Known bugs:
- number of frames per roll in stash format still not visible
- UI and UX are a mess unfixable with LLM
- tapping outside drawer doesn't close it on some devices
- swipe to dismiss not working as intentional, but at least there is X button
- maybe still 0.6x zoom not zooming
- You tell me (really, please, I only test on one phone)

  

[v0.2] - 2026-05-23

- Map now showing tiles
- 1/3 stops options for ISO/shutter/aperture
- you can now load rolls from stash and stash shows rolls that are in camera
- burger menu now openable only through "more" or by tapping on burger menu
- ISO and lenses are now automatically selected for shot log
- rotating screen does not dismiss windows
- stash has filters now and instead of number of shots shows format
- more stocks available
- in stash, selectable month and year is available instead of previous year month day

Known bugs:
- navigation is still choppy despite 120fps refresh rate available
- formats are not enough, need number of shots too
- ...



[v0.1] - 2026-05-19

- Complete overhaul of Light meter for actual metering
- Stash add button is on the top
- some bug fixes

Known bugs:
- navigation slow and locked to 60fps
- metering modes don't seem to do anything


[v0.0.1 - 0.0.5]

- added home screen
- added timers
- added suggested rolls and automatically adjusts ISO and brand
- improved navigation and backswipe/backbutton action
- hamburger menu
- automatic weather notes in shot log
- backup/restore

- various bug fixes
