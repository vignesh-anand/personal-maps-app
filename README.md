# Scoot Transit

A personal Android app for someone who gets around the Bay Area by escooter + Caltrain + BART.
Off-the-shelf transit apps assume you walk or drive to the station; this one assumes you'll scoot
up to ~15 miles to the best station for the trip and treats that as the access/egress mode.

## Features (v1)

- **Caltrain** page: live "next northbound / southbound" cards for your three favorite stations
  plus a fourth that auto-populates from GPS. Tap any card to see the rest of the day. Search
  any station, or run a from→to station-pair query.
- **BART** page: same 4-card layout, plus by-line and station-pair tabs. Live ETDs via BART's own
  ETD API supplemented by 511.org GTFS-RT.
- **Wayfinding** page: multimodal trip planner. Enter from/to (typed address with Places
  autocomplete, GPS chip, Home/Work chips). Pick depart-now / depart-at / arrive-by. Returns
  ranked plans with leave-by times. Optional Google Map view shows route polylines and a
  reachability circle for your scooter range. Save any trip as a preset (also pinned as an
  Android home-screen shortcut for one-tap launch).
- **Settings**: Home + Work addresses (geocoded via Google Places), favorite stations editor,
  scooter range slider, notification toggles, GTFS data status.
- **Home-screen widget**: next NB / SB at your nearest favorite Caltrain station.
- **Notifications**: subscribed service alerts (Caltrain / BART) and last-train-of-night warning.

## Architecture

- 100% native Android - Kotlin + Jetpack Compose
- No backend service. All data comes from public APIs:
  - **511.org Open Data** for GTFS static + GTFS-RT (TripUpdates / VehiclePositions /
    ServiceAlerts) and SIRI StopMonitoring for buses.
  - **api.bart.gov ETD** as a backup live-ETA source for BART.
  - **Google Maps Platform** for tiles, Places Autocomplete, Geocoding, *and* scooter street
    routing (Directions API in `bicycling` mode with a small speed-up factor applied).
- Local SQLite (Room) caches Caltrain + BART static GTFS, scoot-leg Directions responses,
  favorites, presets, and user prefs.
- `WorkManager` refreshes static GTFS weekly, polls service alerts every 15 min, and runs the
  last-train check hourly in the evening.
- The multimodal planner is a hand-rolled candidate-station enumeration: pick K nearest
  stations to origin and dest within scooter range, ask Google Directions for the access/egress
  legs, look up matching trips in the local DB, merge GTFS-RT, and rank by total duration.

## Setup

You need two free API keys:

1. **511.org Bay Area Open Data** - https://511.org/open-data/token (free, instant)
2. **Google Maps Platform** - https://console.cloud.google.com. Create a project, then enable:
   **Maps SDK for Android**, **Places API (New)**, **Geocoding API**, and **Directions API**.
   Add a billing account (no charge for personal use). Restrict the key to your debug +
   release SHA-1 once the app is running.

(BART uses a public legacy default key so no signup is needed there.)

Then copy `local.properties.example` to `local.properties` and fill in:

```properties
sdk.dir=/path/to/Android/Sdk
SCOOT_511_API_KEY=...
SCOOT_BART_API_KEY=MW9S-E7SL-26DU-VV8V
SCOOT_GOOGLE_MAPS_API_KEY=...
```

Open the project in Android Studio (Hedgehog or newer recommended) and let it sync. First launch
will download Caltrain + BART GTFS in the background (~10 MB each, one-time).

If you are building on a fresh checkout with only `gradle` in PATH, run:

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew :app:assembleDebug
```

## Project layout

```
app/src/main/kotlin/com/scoot/transit/
  data/                  # Repositories + Room DB + Retrofit services
    db/                  # Entities + DAOs
    gtfs/                # GTFS zip parser
    remote/              # Retrofit interfaces (511, BART, ORS, SIRI)
  domain/                # Pure-Kotlin models (Station, Departure, TripPlan, etc.)
  routing/               # Multimodal trip planner
  ui/
    caltrain/            # Caltrain page + station detail
    bart/                # BART page (stations / by-line / pair)
    wayfinding/          # Multimodal trip planner UI + map
    settings/            # Settings page
    common/              # Shared Compose components
    theme/               # Material 3 theme
  widget/                # Glance home-screen widget
  work/                  # WorkManager workers (GTFS refresh, alerts, last-train)
  di/                    # Hilt modules (Database, Network)
```

## Known limits

- Bus routing is intentionally minimal in v1 - the SIRI fallback gives "next departures at a
  bus stop" but doesn't do full multimodal bus + train planning. Add bus GTFS via 511 if you
  need richer bus routing.
- Google Directions usage is well within the $200/mo free tier for personal use, but if you ever
  hit the quota the app gracefully falls back to a haversine-based estimate.
- Caltrain GTFS-RT can be flaky; when it is, station cards show "schedule only" and live
  badges go gray.
- The escooter range model is single-value for v1 (default 15 mi); add a current-battery input
  later if you want range to scale with charge state.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE` for API calls
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for nearest-station + GPS chip
- `POST_NOTIFICATIONS` for service alerts and last-train warnings
- `RECEIVE_BOOT_COMPLETED` for `WorkManager` to re-arm after reboot

## License

Personal project, no license; copy what you like.
