# Tracker Finder

An Android app for finding a **Hoco E96** Bluetooth tracker (or any BLE tracker), made for one
person's personal use. It does two jobs:

1. **Nearby (Bluetooth):** listens to the tracker's Bluetooth signal and turns its strength into
   hot/cold feedback, a rough distance, beeps, vibration and a compass "which way" mode.
2. **Anywhere (Google Find Hub):** the E96 is a Google Find Hub tag. The app signs in to Find Hub
   with an unofficial client and shows the tag's reported locations on a map, keeps a history, and
   sends alerts. This part comes from [Tag Tracker Open](#licence-and-credits) and GoogleFindMyTools.

> Current version: **4.7.3** (versionCode 17); 4.7.2 was confirmed working on the owner's phone. Ready-to-install APK: [`releases/TrackerFinder-4.7.3.apk`](releases/TrackerFinder-4.7.3.apk).
> Full step-by-step story and design decisions: [`docs/HISTORY.md`](docs/HISTORY.md).
> Notes for AI agents and new developers: [`AGENTS.md`](AGENTS.md).

**Status:** builds, unit tests and Android lint pass in CI-like conditions (a Linux container), but
**nothing has been run on a real phone by the developer**. The person using it installs the APK and
reports back. See [Known limitations](#known-limitations-and-untested-areas).

---

## What it does (complete list)

### 1. Home tab (where the app opens)
- Headline summary: "All trackers nearby", "2 of 3 trackers nearby", or how many Find Hub tags you have.
- Find Hub status line: number of tags, check interval (or "auto checks off"), time of the last check.
- "Park & guard on" badge when movement alerts are armed.
- Quick actions: **Scan** (Nearby tab), **Map**, **Check** (check all tags now), **Guard / Unguard**.
- One card per Find Hub tag: name, "Last seen 5 min ago · ±20 m", street address (or coordinates),
  number of saved locations, battery level and when it was read, last check error, "Area alert" badge,
  and buttons **Map**, **History**, **Share**. Tapping the card opens it on the map.
- If Find Hub isn't connected: a card that explains it and opens setup (or "Finish setup" after step 1).
- One card per saved Bluetooth tracker: live signal bars, "In range · very close" / "Possibly nearby" /
  "Out of range · last heard 3 h ago", "watching" if a left-behind alert is on, and a **Find** button.
- If nothing is saved: a card that sends you to Nearby to find and star your tracker.
- **Checks & alerts** card: automatic check interval, last check (with result), next check time,
  locations stored, movement alerts on/off, number of tags with area alerts, left-behind alerts.
- Refreshes itself every minute.

### 2. Nearby tab (Bluetooth scanner)
- Live list of Bluetooth Low Energy devices around you, strongest first, with signal bars and dBm.
- Filter: **Trackers** (Google Find Hub, Apple Find My, Fast Pair, devices named like Hoco/E96) or **All devices**.
- Help text when nothing shows (press the tracker's button, turn on Location).
- Tap any device to open the finder.

### 3. Finder (hot/cold screen)
- Big colour circle that goes blue → yellow → red as you get closer, with the zone ("Right here",
  "Very close", "Near", "In the area", "Far") and a hint ("About a meter away").
- Trend pill: **Getting warmer / Getting colder / Steady**; "Searching" and "Lost · no signal for 12 s" states.
- Smoothed and raw signal in dBm and an estimated distance in metres.
- Chart of the last 60 seconds.
- **Beep as I get closer** (like a metal detector) and **Vibrate as I get closer** (for quiet places).
- **Calibrate at 1 m** to improve the distance estimate (reset in More).
- **Direction mode:** turn slowly in a circle with the phone against your chest; a 24-sector compass
  dial shows where the signal is strongest and says turn left/right; "New sweep" to start again.
- Follows the tracker when it changes its Bluetooth address (every ~15 min) and says how many times it did.
- Warns when other trackers of the same type are nearby (readings could be mixed up).
- Star button to save the tracker to Favorites with a nickname.
- Screen stays on while the finder is open.

### 4. Map tab (Google Find Hub)
- **Setup** (first time): step 1 sign in to Google, step 2 unlock end-to-end encryption with the
  phone's screen lock, both on Google's own pages inside the app; or import a login file. Shows a clear
  warning that this is an unofficial method.
- Full-screen map with a draggable bottom sheet:
  - **Your tags** list: initial avatar, "Seen 5 min ago · 1.2 km away", Check all now, menu.
  - **Tag details:** name, age, distance from you, street address, battery, accuracy, area alert,
    check schedule, last error, and round buttons: **Play sound** (ring the tag / Stop),
    **Locate** (ask Google for a fresh location), **Find nearby** (opens the Bluetooth finder for the
    linked favorite, asking once which favorite it is), **Directions** (opens your maps app),
    **History**, **Share** (Google Maps link); plus **Park & guard**.
  - Menu: Rename, Set/Change area alert (circle of 100 m to 3 km around the tag, or move it to the
    current spot, or remove it), Check all tags now, Refresh tag list from Google, Open Google Find Hub.
- Map drawing (MapLibre): each tag is a "glow dot": a coral dot with a thick white border and
  shadow, inside two soft halo rings that keep spreading out (coral so it never looks like your blue
  arrow; the selected tag is a little larger; your arrow is always drawn above the dots), accuracy circle, area-alert circle, your phone as a blue pointer arrow that turns with the compass, with
  an accuracy circle; camera flies to the tag you pick. Tap a pin for its name and last-seen time.
  A small credit line for the map source is always shown (tap it to open the source's site).
- Buttons: go to **my location**, **show all tags**. Busy indicator while Google is being asked.
- Map refreshes itself from stored data every minute.

### 5. History (from the Map tab or Home)
- **Automatic checks** card: on/off switch, interval chips (10 min, 15 min, 30 min, 1 h, 2 h),
  last check and its result, next check time, total stored locations, checks today, recent check
  times, average check duration, **Check now**.
- Range chips: Today, 24 h, 2 days, 7 days, 30 days, 90 days, 1 year, Custom (date-range picker).
- **Path / Heatmap** switch, and **Show individual points** (dots; tap one for its time and accuracy).
  Both choices are remembered between launches.
- Map of the range with the path; **Replay** (play/pause and a slider) with the time of each point.
- Trip stats: distance, time span, number of locations, average speed (ignores GPS jitter under 40 m).
- **Places visited** timeline: every stop of 10+ minutes with address, from-to time, duration and
  number of reports; tap one to jump the replay there.
- **Export CSV** and **Export GPX** for the tag and range (share sheet).
- List of every saved location (newest 300): time, address or coordinates, accuracy, Google's place
  name when it reports one ("Google says: Home"), "your phone" for reports from your own phone.
  Tap one to jump the replay to it.

### 6. Favorites tab (saved Bluetooth trackers)
- Each saved tracker with nickname, type, live status (In range / Possibly nearby with count of
  look-alikes / Last seen …) and **Find**.
- Menu: **Alert me if left behind** on/off, Rename, Open in Find Hub, Remove (with confirmation).
- Badge on the tab with the number of favorites.

### 7. More tab (settings)
- **Appearance:** System, Light or Dark (Material You colours on Android 12+).
- **Map:** OpenFreeMap (default; clean vector map with a real dark version), OpenStreetMap (classic
  look, colours inverted in dark mode) or Esri (backup). The choice is saved and used every time the
  app opens; the map switches between light and dark with the app theme.
- **Privacy - App lock:** set, change or turn off a password with an optional hint; asked on open and
  after a minute in the background; app hidden from the recent-apps preview while on.
- **Find my phone:** learn your tracker's button, on/off, Test ring, Forget.
- **Quiet hours & safe places:** quiet hours on/off with from/to times; safe places added from where
  the phone is now (100/150/300/500 m), listed and removable.
- **Find Hub:** account and Sign out; **Export login file** (Save to Files or Share, with a warning);
  Background checks on/off and interval; Movement alerts on/off; Check status notification;
  **Unrestricted battery use** shortcut; **Google Drive backup** (auto switch, Back up now, Check,
  Restore); **Backup file** (write history to a CSV you pick, e.g. in Drive); **Import history from CSV**.
- **Nearby finder:** Open Google Find Hub, Bluetooth settings, Beep, Vibrate, Distance calibration (reset).
- Explanations of left-behind alerts and of the widgets and Quick Settings tile.
- **Help, privacy & legal** guide, licences, version number.

### 8. Works in the background
- **Automatic Find Hub checks** every 10 minutes by default, even with the app closed (WorkManager;
  Android may delay them when the phone is idle). Each check stores new locations, then runs movement
  alerts, area alerts, the Drive backup, the backup file, the status notification and the map widget.
- **Left-behind monitor** (foreground service with a permanent notification) while any favorite has
  the alert on or Find my phone is on: low-power filtered scans that keep working with the screen off.
- Pauses left-behind alerts when Bluetooth is turned off (no false alarms) and resumes when it's back.

### 9. Alerts and notifications
- **Left behind:** "Did you leave Keys behind?" when a favorite hasn't been heard for 2 minutes after
  being near; cleared when it's heard again. Held back during quiet hours and at safe places.
- **Movement (Park & guard):** a tag moved more than 150 m since its last report.
- **Area:** a tag left its circle, and when it comes back.
- **Low tag battery** (low or very low), at most once a day per tag.
- **Check status** (optional, silent): how many new locations the last check saved.
- Find Hub alerts have **Ring tag** and **Open in Maps** buttons; tapping opens the tag on the map.
- **Quiet hours** silence all of these (Find my phone still rings).

### 10. Find my phone
- Learns what your tracker sends when you press its button, then rings the phone at full alarm
  volume (even on silent) with vibration for up to 45 s when it hears that signal close by.
  Stop from the notification. Works with the screen off. Tells you honestly if your tracker sends
  nothing new on a button press (common for Find Hub tags).

### 11. Tag battery level
- Google doesn't report it, so the app reads it from the tag's own Bluetooth signal when the phone is
  near the tag, using the tag's key that the app already uses to decrypt locations. Shows OK / low /
  very low / not reported, and warns when low.

### 12. Widgets and Quick Settings
- **My trackers** widget: up to four favorites with "In range" or "Last heard …"; tap one to find it.
- **Tag on map** widget: mini map of your Find Hub tag's last spot with a pin, name, address and age;
  updates after each background check; tap to open the map.
- **Find tracker** Quick Settings tile: opens the finder for your first saved tracker in one tap.

### 13. Data, privacy and safety
- Everything is stored on the phone (SQLite history, settings); excluded from Android cloud backup.
- Google tokens and the tag key file never leave the phone except to Google (Find Hub, and Drive if
  you turn backup on). Addresses come from the phone's geocoder (usually Google). Map tiles come from
  OpenFreeMap, OpenStreetMap or Esri (whichever you pick). No ads, no analytics, no server of our own.

---

## Install (for the user)

1. On the phone, download [`releases/TrackerFinder-4.7.3.apk`](releases/TrackerFinder-4.7.3.apk) and open it.
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
- Map data © OpenStreetMap contributors. Default map tiles and styles from
  [OpenFreeMap](https://openfreemap.org) (© OpenMapTiles); optional Esri tiles © Esri.
- Map engine: [MapLibre Native for Android](https://github.com/maplibre/maplibre-native) (BSD-2-Clause).

Not affiliated with Google or Hoco. For finding your own belongings only; tracking people without
consent is illegal in many places.
