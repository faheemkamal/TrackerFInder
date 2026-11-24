# Tracker Finder

A small Android app that shows the live Bluetooth signal from a Hoco E96 (or any BLE tracker)
so you can tell whether you are getting closer or further away.

## What it does
- Scans for Bluetooth Low Energy devices and flags ones that look like trackers
  (Google Find Hub, Apple Find My, Fast Pair, or named "hoco"/"E96").
- Tap your tracker to open the finder screen: a big circle that goes blue → yellow → red as you get
  closer, a "Getting warmer / colder" trend, smoothed and raw signal (dBm), a rough distance,
  a 60-second graph, and an optional beep that speeds up as you get closer (like a metal detector).
- "Calibrate at 1 m" improves the distance guess for your phone and tracker.
- Trackers change their Bluetooth address about every 15 minutes for privacy. If the selected one
  goes quiet for 8 s and a new tracker of the same type appears, the app follows it automatically.

## Home (default tab)

The app opens on Home: a summary of how many favorites are in Bluetooth range, every Find Hub tag with its
last location, accuracy and age (Map, History, Share buttons), quick actions (Scan, Map, Check now,
Park & guard) and a Checks & alerts card (interval, last and next check, stored locations, which alerts are on).

## New in 4.5

- Find my phone (More → Find my phone): learns what signal your tracker sends when its button is pressed, then rings the phone loudly when it hears it close by. Works only if the tracker sends such a signal.
- Quiet hours and safe places (More): no alerts at night; no left-behind alerts while the phone is at a safe place.
- Street addresses on Home, the map sheet, History list and the timeline (Android geocoder, cached).
- History: Path/Heatmap switch and a "Places visited" timeline (stops of 10+ minutes).
- Tag battery: read from the tag's Bluetooth advert using its identity key (FMDN hashed flags), warning when low. Only updates when the phone hears the tag.
- Quick Settings tile "Find tracker" and a "Tag on map" home-screen widget.

## Audit fixes (4.4)

- Bluetooth scanning is fast only in the finder and Nearby list, gentle on Home/Favorites, and off on Map/More.
- Screen stays on only while the finder is open. Device list updates at most twice a second.
- Turning Bluetooth off no longer causes false "left behind" alerts; scanning resumes when it's back on.
- "Check now" no longer doubles the background check schedule; manual checks now trigger movement/area alerts and backups.
- App lock hides the app from the recent-apps preview. The sign-in key bridge only accepts Google pages.
- Release build is shrunk with R8 (about 29 MB instead of 37 MB), signed with the same debug key.
- Your phone shows on the map as a small phone icon.

## Map look (4.3)

Full-screen map with a draggable bottom sheet: a list of your tags, or one tag's details with round action
buttons (Play sound, Locate, Find nearby, Directions, History, Share) and Park & guard. Tag pins are round
badges with the tag's initial, an accuracy halo and a soft pulse; picking a tag flies the camera to it.
Your phone shows as a blue dot with an accuracy circle and a compass cone; the top-right buttons jump to
you or fit all tags. All artwork is drawn in code (no Google icons or branding).

## Added in 4.2 (Tag Tracker Open parity)

- Park & guard button on the Map tab and Home (arms movement alerts from the current position).
- Share location from the tag menu or Home card.
- 24 h history range; "Show individual points" switch; accuracy circle around the last location.
- Map and History refresh themselves every minute while open; "Refresh tag list from Google" in the tag menu.
- Alert notifications have Ring tag and Open in Maps buttons.
- More → Find Hub: check status notification, unrestricted battery use, import history from CSV, 2 h interval.
- More → Privacy: app lock (password + hint, relocks after a minute in the background).
- More → Help, privacy & legal.

## Favorites
Open a tracker from **Nearby** and tap the star to add it to **Favorites** with a nickname (Keys,
Wallet, Bag…). The Favorites tab shows each one as "In range", "Possibly nearby" or "Last seen …",
with a **Find** button that opens the finder straight away. Rename, remove or open Google Find Hub
from each card's menu. With exactly one favorite, the app opens it automatically on launch.

