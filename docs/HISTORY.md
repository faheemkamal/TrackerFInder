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

## 4.6 - OpenFreeMap, saved map choice, new markers

Owner's requests: "Add open free maps. Also if i select a map style, it does not stick. Reverts to
whatever when i reopen the app", then "instead of the phone icon, can it be a blue pointer arrow. And
make the tag look like a tag instead of the letter from the tag name".

- **Map engine osmdroid → MapLibre Native (12.3.1).** OpenFreeMap only serves vector tiles, which
  osmdroid can't draw. 12.3.1 was picked because it is built with Kotlin 2.0.x (13.x needs 2.2) and
  needs no compileSdk bump. All drawing moved to `ui/TagMap.kt` as GeoJSON sources + layers
  (fill/line/circle/symbol, native `HeatmapLayer`). APK grew from ~29 MB to ~41 MB (arm64 native lib).
- **Base maps:** OpenFreeMap "liberty" (light) and "dark" are the new default; OSM and Esri remain as
  raster styles. The credit line is always shown (OpenFreeMap's attribution requirement).
- **Map choice not sticking:** the old code read the saved choice once into memory and the osmdroid
  view always started on OSM before switching; no definite cause was found without a device. Now the
  choice is stored under a new key `map_style` with `commit()`, read from disk when the activity is
  created and again whenever a map is created, and the map is built straight from it. Old `map_tiles`
  values are ignored on purpose so everyone lands on OpenFreeMap once.
- History's Path/Heatmap and "Show individual points" choices are now saved too (`findhub` prefs).
- **Markers:** the phone is a blue navigation arrow rotated by the compass (rotation-vector sensor;
  GPS bearing as a fallback) with an accuracy circle. Tags are a drawn tracker-tag fob with a keyring
  hole instead of a letter badge. Rotation/tilt gestures are off so the arrow's heading reads as
  compass north-up. The home-screen widget uses the same tag pin.
- Not tested on a device: OpenFreeMap tile loading, compass direction, and gestures inside the
  History list's map card.

## 4.7 - "Glow dot" tag marker

The owner was shown a few marker ideas and picked "Glow dot". Each tag is now a solid dot (14 dp
radius when selected, 11.5 dp otherwise) with a thick white border and drop shadow, inside two soft
halo rings that spread out and fade (circle layers "pulse1"/"pulse2", 2.4 s cycle, ~30 fps, only while
the map is on screen). The rings now surround every tag on both the main map and History, not just the
selected tag. Tag colour is a fixed warm coral (`TagCoral`, #FF5A4E) so a tag can never be confused
with the blue phone arrow. Tap a dot for the tag's name and last-seen time (unchanged). The 4.6
keyring-tag drawing is gone; the map widget uses the same dot.

## 4.7.1 - Heatmap crash fix

The owner reported "heat map crashed twice" on 4.7. No crash log was available. The only new thing on
the heatmap path since 4.5 was MapLibre's native `HeatmapLayer` (4.6), which renders through an
offscreen framebuffer; with the map in texture mode on some phones' GPUs that can fail natively, which
no Kotlin try/catch can stop. Because 4.6 also started saving the Path/Heatmap choice, the History
page reopened straight into the heatmap, which explains the second crash.

Fix: the heatmap is now two blurred circle layers (orange glow + smaller red core), whose opacity
scales with the number of reports so busy places pile up into a hot spot. This looks close to the old
osmdroid heatmap. All map data updates and the style-load callback are wrapped in try/catch, so a
Kotlin-side map error is logged and the map just stays as it was. Confirmed fixed by the owner on 4.7.2.

## 4.7.2 - Smaller glow dot, arrow on top

The owner: "the glow dot is too big. it hides the arrow when together". The dot went from 14/11.5 dp
radius to 9/7.5 dp (selected/other), with a thinner white border and shadow. The halo rings now grow
from 8 to 21 px instead of 14 to 36. The "me" symbol layer is now added after "pins", so your blue
arrow is always drawn above the tag dots.

**Verified (2026-11-24):** the owner installed 4.7.2 and reported "Everything working fine now":
OpenFreeMap map in light/dark, the saved map choice, the blue arrow, the glow dots and the heatmap all
work on their phone. Why the APK is bigger since 4.6 (~29 MB to ~41 MB): MapLibre ships a native
rendering library (arm64 only, as the build filters ABIs for Chaquopy) plus its Java/Kotlin layer.

## 4.7.3 - Glow dot a little bigger

The owner: "make glowing dot just a little bit bigger". The dot radius went up about 15%, from 9/7.5 dp
to 10.5/8.6 dp (selected/other), and the halo rings from 8-21 px to 9-24 px. It is still well under the
4.7 size (14/11.5 dp), and your arrow stays drawn above the dots.

## 4.8 - Heatmap playback

The owner: "In path, there is animation. In heatmap, there is no animation. I can't tell which point is
which when played back history." In path mode playback already drew the travelled part of the path
and a dot at the current step, but heatmap mode drew all the heat at once with no dot. Now, while
playing back (or dragging the slider) in heatmap mode:
- the heat builds up from the first report to the current step;
- the current-position dot is shown on top;
- a short trail joins the last 8 steps, so you can see where the tag just came from.
Outside playback the heatmap looks the same as before. Implemented in `TagMap` by reusing the
`path-done` and `replay` sources (`TRAIL` = 8).

## 4.9 - Adjustable movement alert distance

The owner: "For movement alerts, its set at 150m. Can you make it adjustable with a slider 150m to 1 km",
then "Put it in more tab". Under More > Find Hub > Movement alerts there is now an "Alert distance"
slider, 150 m to 1 km in 50 m steps. It is saved as `findhub`/`move_distance` when you let go, and the
default is 150 m. The value is passed to Python's existing `tagapp.new_movements(threshold_m)`, both
from background checks (`PollWorker.afterNewLocations`) and when arming Park & guard. The Home status
line, the Park & guard toast and Help now show the chosen distance instead of a fixed 150 m.

## 5.0 - Area alerts like Find Hub

The owner: "Can you make area alert similar to find hub. You set the address and a radius from that and
if the tracker moves beyond the area, you get notified". Before, an area was always centred on the
tag's latest location, and you could only change the radius in a small dialog. Now "Set/Change area
alert" opens a full-screen editor (`ui/AreaAlertEditor.kt`):
- Search an address or place: `Addresses.search` uses Android's Geocoder forward lookup (on most
  phones this asks Google), and shows up to 5 results. You can also tap the map (new `onMapTap`
  parameter on `TagMap`), or use "Tag's location" or "My location" (last known fix).
- A radius slider (100 m to 3 km, 50 m steps), with the circle previewed live and the camera kept
  fitted to it. Below it: the place name, how far the tag is from the centre, and whether it is inside
  right now.
- `Geofence` gained `label` (stored as `label` in the `fence.<id>` JSON). Notifications use it:
  "Keys left <place>" / "Keys is back at <place>".
- Checking is unchanged: `PollWorker.checkGeofences` after each background check, alerting on
  inside->outside and outside->inside.

## 5.1 - Wi-Fi finder

The owner: "In the nearby, can you add a wifi tracker and the ability for it to be added to favorites.
Give the same features of hot and cold tracking. Distance and direction. Beeps". Nearby now has a
**Wi-Fi** filter beside Trackers and All. Wi-Fi networks join the same device map as Bluetooth
(`TrackerKind.WIFI`, address = BSSID), so the finder (signal, zone, distance, trend, direction
sweep, beeps and vibration) and favorites work unchanged.
- `WifiScan.kt` asks for a scan every 30 s. Android allows 4 per 2 minutes in the foreground, so that
  is the fastest that works; if Developer options "Wi-Fi scan throttling" is off, it scans every 5 s.
  The connected network's RSSI is read every second from `WifiManager.connectionInfo`, with no scan.
- Routers are much stronger than a coin-cell tag, so Wi-Fi RSSI is stored minus 20 dB. This puts
  "1 m from a router" near the tag's 1 m reference. It's a rough guess, and there is no calibration
  for Wi-Fi.
- Wi-Fi gets longer "lost" (75 s) and expiry (100 s) windows and lighter smoothing, so sparse
  readings don't flicker or lag. Look-alike following and left-behind alerts are off for Wi-Fi.
- Wi-Fi scanning runs whenever Bluetooth scanning does (Home, Nearby, Favorites, finder), so saved
  networks show as in range. New permissions: ACCESS_WIFI_STATE and CHANGE_WIFI_STATE; results also
  need location, which the app already has.

## 5.2 - Wi-Fi channels, bands and spectrum analyser

The owner: "In nearby, all is misleading now. Say all bluetooth devices. Also can you add wifi
channels to the ssids. Also can you add a tab in nearby for a spectrum analyzer to understand which
channels are busy. Do that overlapping curve visual", then "wifi tab doesnt say which are 5ghz and
which ones are 2.4ghz".
- The "All" chip is now "All Bluetooth devices". The chip row scrolls sideways.
- `WifiHeard` now carries the frequency, channel width (from `ScanResult.channelWidth`) and the centre
  of the bonded channel (`centerFreq0`). `WifiBands` turns frequencies into channel numbers and bands.
  `ScanViewModel.wifiNets` keeps the latest reading per BSSID in real dBm, and the connected-network
  reading keeps the width from the last scan.
- The Wi-Fi list shows a coloured band badge (2.4 amber, 5 blue, 6 purple) beside the SSID, and the
  channel and width under it. The finder shows the channel under the network's name.
- New **Wi-Fi spectrum** view (`ui/WifiSpectrum.kt`): one rounded hump per network, drawn from
  centre ± width/2 (2.4 GHz 20 MHz drawn 22 MHz wide, as the signal really spreads), with height
  -100..-20 dBm. Humps are drawn weakest first, have a per-network colour and an SSID label, and
  rise when the band changes. Channel load sums the strength of networks overlapping each 20 MHz
  channel. On 2.4 GHz the recommendation is among 1, 6 and 11, the only channels that don't overlap.

## 5.3 - Home without Wi-Fi, "My favorites", smooth spectrum curves

The owner made three requests:
- Saved Wi-Fi networks appeared under Home's "Nearby trackers (Bluetooth)". Home now filters favorites
  to non-Wi-Fi ones; that covers the section, the header count and left-behind count. Wi-Fi favorites
  stay in the Favorites tab.
- The Favorites tab heading "My trackers" is now "My favorites".
- They asked why the spectrum humps had flat tops. The honest answer: the flat top loosely copied a
  real Wi-Fi channel's spectral mask, where power is roughly level across the channel and then falls
  off at the edges. But the phone gives one signal number per network, not power per MHz, so the
  chart's shape is only a picture and the flat top suggested detail the app doesn't measure. Each
  network is now a smooth parabolic arch (a quadratic Bézier from edge to edge, peaking at its dBm in
  the channel centre), the look used by common Wi-Fi analyser apps.

## 5.4: audit, battery, smaller APK, "exclude my networks"
The owner asked for a bug audit, clean-up and optimisation, then for a way to leave their own Wi-Fi
out of the spectrum. Changes:
- Bugs: the Quick Settings tile and the favorites widget could pick a Wi-Fi favorite (they're for
  tags); both now skip Wi-Fi. Home's area-alert badges were cached with `remember` and went stale
  after editing an area; they're recomputed now. `MapTiles.applyHttp` built a new OkHttpClient (and
  thread pool) on every map/provider change; it now only rebuilds when the connection limit changes.
