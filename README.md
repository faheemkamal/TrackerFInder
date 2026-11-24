# Tracker Finder

An Android app for finding a **Hoco E96** Bluetooth tracker (or any BLE tracker), made for one
person's personal use. It does two jobs:

1. **Nearby (Bluetooth):** listens to the tracker's Bluetooth signal and turns its strength into
   hot/cold feedback, a rough distance, beeps, vibration and a compass "which way" mode.
2. **Anywhere (Google Find Hub):** the E96 is a Google Find Hub tag. The app signs in to Find Hub
   with an unofficial client and shows the tag's reported locations on a map, keeps a history, and
   sends alerts. This part comes from [Tag Tracker Open](#licence-and-credits) and GoogleFindMyTools.

> Current version: **4.5** (versionCode 12). Ready-to-install APK: [`releases/TrackerFinder-4.5.apk`](releases/TrackerFinder-4.5.apk).
> Full step-by-step story and design decisions: [`docs/HISTORY.md`](docs/HISTORY.md).
> Notes for AI agents and new developers: [`AGENTS.md`](AGENTS.md).

**Status:** builds, unit tests and Android lint pass in CI-like conditions (a Linux container), but
**nothing has been run on a real phone by the developer**. The person using it installs the APK and
reports back. See [Known limitations](#known-limitations-and-untested-areas).

---

## Features

### Tabs
| Tab | What it shows |
| --- | --- |
| **Home** (opens by default) | Summary ("2 of 3 trackers nearby"), every Find Hub tag with address, age, accuracy and battery, saved Bluetooth trackers with in-range status, quick actions (Scan, Map, Check now, Park & guard), and a Checks & alerts card. |
| **Nearby** | Live list of Bluetooth devices, trackers first. Tap one to open the finder. |
| **Map** | Full-screen map with a draggable bottom sheet: your Find Hub tags, or one tag's details and actions. History opens from here. |
| **Favorites** | Saved Bluetooth trackers with nicknames, status, Find button, left-behind alert switch. |
| **More** | Appearance, map provider, app lock, Find my phone, quiet hours & safe places, Find Hub settings, finder settings, widgets, help, licences. |

### Nearby finder (Bluetooth)
- Hot/cold circle, smoothed and raw dBm, zone ("Very close", "Near"…), rough metres, trend, 60 s chart.
- Beep and vibration that speed up as you get closer; "Calibrate at 1 m".
- **Direction mode:** hold the phone against your chest and turn in a circle; your body blocks the
  signal, so the strongest compass sector points at the tracker.
- Follows the tracker across its ~15-minute Bluetooth address rotation.

### Find Hub (anywhere)
- Map with round initial-badge pins, accuracy halo, pulse on the selected tag, your phone as a small
  phone icon with a compass cone, fly-to camera, "my location" and "fit all" buttons.
- Actions: Play sound (ring), Locate now, Find nearby (opens the Bluetooth finder), Directions,
  History, Share, Rename, area alert, Park & guard.
- **History:** ranges (today, 24 h, 2/7/30/90 days, 1 year, custom), path or **heatmap**, replay,
  trip stats, individual points, **Places visited** timeline (stays of 10+ min with addresses),
  CSV/GPX export, every saved point.
- **Automatic checks** every 10 min by default (10/15/30/60/120), even when the app is closed.
- **Alerts:** movement (>150 m), area (leave/return), left-behind (Bluetooth), low tag battery,
  optional status notification after each check. Notification buttons: Ring tag, Open in Maps.
- **Quiet hours** (no alerts at night) and **safe places** (no left-behind alerts at home).
- **Backups:** Google Drive folder, or a CSV file you choose; CSV import/restore; login-file export/import.
- **Tag battery:** decoded from the tag's own Bluetooth advert (Google's API doesn't return it).

### Extras
- **Find my phone:** learn what your tracker sends when its button is pressed, then ring the phone
  loudly when that signal is heard close by (only works if the tracker sends something new).
- Widgets: "My trackers" (Bluetooth status) and "Tag on map" (last spot on a mini map).
- Quick Settings tile "Find tracker".
- App lock (password + hint, PBKDF2 hash), hidden from recent-apps preview when on.
- Light/dark/system theme; Material You colours.

---

## Install (for the user)

1. On the phone, download [`releases/TrackerFinder-4.5.apk`](releases/TrackerFinder-4.5.apk) and open it.
   Allow "install unknown apps" for your browser/files app if asked.
2. Allow "Nearby devices" and "Location" when the app asks. Notifications are needed for alerts.
3. For the map: Map tab → step 1 sign in to Google → step 2 unlock encryption (your phone's screen lock).

Requirements: Android 8.0+ on 64-bit ARM (arm64-v8a). Each APK is signed with the same debug key, so
new versions install over old ones and keep data.

## Build (for developers)

Needs JDK 17, the Android SDK (platform 35, build-tools 34+), and **Python 3.13 on PATH** (Chaquopy
uses it at build time to compile the Find Hub engine and download its packages from PyPI).

```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk (R8-shrunk, ~29 MB)
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk (~37 MB)
./gradlew testDebugUnitTest        # JVM unit tests (signal maths, trips, stays, quiet hours…)
./gradlew lintDebug                # Android lint; must report 0 errors
```

Network hosts the build needs: Google Maven (`dl.google.com`, `maven.google.com`), Maven Central,
`chaquo.com` (Chaquopy's Python packages), `pypi.org` / `files.pythonhosted.org`.

## Architecture (short)

```
Kotlin / Jetpack Compose UI (MainActivity + ui/*)
 ├─ ScanViewModel ── Android BLE scanner ── Advert / ScanParsing / Signal (smoothing, zones)
 │    └─ Favorites, Presence, FindPhone, BeaconBattery (battery from adverts)
 ├─ MonitorService (foreground, filtered scans): left-behind alerts + Find my phone
 ├─ HubViewModel ── FindHub.kt ──(Chaquopy, one thread)── Python engine
 │                                      tagapp.py → server/{finder,poller,db,drive_sync}.py
 │                                                → third_party/GoogleFindMyTools (Google APIs, crypto)
 ├─ PollWorker (WorkManager, self-rescheduling) → checks, alerts, backups, widget refresh
 └─ Widgets (FavoritesWidget, TagWidget), FindTileService, receivers (Ring, StopAlarm)
```

Data stays on the phone: SQLite history and Google tokens in `files/findhub/` (excluded from Android
backup), settings in SharedPreferences. More detail in [`AGENTS.md`](AGENTS.md).

## Known limitations and untested areas

- **Not device-tested by the developer.** Everything was built and unit-tested in a container.
- **Find my phone** depends on the tracker sending a new kind of Bluetooth signal when its button is
  pressed. Many Find Hub tags (possibly the E96) don't; the app says so during learning.
- **Tag battery** decoding follows the FMDN spec as understood (hashed-flags byte, bits 5-6) and was
  only verified against synthetic data. It needs one Locate/check on v4.5+ to store the tag key.
- **Distance** is an estimate; walls, bodies and pockets change RSSI a lot. Trend is more reliable.
- **Bluetooth identity:** trackers rotate their address/ID every ~15 min, so two trackers of the same
  model can't be told apart reliably; the finder warns about look-alikes.
- **Find Hub access is unofficial** (GoogleFindMyTools). Google can change or block it, and using it
  may go against Google's terms. A secondary Google account is safer.
- Background checks can be delayed by Android Doze; "Unrestricted battery" in More helps.
- Left-behind monitor doesn't restart after a reboot until the app is opened once.
- arm64 only (the Python native libraries are built for arm64-v8a only, to save space).

## Licence and credits

GPL-3.0 (see [`LICENSE`](LICENSE)), because the app includes GPL-3.0 code:
- **Tag Tracker Open** (a modified fork of Shekh-Muhsen's Tag Tracker) - server code in
  `app/src/main/python/server/` and `tagapp.py`. Licence: `third_party/TagTrackerOpen-LICENSE.txt`,
  notes: `third_party/TagTrackerOpen-LEGAL.md`.
- **GoogleFindMyTools** by Leon Böttger - `third_party/GoogleFindMyTools/` (own LICENSE inside).
- Map data © OpenStreetMap contributors; optional Esri World Street Map tiles © Esri.

Not affiliated with Google or Hoco. For finding your own belongings only; tracking people without
consent is illegal in many places.
