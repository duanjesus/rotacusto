# RotaCusto

Trip cost calculator for road travel — fuel/energy, tolls, vehicle wear, and food
stops — plus live turn-by-turn GPS navigation with voice guidance once a trip is
calculated. Runs on Windows desktop and Android from the same Flutter codebase.
Routing and maps run on OpenStreetMap (Nominatim + OpenRouteService + Overpass),
no Google Maps billing required.

Supports car, motorcycle, van, truck, and bus, including plug-in hybrids and pure
electric vehicles — see [`CLAUDE.md`](CLAUDE.md) for how the ~9000-row vehicle
catalog mixes real government data with clearly-documented estimates where no
official source exists.

## Structure

- [`backend/`](backend) — Spring Boot API. Vehicle catalog (consumption/wear by
  model), geocoding, routing (incl. turn-by-turn instructions), toll-plaza
  detection on the route, and the cost calculation engine.
- [`app/`](app) — Flutter client (Windows desktop + Android). Trip form, vehicle
  picker, OSM map with the route and crossed tolls, and a live navigation screen
  (GPS position, voice-guided instructions, automatic rerouting on deviation).

## Running locally

**Backend** (needs JDK 21+; Maven Wrapper included):

```
cd backend
ORS_API_KEY=<your-openrouteservice-key> ./mvnw spring-boot:run
```

Get a free key at [openrouteservice.org](https://openrouteservice.org).

**App** (needs the Flutter SDK):

```
cd app
flutter run -d windows   # or -d chrome / -d edge
```

## Status

Core roadmap complete: multi-vehicle cost calculation (fuel/energy, tolls, wear,
food stops), multi-stop routing, Windows + Android apps, in-app turn-by-turn
navigation with voice, automatic rerouting and Android background operation,
a real persistent Postgres database, optional user accounts with trip
history, and offline resilience (cached map tiles, resume a calculated trip
with no connection). CI runs the backend and Flutter test suites on every
push. See `CLAUDE.md`'s "Known gaps" section for what's left (iOS, app store
distribution — both blocked on hardware/accounts this environment doesn't have).