- Battery: Wi-Fi scanning only runs in FAST (Nearby, finder), or in BALANCED when a Wi-Fi network is
  a favorite. The connected network's signal is read every 1 s only in FAST, else every 10 s
  (`WifiScan.fast`). `devices` is republished only when the live map changed (`liveDirty`). Wi-Fi
  on/throttled status is a cached `wifiStatus` flow (refreshed every 5 s and on mode change) instead
  of binder calls on every recomposition.
- APK size: `packaging.jniLibs.useLegacyPackaging = true` stores native libraries compressed;
  42.6 MB to 25.7 MB download (Android extracts them on install, so installed size is similar).
- Spectrum: each network row has a "Mine" chip (saved in prefs `settings`/`wifi_mine`, by SSID so
  every band and mesh unit counts, hidden networks by BSSID) and an "Exclude my networks" switch
  (`wifi_exclude_mine`). When on, those networks leave the chart and the busiest/quietest channel
  maths, so the advice is about the neighbours only.

## 5.5: alerts after restart, Bluetooth breadcrumbs, Wi-Fi only on screen, wording pass
The owner picked two ideas from the 5.4 list and added rules:
- **Restart:** `BootReceiver` (BOOT_COMPLETED and MY_PACKAGE_REPLACED) calls `MonitorService.sync(fromBoot = true)`.
  Android 14+ may refuse a location-type foreground service started at boot; if `startForeground`
  or the start itself fails, a "Left-behind alerts are paused, tap to turn them back on" notification
  (id 99000) opens the app, whose onStart already re-syncs the monitor. Untested on the owner's phone.
