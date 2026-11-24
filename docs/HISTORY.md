# Project history and design decisions

This is the full story of how Tracker Finder was built, step by step, with the reasons behind each
choice. It was written so that another developer or AI agent can continue the work without having
been part of the original conversation.

**How it was made:** the owner (non-developer, personal use) asked for features in plain words; an AI
coding agent (Claude Code, running in a cloud Linux container) wrote, built, unit-tested and linted
the app, and delivered APKs. The owner installed each APK on their phone and reported problems.
The agent never had a physical Android device, so all "it works" claims are from builds, unit tests
and lint, plus the owner's reports.

**About the git history:** the git history was reconstructed at the end (v4.5). Each commit adds the
files a step introduced, in their final form; intermediate versions of shared files (for example
`MainActivity.kt`, `ScanViewModel.kt`, `MapScreen.kt`) were not kept. Only the latest commit is
guaranteed to build. The descriptions below are the authoritative record of what changed when.

---

## v1 - Bluetooth signal-strength finder

**Ask:** "I have a Hoco E96, can you build an Android app that shows Bluetooth signal strength to the
tracker, to help me understand if I'm near or far?"

**Done**
- Kotlin + Jetpack Compose app, package `com.example.trackerfinder`, minSdk 26, target/compile 35.
- `ScanViewModel` scans BLE (`SCAN_MODE_LOW_LATENCY`), keeps a map of devices, and runs the finder.
- `Advert.kt` classifies adverts. The Hoco E96 is sold as a **Google Find Hub (FMDN) tracker**: it
  advertises Eddystone service data on UUID `0xFEAA` with frame type `0x40` (normal) or `0x41`
  (separated from owner / unwanted-tracking mode), followed by a 20-byte rotating EID.
  Fast Pair (`0xFE2C`), Apple Find My (Apple company ID, type `0x12`) and named devices are also detected.
- `Signal.kt`: RSSI smoothing (median of the last 5, then an exponential moving average), zones,
  log-distance model `d = 10^((rssi1m - rssi) / (10 n))` with `n = 2.7` (indoor) and a default
  `rssi1m = -60 dBm` that the user can calibrate.

**Why these choices**
- Raw RSSI jitters by ~10 dB, so a median filter removes spikes and EMA smooths the rest.
- Zones and the warmer/colder trend are more trustworthy than metres; the UI leads with them.
- **Rotating addresses:** FMDN trackers change their Bluetooth MAC and EID about every 15 minutes
  (1024 s windows) for privacy. The finder "follows" a tracker: if the target goes quiet for 8 s and a
  new device with the same type fingerprint appears after that, it's assumed to be the same tracker.
  This is a heuristic and can be fooled by look-alikes.

**Environment note:** Android's Google Maven host (`dl.google.com`) was blocked in the cloud
environment at first; the owner approved adding it to the allowlist.

## Save tracker (still v1)

**Ask:** "Is there a way to add the tracker once identified?"

**Done:** "Save as my tracker" storing nickname, last address and a type fingerprint
(`findhub:<service-data length>` etc.) in SharedPreferences.

**Why:** an address alone is useless after rotation; the fingerprint lets the app re-find "a tracker
of the same kind" and warn if several are around. No stable identifier exists in FMDN adverts
without the owner's key (this changed in v4.5, see battery).

## APK delivery

**Ask:** "Can you build the APK for me?" The agent installed the Android SDK (cmdline-tools,
platform 35, build-tools 34) in the container and built a debug APK.

## v2 - Material 3 redesign and Favorites tab

**Ask:** "The UI is very basic, fix it, especially favorite trackers, make it a tab." The owner also
asked whether the app could connect to the Google Find Hub network.

**Done:** bottom navigation (Nearby / Favorites / More), Material You dynamic colour, multiple
favorites with nicknames and live status, migration from the single saved tracker, More tab with
calibration and a shortcut to Google's Find Hub app.

