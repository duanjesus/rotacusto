# RotaCusto

Trip cost calculator for road travel: fuel, tolls, vehicle wear, and (soon) food
stops — starting on desktop (Windows), with mobile to follow from the same
Flutter codebase. Routing and maps run on OpenStreetMap (Nominatim + OpenRouteService),
no Google Maps billing required.

## Structure

- [`backend/`](backend) — Spring Boot API. Vehicle catalog (consumption/wear by
  model), geocoding, routing, toll-plaza detection on the route, and the cost
  calculation engine.
- [`app/`](app) — Flutter client (Windows desktop first, Android/web later).
  Trip form, vehicle picker, and an OSM map showing the route and crossed tolls.

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

MVP backend (fuel, wear, tolls, vehicle catalog) is implemented and tested.
Food-stop cost, multi-vehicle-type toll pricing (motorcycle/truck/van/bus), and
in-app turn-by-turn navigation are planned next.