- **Bluetooth breadcrumbs:** `Breadcrumbs.record` runs whenever `Presence.heard` writes (every 10 s
  per heard favorite, app or monitor, Bluetooth favorites only). It takes the phone's last known
  position (passive, never starts GPS; fix under 2 min old and 150 m accuracy) and keeps the last 40
  spots per favorite in prefs `breadcrumbs`, merging spots within 30 m. The main map draws them as
  violet dots (`crumbs`, newest bigger) with a dashed trail (`crumb-path`); tapping one says when.
- **Wi-Fi only on screen:** `ScanViewModel.setWifiScreen(key, open)`; Nearby's Wi-Fi list/spectrum
  and the finder for a Wi-Fi network register while composed. Wi-Fi never scans on other tabs or in
  the background, even for Wi-Fi favorites (they're checked when you tap Find).
- **Wording/consistency pass:** Favorites texts no longer assume every favorite is a tracker
  ("Rename favorite", "The device itself"), "Open in Find Hub" hidden for Wi-Fi favorites, Nearby
  empty state names "All Bluetooth devices", finder search hint for Wi-Fi, left-behind monitor and
  More's count ignore Wi-Fi favorites, Help covers Wi-Fi, the spectrum, breadcrumbs and restart, and
  Privacy now lists OpenFreeMap among the tile sources.

## 5.6: Google-Maps-like dark map
The owner sent a screenshot of the OpenFreeMap "dark" style (near-black, faint roads) and asked for it
to look like Google Maps' dark mode. Dark mode now loads the detailed "liberty" style and recolours it
after load with our own palette in `ui/NightMap.kt` (blue-grey base #1f2633, grey roads getting brighter
with road class, dark teal water, muted green parks, soft grey labels with dark halos). Layers are
matched by OpenMapTiles source layer + id keywords, so new/renamed layers still get a dark colour; road
shields keep their own text colour. No Google assets or colours copied. The build environment can't reach
tiles.openfreemap.org, so the look was not previewed before release.

## Repository upload

The owner asked for the project on GitHub with detailed commit messages, the APK, and documentation
good enough for another AI or human to continue. The APK is committed under `releases/` because the
agent's session wasn't allowed to create GitHub Releases.

## Ideas not built yet
- Wear OS tile (the owner declined).
- Restart the left-behind monitor after reboot (`BOOT_COMPLETED`).
- Proper release signing key (currently the debug key, fine for personal sideloading only).
- Widget configuration to pick which tag the map widget shows (currently the first tag with a location).