How reliable re-finding is: trackers like the E96 deliberately hide who they are. They change their
Bluetooth address and ID about every 15 minutes, so nothing in the signal permanently identifies
*your* tracker. The app remembers the last address it heard (exact match while it hasn't rotated)
plus the tracker's type. After a rotation it picks the strongest tracker of the same type, and warns
you when look-alikes are in range. Several favorites of the same model can't be told apart reliably.

## Finding aids
- **Beep and vibrate:** both speed up as you get closer. Turn either on in the finder or the More tab.
- **Direction mode:** in the finder, switch to "Direction". Hold the phone flat against your chest and
  turn slowly in a full circle. Your body blocks Bluetooth, so the signal is strongest when you face the
  tracker. The dial records each direction and tells you to turn left or right. Do a new sweep every
  few meters.

## Left-behind alerts
In Favorites, open a tracker's menu and pick "Alert me if left behind". The app then watches in the
background (with a permanent notification, which Android requires) and alerts you when that
tracker hasn't been heard for 2 minutes after being near you. Limits: it can't tell your tracker from
a look-alike nearby, it uses some extra battery, and it doesn't restart by itself after a phone reboot
until you open the app once.

## Widget
Long-press the home screen → Widgets → Tracker Finder. It lists up to four favorites as "In range" or
"Last heard …"; tap one to open its finder. Android refreshes widgets on its own schedule, so it's
most current while the app or the left-behind monitor is running.

## Find Hub map (from Tag Tracker Open)
The **Map** tab connects to Google's Find Hub network, so you can see your tags from anywhere:
- Live location of each tag on an OpenStreetMap map, **Locate** now, and **Ring** the tag.
- Saved location history with date ranges (today, 2/7/30/90 days, 1 year, custom), the path on the
  map, a trip **replay**, and stats (distance, time span, locations, average speed).
- **Background checks** every 10 minutes (adjustable). Android may delay them while the phone is idle.
- **Movement alerts** (moved more than 150 m) and per-tag **area alerts** (left / came back to a circle).
- **CSV and GPX export**, **Google Drive backup** (plus a backup-file alternative and restore).
- **Nearby** button: links the tag to one of your favorites and opens the signal finder.

Setup is two one-time steps on Google's own pages in the app: sign in, then unlock encryption with
your phone's screen lock. This uses an unofficial client (GoogleFindMyTools); it can break if Google
changes things and may be against Google's terms, so a secondary account is safer. Tokens and history
stay on the phone (`files/findhub`), excluded from Android backups.

### License
The Find Hub engine comes from Tag Tracker Open (a fork of Shekh-Muhsen's Tag Tracker) and
GoogleFindMyTools by Leon Böttger, both GPL-3.0, so this app as a whole is GPL-3.0. License texts:
`third_party/TagTrackerOpen-LICENSE.txt` and `third_party/GoogleFindMyTools/LICENSE`. Python sources are in
`app/src/main/python` and `third_party/GoogleFindMyTools`.

## Theme
More → Appearance: System, Light or Dark.

## Build and install
1. Install Android Studio (Ladybug 2024.2 or newer).
2. File → Open → select this `TrackerFinder` folder. Let Gradle sync (it downloads what it needs).
3. On your phone: Settings → About phone → tap "Build number" 7 times, then enable
   Developer options → USB debugging. Plug the phone in and accept the prompt.
4. Press Run ▶ in Android Studio. Or from a terminal: `./gradlew installDebug`.
5. To get an APK file instead: Build → Build App Bundle(s)/APK(s) → Build APK(s). The file lands in
   `app/build/outputs/apk/debug/app-debug.apk`; copy it to the phone and open it.

Requires Android 8.0+ with Bluetooth LE, on a 64-bit ARM phone (almost all current phones). Building needs Python 3.13 on your PATH for the Find Hub engine. On first launch, allow "Nearby devices" and "Location".
If nothing shows up, turn on Location in quick settings.

## How to find your E96 in the list
Hold the phone right next to the tracker. Its entry should be at the top with the strongest signal
(around -40 to -55 dBm). Press the tracker's button if it doesn't appear.

## Limits
- Distance is an estimate. Walls, bodies, pockets and phone models change the signal a lot, so the
  warmer/colder trend is more reliable than the meters number.
- The app only listens. It cannot make the tracker ring; use the Find Hub app for that.
- Scanning runs only while the app is open (the screen stays on).
