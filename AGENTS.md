# AGENTS.md - context for AI agents and new developers

Read this first, then [`docs/HISTORY.md`](docs/HISTORY.md) for why things are the way they are.

## What this is
Personal Android app (Kotlin, Jetpack Compose, Material 3) for one owner's **Hoco E96** tracker, a
Google Find Hub (FMDN) tag. Two halves:
- **Bluetooth half**: scan, hot/cold finder, favorites, left-behind alerts, find-my-phone.
- **Find Hub half**: an embedded **Python** engine (GoogleFindMyTools + Tag Tracker Open server code,
  GPL-3.0) run through **Chaquopy**, giving map, history, alerts and backups.

Owner is not a developer: they install the APK and report what they see. **No physical device has
been used during development**; say so honestly when reporting results.

## Build, test, lint
```bash
# needs JDK 17, Android SDK (platform 35), Python 3.13 on PATH, local.properties with sdk.dir
./gradlew assembleRelease testDebugUnitTest lintDebug --console=plain -q
# APK delivered to the owner = app/build/outputs/apk/release/app-release.apk (R8, debug-signed)
```
- Lint must have **0 errors** (it fails the build); warnings are tolerated.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` **and** the version text at the bottom
  of `ui/MoreScreen.kt` for every delivered build. Current: 15 / "4.7.1".
- Release is signed with the debug key on purpose so new APKs install over old ones. If you change the
  signing key, the owner must uninstall (and loses local data) - ask first.
- Robolectric/screenshot tests were not set up (Maven Central throttling in the build environment).
- Python can be sanity-checked on the host with a venv (`pycryptodomex`, `ecdsa`) and `python -I`,
  adding `app/src/main/python` and `third_party/GoogleFindMyTools` to `sys.path`.

## Code map (`app/src/main/java/com/example/trackerfinder/`)
| File | Role |
| --- | --- |
| `MainActivity.kt` | Permissions, app lock gate, tabs (`Tab` enum: HOME, NEARBY, MAP, FAVORITES, MORE), scan-mode per tab, keep-screen-on in finder, BT state receiver, deep links (`EXTRA_FAVORITE_ID`, `EXTRA_HUB_DEVICE_ID`). |
| `ScanViewModel.kt` | BLE scanning (`ScanMode` FAST/BALANCED/OFF + boost), device map (published every 500 ms), finder state, favorites, beep/vibrate. Feeds `BeaconBattery` and `FindPhone`. |
| `Advert.kt`, `ScanParsing.kt`, `Signal.kt` | Advert classification and fingerprints, `advertTokens`, RSSI smoothing, zones, distance model. |
| `Favorites.kt`, `Presence.kt` | Saved trackers (JSON in prefs `settings`), last-heard times (prefs `presence`). |
| `MonitorService.kt` | Foreground service (type location): filtered BALANCED scans for left-behind alerts and Find my phone. |
| `FindPhone.kt` | Button-press learning, matching, `PhoneAlarm`, `StopAlarmReceiver`. |
| `AlertRules.kt` | Quiet hours and safe places. |
| `Addresses.kt` | Geocoder + cache, `rememberAddress()` composable. |
| `AppLock.kt`, `ui/LockUi.kt` | Optional password lock. |
| `FavoritesWidget.kt`, `TagWidget.kt`, `FindTileService.kt` | Widgets and Quick Settings tile. |
| `findhub/FindHub.kt` | Kotlin↔Python bridge. **All Python calls go through `FindHub.call` on one thread.** |
| `findhub/HubViewModel.kt` | Find Hub UI state (`HubUi`), refresh, history ranges, replay, export, guard. |
| `findhub/PollWorker.kt` | Self-rescheduling WorkManager check; `afterNewLocations` (alerts, geofences, file backup). |
| `findhub/HubSettings.kt` | Prefs `findhub`: interval, toggles, geofences, links, battery, widget cache. |
| `findhub/HubNotify.kt`, `RingReceiver.kt` | Find Hub notifications and their Ring action. |
| `findhub/GoogleAuthActivity.kt` | WebView sign-in (step 1 cookie, step 2 vault keys via JS bridge `mm`). |
| `findhub/Geo.kt` | Distance, `Trips.stats`, `Trips.stays`. |
| `findhub/BeaconBattery.kt` | Sends FMDN adverts to `beacon.py` once per address. |
| `ui/HomeScreen.kt`, `ui/MapPage.kt`, `ui/MapScreen.kt` | Home; full-screen map + sheet; setup and History page. |
| `ui/TagMap.kt` | `TagMap` composable (MapLibre MapView): GeoJSON sources/layers for pins, halo rings, path, heatmap, points, areas, phone arrow; `MapHandle` (flyTo/fit); `MeTracker` (GPS + compass). |
| `ui/MapOverlays.kt`, `ui/MapTiles.kt` | `MapArt` bitmaps (tag glow dot in `TagCoral`, phone arrow) and `circle()`; base-map styles, saved choice (`settings`/`map_style`), OkHttp UA. |
| `ui/MoreScreen.kt`, `ui/HubSettingsSection.kt`, `ui/ExtraSettings.kt`, `ui/HelpDialog.kt` | Settings and help. |

Python (`app/src/main/python/`): `tagapp.py` (functions Kotlin calls, return JSON strings),
`server/` (Tag Tracker Open: `finder.py` Google calls + decryption, `poller.py`, `db.py` SQLite,
`drive_sync.py`), `beacon.py` (battery from adverts). Vendor: `third_party/GoogleFindMyTools/`.

## Conventions
- Compose + Material 3 only, Material You colours; all artwork is drawn in code. **Do not copy Google
  icons, branding or Find Hub artwork** (the UI is inspired by Find Hub, not a copy).
- User-facing text: plain, friendly English, no jargon; explain limits honestly in the UI.
- Settings are SharedPreferences: `settings`, `presence`, `findhub`, `alert_rules`, `applock`,
  `find_phone`, `addresses`. Engine data: `files/findhub/` (SQLite, `google_secrets.json`,
  `beacon_keys.json`). `allowBackup=false`.
- Keep vendored code unchanged where possible. Deliberate local edits: `server/finder.py` (`_decrypt`
  takes `canonic_id` and stores beacon keys; browser modules stubbed). Mark any new edit with a
  "Tracker Finder:" comment.
- Commit messages: "what / how / why" sections. Never commit APK builds except under `releases/`.

## Gotchas
- **Never put personal data or contact emails in network requests** (e.g. the tile User-Agent).
- **Secrets:** `google_secrets.json` and `beacon_keys.json` are account-level credentials. Never log,
  commit or upload them. `.gitignore` covers `google_secrets*.json`, keystores and APKs.
- Don't trim `third_party/GoogleFindMyTools` by guesswork: modules import `example_data_provider`,
  `main`, `chrome_driver` at import time (this broke sign-in step 2 once).
- Map engine is **MapLibre** (since 4.6; osmdroid before). OpenFreeMap styles are loaded by URL
  (`tiles.openfreemap.org/styles/liberty` and `/dark`); OSM and Esri are one-layer raster styles built
  in `MapTiles.style`. MapLibre zoom = old osmdroid zoom - 1. Its OkHttp client is replaced in
  `MapTiles.applyHttp` to send our User-Agent; OSM needs ≤2 connections and no prefetch
  (`prefetchZoomDelta = 0`). Esri tile URLs are `z/y/x`. CARTO needs an API key now - don't switch back.
- `TagMap` keeps everything as GeoJSON sources updated in `LaunchedEffect`s; base-map changes call
  `setStyle`, which drops our layers, so `addLayers` and the images are re-added on every style load.
- **Don't use MapLibre `HeatmapLayer`**: it crashed the owner's phone in 4.7 (likely native/GPU, with
  texture mode). The heatmap is two blurred `CircleLayer`s (`heat`, `heat-core`). Map updates in
  `TagMap` are wrapped in try/catch so a drawing error is logged, not fatal.
- The map credit line is a Compose overlay (MapLibre's own attribution/logo are off); keep it visible.
- Keep `TagMap` callback params stable (`remember`) so a GPS fix doesn't restart effects.
- Android allows ~5 BLE scan starts per 30 s per app; `ScanViewModel.applyScan` throttles. Don't add
  code that restarts scans in a loop.
- Background BLE needs a foreground service of type location and **filtered** scans to work with the
  screen off.
- WorkManager periodic minimum is 15 min, hence self-chaining. Only jobs tagged `findhub-chain` may
  re-schedule, otherwise checks double.
- Notification ID ranges: Find Hub 4999-21400, find-my-phone 7700, left-behind 100000+.
- R8 keeps `com.example.trackerfinder.**`; Chaquopy brings its own keep rules. If you add Python code
  that calls Java classes (`from java import ...`), add keep rules for them.
- Release builds re-download Python packages through pip (unpinned pure-Python deps may move).

## Untested / risky areas
Find my phone on FMDN tags; battery decoding on real hardware; behaviour under Doze; the map widget's
tile fetching; anything requiring the owner's real Google account. Prefer asking the owner for a
screenshot or the exact error text over guessing.