**Answer at the time on Find Hub:** not possible with official APIs (reports are end-to-end encrypted,
no public API). This was revisited in v4 when the owner supplied Tag Tracker Open.

## v3 - Vibration, left-behind alerts, widget, direction mode

**Ask:** "Add all of the suggested."

**Done**
- Vibration pulses (faster when closer).
- `MonitorService`: foreground service (`foregroundServiceType="location"`, required by Android for
  background BLE) using **filtered, balanced** scans so they continue with the screen off. Alerts when
  a favorite that was heard hasn't been heard for 2 minutes.
- `FavoritesWidget` (up to 4 rows) fed by `Presence` (last-heard timestamps shared across processes).
- `Direction.kt`: 24 sectors x 15 degrees from the rotation-vector sensor; the user turns slowly with
  the phone against their chest (the body shadows the signal), the strongest sector points at the tag.

## v4.0 - Tag Tracker Open integration (Google Find Hub)

**Ask:** "Integrate the Tag Tracker Open functionality into this app, only I will be using it."
The owner uploaded `TagTrackerOpen_v1.zip` (a Python/Flask web app built on GoogleFindMyTools).
The zip was reviewed first and no malicious code was found; its scripts were never run outside the build.

**Done**
- Vendored GoogleFindMyTools into `third_party/GoogleFindMyTools` (minus ESP32/Zephyr firmware and CI)
  and TTO's `server/` package plus a thin `tagapp.py` entry module into `app/src/main/python`.
- Embedded Python with **Chaquopy 17 (Python 3.13)**; native wheels pinned to versions Chaquopy
  publishes for Android (`app/requirements-android.txt`); two pure-Python wheels in `app/wheels/`.
- `FindHub.kt` is the bridge: every Python call runs on **one dedicated thread** (the engine isn't
  thread-safe and Python start-up is slow), returns JSON strings parsed in Kotlin.
- `GoogleAuthActivity`: WebView sign-in. Step 1 loads `accounts.google.com/EmbeddedSetup` and reads
  the `oauth_token` cookie, exchanged by `gpsoauth`. Step 2 loads Google's shared-key page, which calls
  a JavaScript bridge `mm.setVaultSharedKeys(...)` with the vault keys used to decrypt the owner key.
- Map tab (osmdroid), history, replay, ranges, stats (`Geo.kt`, 40 m jitter filter), Locate, Ring,
  rename, CSV/GPX export, Drive backup, CSV import, movement/area alerts (`HubNotify`), background
  checks (`PollWorker`), theme switcher (`AppTheme`, light/dark/system).
- App became **GPL-3.0** (TTO and GoogleFindMyTools are GPL-3.0); licences kept in `third_party/`.
- `allowBackup="false"` so tokens and history never go into Android cloud backups.

**Why WorkManager self-chaining:** `PeriodicWorkRequest` has a 15-minute minimum; the default here is
10 minutes, so each run enqueues the next one-off job (`APPEND_OR_REPLACE`) with an initial delay.
Android Doze can still delay runs.

**Environment note:** `chaquo.com` had to be allowlisted (owner approved), and Python 3.13 must be on PATH.

## v4.0.1 - "No module named example_data_provider"

**Report:** step 1 sign-in worked, step 2 failed with that error.

**Cause:** when trimming the vendor folder, `example_data_provider.py`, `main.py` and
`chrome_driver.py` were removed as "desktop-only", but GoogleFindMyTools imports them at module level
(e.g. `FMDNCrypto/eid_generator.py` imports `example_data_provider`).

**Fix:** restored the files; `server/finder.py` stubs browser modules (Selenium/undetected-chromedriver)
instead; every import in the vendored tree was checked statically with an AST scan.

## v4.0.2 / 4.0.3 - Map tiles and login export

**Reports:** "OpenStreetMap is not loading, it says the app is not following their usage policy", then
"CARTO says API key required".

**Decisions**
1. The OSM refusal was because osmdroid's default User-Agent was the bare package name. OSM's tile
   policy requires a unique, identifying UA. First the app switched to CARTO basemaps...
2. ...but CARTO now requires an API key. Final choice (`ui/MapTiles.kt`): **OSM standard tiles** with
   UA `TrackerFinder/<version> (personal Android tracker app)`, a `TileSourcePolicy` with at most 2
   concurrent downloads, no bulk download and no preventive fetching; plus **Esri World Street Map**
   as a keyless fallback (note Esri URLs are `z/y/x`). Dark mode inverts tile colours.
   The UA deliberately contains no personal contact details.

Also added **Export login file** (copies `google_secrets.json` via SAF or share sheet, with a strong
warning; it is equivalent to the Google account's Find Hub access).

## v4.1 - History and check interval made visible

**Report:** "It doesn't have the history feature or show at what intervals data is stored and checked."
The features existed but were hidden. Map and History became separate tabs; History opens with an
"Automatic checks" card: interval chips, last check, next check (from WorkManager's
`nextScheduleTimeMillis`), total stored, checks today, recent check log.

## v4.2 - Home screen and full Tag Tracker Open parity

**Ask:** "Get all the features from Tag Tracker Open but with a better UI, and open the app on a home
screen, not Favorites."

**Feature audit vs TTO:** already present: sign-in/import, multiple tags, map, Locate/Ring, ranges,
replay, stats, check now, export, rename, Drive sync/verify/restore, custom Drive file, interval/pause,
movement alerts, check log, semantic locations. Added: Park & guard, share location, 24 h range,
individual points and accuracy circle, auto-refresh, refresh tag list, notification actions
(Ring / Open in Maps, `RingReceiver`), status notification, battery-optimisation exemption, app lock,
CSV import UI, Help & legal. Not applicable: TTO's server, web login, "change server".

**Home tab** is now the default; the old behaviour of auto-opening the finder when exactly one favorite
exists was removed (it hid Home).

**App lock:** salted PBKDF2-WithHmacSHA256 (120k iterations) hash only; relocks after 60 s in the
background. It hides the UI; it does not encrypt files.

## v4.3 - Find Hub-style map, new pin, phone location

**Ask:** "Change the location pointer, it's ugly. Mimic the Find Hub UI with smooth animations. Show my
phone's location."

**Done:** full-screen map (no top bar) with `BottomSheetScaffold` (list ↔ detail via `AnimatedContent`),
round action buttons, camera fly-to (`controller.animateTo`), pins drawn in code (`MapArt.tagPin`),
`PulseOverlay`, `MeOverlay` (osmdroid `MyLocationNewOverlay` + compass heading from
`InternalCompassOrientationProvider`). **Original artwork only**: no Google icons, branding or assets.

## v4.4 - Audit, clean-up, optimisation

**Ask:** "Audit and find bugs, clean up and optimise; suggest new features." Also: "show a tiny phone
icon for my location".

**Bugs found and fixed**
- Bluetooth turned off → monitor heard nothing → **false left-behind alerts**. Now a
  `BluetoothAdapter.ACTION_STATE_CHANGED` receiver pauses alerts (clears "armed") and restarts scans.
- Same toggle in the foreground left `scanning=true` with a dead scan; now handled too.
- `runNow` + self-chaining: a manual run also called `scheduleNext` → **two chains, double checks**.
  Fixed by tagging chain jobs (`findhub-chain`) and only re-scheduling from tagged runs.
- Manual Check now / Locate didn't run movement/area alerts or file backup → shared `afterNewLocations`.
- The engine reports failures in its check log instead of throwing, so failures were recorded as OK.
- Left-behind notification IDs (1000+hash&0xFFFF) overlapped Find Hub IDs (5000-21000) → moved to 100000+.
- The WebView key bridge accepted calls from any page → now only from `google.com` hosts.

**Performance and battery**
- Scan modes per screen: FAST (finder, Nearby, learning), BALANCED (Home, Favorites), OFF (Map, More),
  with protection against Android's limit of 5 scan starts per 30 s.
- Screen kept on only while the finder is open (it used to be for the whole app).
- Device map published to Compose at most every 500 ms instead of per advert (was dozens of
  recompositions per second); favorites' last-seen written at most every 10 s.
- Map callbacks memoised so GPS fixes don't rebuild all overlays.

**Size:** release build with **R8 + resource shrinking**: 37 MB → 29 MB (dex shrank; the Python
runtime and libraries are most of the rest). Signed with the debug key so it upgrades in place.
`proguard-rules.pro` keeps `com.example.trackerfinder.**` because WorkManager, receivers and services
are referenced by name; Python never calls into app classes (checked: no `from java` imports).

## v4.5 - Owner-picked features 1-6

**Ask:** "Add 1-6, don't need 7 (Wear OS)."

1. **Find my phone** (`FindPhone.kt`): no standard exists, so the app *learns*: 4 s baseline of advert
   "tokens" (coarse labels like `svc:feaa:40`, `svc:fe2c`, `mfr:004c:12`, `name:…`) from strong
   signals, then 12 s while the user presses the button; tokens that appear only then win. The monitor
   service adds a hardware `ScanFilter` for that token and rings (`PhoneAlarm`: alarm stream at max
   volume, restored after; 45 s; Stop action; 20 s cooldown). Requires RSSI ≥ -80 to fire.
   **Risk:** FMDN tags may not advertise anything new on a button press; the UI says so if nothing is found.
2. **Quiet hours & safe places** (`AlertRules.kt`): quiet hours silence Find Hub alerts and left-behind
   alerts; safe places (phone's last known location within radius) silence left-behind alerts only,
   so leaving home without the tracker still alerts.
3. **Addresses** (`Addresses.kt`): Android `Geocoder` (async API on 33+), cached by ~10 m grid cell in
   memory and SharedPreferences. On most phones this sends coordinates to Google; noted in Help.
4. **Heatmap & timeline:** `HeatOverlay` (radial-gradient blob per report) and `Trips.stays`
   (consecutive reports within 120 m of a running centroid, ≥10 min).
5. **Tag battery** (`findhub/BeaconBattery.kt`, `python/beacon.py`): Google's API returns no battery
   level. FMDN adverts carry a "hashed flags" byte after the EID: `flags XOR lastByte(SHA-256(r))`,
   battery in bits 5-6 (0 unsupported, 1 normal, 2 low, 3 critical), bit 7 UTP. `r` needs the tag's
   identity key (EIK) and its clock. While decrypting location reports, `finder._decrypt` now saves the
   EIK and the report's `deviceTimeOffset` + timestamp to `files/findhub/beacon_keys.json` (mode 600;
   deleted on sign-out). When the phone hears an FMDN advert, the app estimates the tag clock, tries
   windows ±3 x 1024 s, recomputes the EID, and on a match decodes the flags. Verified only with
   synthetic round-trip data.
6. **Quick Settings tile** (`FindTileService`) and **"Tag on map" widget** (`TagWidget`, stitched from
   up to 4 OSM tiles cached 7 days, updated after each background check via `TagWidgetWorker`).

## Repository upload

The owner asked for the project on GitHub with detailed commit messages, the APK, and documentation
good enough for another AI or human to continue. The APK is committed under `releases/` because the
agent's session wasn't allowed to create GitHub Releases.

## Ideas not built yet
- Wear OS tile (the owner declined).
- Restart the left-behind monitor after reboot (`BOOT_COMPLETED`).
- Proper release signing key (currently the debug key, fine for personal sideloading only).
- Widget configuration to pick which tag the map widget shows (currently the first tag with a location).
