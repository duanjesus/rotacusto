# CLAUDE.md

Guidance for Claude Code (or any AI coding agent) working in this repository.

## What this is

RotaCusto calculates the **total cost of a road trip** (fuel/energy, tolls, vehicle
wear, food stops) and, once a trip is calculated, offers **live turn-by-turn GPS
navigation** with voice guidance. Monorepo:

```
rotacusto/
├── backend/   Spring Boot 3 REST API (Java 21, H2 in-memory, no auth)
├── app/       Flutter client — Windows desktop + Android, same codebase
└── .github/workflows/ci.yml   backend (mvnw test) + frontend (flutter analyze/test)
```

The **cost/routing "brain" lives entirely in the backend** — the Flutter app never
calculates anything itself, it only sends parameters and renders the response. Routes
and maps run on OpenStreetMap infrastructure (Nominatim geocoding, OpenRouteService
routing, Overpass for tolls/fuel stations, CartoDB tiles) — no Google Maps, no billing.

## Running locally

```bash
# Backend (JDK 21, Maven Wrapper committed — no local Maven needed)
cd backend
ORS_API_KEY=<your-openrouteservice-key> JWT_SECRET=<32+ char random string> ./mvnw spring-boot:run   # ORS key: openrouteservice.org

# App
cd app
flutter run -d windows   # or -d chrome/edge; Android needs an emulator/device + `adb reverse` or 10.0.2.2 (handled automatically, see api_client.dart)
```

**Postgres is real and persistent, but starts itself** — `RotaCustoApplication.main()`
boots an embedded Postgres server (`io.zonky.test:embedded-postgres`) on port 5433
*before* Spring context startup, pointed at `~/.rotacusto/pgdata` (outside the repo, on
purpose — never at risk of being committed). No Docker, no separate service to start;
`./mvnw spring-boot:run` is still the only command needed, and data survives restarts
(including unclean ones — Postgres does WAL crash recovery on the next start). The
vehicle/toll seeders are idempotent (`if (repository.count() > 0) return;`) so they
never duplicate data on restart. `JWT_SECRET` is optional in dev (a random one is
generated per-process if unset) but every existing session becomes invalid whenever the
secret changes, so set it for anything beyond a quick local test.

**Tests never touch the embedded Postgres** — `@SpringBootTest` boots the Spring context
directly, without going through `main()`, so it never starts Postgres. Instead,
`backend/src/test/resources/application.yml` (a separate file, picked up automatically
because the test classpath shadows `src/main/resources` for identically-named files)
configures H2 in-memory, exactly like the whole project used before Postgres existed.
This is also why **CI needs zero external services** — no Postgres container, nothing.

Login is **entirely optional** — every endpoint that existed before accounts were added
stays public; only `/api/trip-history/**` requires a JWT (`Authorization: Bearer …`,
via `SecurityConfig`/`JwtAuthFilter`). "Missing vehicle" reports still go to a flat file
(`vehicle-reports.log`), not the database — see below for why.

## Vehicle catalog — the part with the most nuance

`backend/src/main/resources/data/vehicle-models.json` (~9000 rows) and its seeder
`VehicleModelSeeder.java` have **extensive Javadoc explaining exactly which rows are
real official data vs. estimated, and why** — read that class before touching the
catalog. Summary:

- **Cars (combustion + electric)**: real INMETRO PBE Veicular data, 2016–2026, extracted
  from government PDFs via `tabula-java` (value-based parsing, not fixed column
  position — the PDF layout shifts between years and even between pages of the same
  PDF). A flex car is **two rows** (GASOLINA + ETANOL), not one row with gasoline as a
  proxy — `tipoCombustivel` is part of the record's identity.
- **Motorcycles, heavy trucks, heavy buses**: **no official Brazilian source exists**
  at this granularity. Methodology: use a real, publicly verifiable spec (engine
  `cilindradaCC` for motos; `pbtKg` — peso bruto total — for trucks/buses) to drive an
  *estimated* consumption via linear interpolation between real-world anchor points,
  documented as an estimate, never presented as official. Replicated across all years
  2016–2026 (explicit user request, even though there's no real per-year difference).
- **Vans / light trucks / micro-buses**: **not new data** — PBE already certifies some
  light commercial vehicles under CARRO, so these were found by retagging existing
  entries by known model name (Ducato→VAN, Kia Bongo→CAMINHAO, Master Bus→ONIBUS, etc.)
  and keep their real consumption figures.
- **Plug-in hybrids (PHEV)**: INMETRO's PBE label exposes an "equivalent" consumption
  number (electric + gasoline blended into one inflated km/l) that **overstates real
  economy once the battery is depleted** — a few PHEVs that slipped into the catalog
  early on (e.g. old Volvo XC90 T8 entry) used that number by mistake. Fixed PHEVs are
  modeled as two paired rows (ELETRICO with `consumoKmPorKWh` = autonomia÷bateria, plus
  a combustion row), same pattern as flex fuel — see `ManeuverTranslator`-adjacent
  commentary in `VehicleModelSeeder.java` for the full list of what was fixed vs. what's
  still using the misleading number (BMW 530e — no reliable source found to fix it yet).
- **`cilindradaCC`** (moto) and **`pbtKg`** (caminhão/ônibus) are nullable columns only
  populated for the *estimated* segments above — real PBE-derived rows leave them null.
  Tests assert this distinction (`SeedersIntegrationTest`).

**Research methodology that works for this kind of gap**: when a government PDF or
manufacturer spec sheet blocks a web-search agent, don't retry the same source —
switch to Brazilian trade press / dealer spec sites that quote the number in HTML
(`viacircular.com.br`, `diariodotransporte.com.br`, `carrosnaweb.com.br`, `blog
docaminhoneiro.com` worked repeatedly; raw manufacturer PDFs consistently didn't). If a
source only gives an ambiguous range across multiple variants, skip it — this catalog
consistently prefers fewer real numbers over more imprecise ones.

## Tolls

`backend/src/main/resources/data/tollplazas.json` is a **national curated seed** (159
entries: 158 real federal toll plazas + 1 state plaza) combined at runtime with **live
OSM Overpass** queries (`TollService`) — Overpass gives precise real-time coordinates
for any toll booth in Brazil; the curated dataset only *enriches* name/concessionária/
tariff when a coordinate match is found, never adds its own entry. `tarifaPorEixo` and
`tarifaMoto` are both **nullable** on `TollPlaza` — a curated entry can have a real,
confirmed location/direction without a confirmed price. When `tarifaPorEixo` is null,
`TollService` substitutes the configured default rather than letting `null` reach
`TollCostCalculator` (`tarifaPorEixo × numeroEixos` would NPE otherwise) — so an
unpriced plaza still shows its **real name and concessionária** instead of the generic
"Pedágio (OpenStreetMap)" fallback, just with an approximate default price.

**Scale of the national expansion**: the user asked to verify pricing/direction for
every toll in Brazil, not just the RJ corridor already fixed. Researching each of the
~400-1,800 toll points in Brazil individually (the method used for RJ) isn't feasible —
instead, `TollPlazaSeeder` now loads from the **ANTT's own open-data KMZ**
(`dados.antt.gov.br/dataset/praca-de-pedagio`), which lists every toll plaza on every
federally-concessioned highway (23 concessions, 158 active plazas) with real
coordinates, highway, km marker, municipality, and lane direction
(Crescente/Decrescente/both). Parsed from KML (`Placemark` → HTML table embedded in a
CDATA `description`) via a one-off PowerShell script — there's no clean CSV/JSON
resource for this dataset, only KMZ + a PDF data dictionary.

- **Direction is solved differently than before, and better**: earlier one-way plazas
  (like the Rio-Niterói bridge) needed the bearing-based `cobraApenasIndo` +
  `refLat`/`refLng` mechanism because only one approximate coordinate existed for a toll
  that's physically only on one carriageway. The ANTT data gives each physical booth its
  **own precise coordinate** — a divided highway's two carriageways are geographically
  separate, so the existing tight `osm-detection-radius-km` (0.1km) naturally excludes a
  route on the wrong carriageway without any bearing math. The `cobraApenasIndo`
  mechanism is only still used for Ecoponte (Ponte Rio-Niterói), which really does have
  just one physical booth for both direction's traffic patterns.
- **Tariff is NOT uniform per concession as a rule — that was a lucky case, not the
  norm**: researching one tariff per concession (~23 searches) was the plan, mirroring
  what worked for Arteris Fluminense in the RJ fix. Turned out most larger/older
  concessions charge **different prices at different plazas** (e.g., EcoRioMinas ranges
  R$8.90–R$13.30 across 6 plazas; Nova Dutra/CCR RioSP ranges R$8.10–R$16.90 across 15).
  Applying one blanket number to those would reintroduce the exact class of error this
  work was meant to fix. Only concessions with an explicit uniform-tariff confirmation
  got a curated price: Autopista Fernão Dias, Autopista Litoral Sul, Autopista Planalto
  Sul, Autopista Régis Bittencourt, Autopista Fluminense (Arteris), Eco101/Ecovias
  Capixaba, Via Costeira, Ecoponte — 43 of the 158 federal plazas. The rest (including
  entire major concessions like Nova Dutra) keep their real coordinate/name but fall back
  to the default tariff, which is the honest state given no reliable confirmed number.
- **Brazilian toll tariffs are always quoted as the total price for a standard 2-axle
  car**, never per-axle — a real bug caught only by testing live (not by unit tests,
  since the seed data itself was wrong): the first version of this dataset stored that
  total directly as `tarifaPorEixo`, doubling every price once `× numeroEixos` ran
  (`TollCostCalculator`). Fixed by halving every confirmed car price before writing it as
  `tarifaPorEixo`. `tarifaMoto` doesn't need this — motorcycle tariffs are charged flat,
  not `× eixos`, so the quoted moto price is used directly.
- **Itaboraí (RJ-116) and the Rio-Niterói bridge are state/non-federal-KMZ exceptions**,
  fixed earlier this session: Itaboraí was wrongly marked one-way (`cobraApenasIndo:
  false`, "só na volta") when Rota 116's own tariff page says "cobrança bidirecional" —
  fixed by dropping the direction constraint entirely. The bridge's direction was already
  correct (Rio→Niterói only, confirmed by EcoRodovias).
- **São Paulo (ARTESP) added next — 151 more real plazas, richer data than the federal
  set**: state highways have no equivalent to the ANTT's single national dataset (each
  state has its own regulator), but São Paulo's `malha-rodoviaria` open-data KMZ (one
  file per concession, 23 total) turned out to embed the **actual tariff table** (Leves/
  Comercial por eixo/Motos) directly in each toll plaza's description — no separate
  per-concession price research needed, unlike the federal ANTT data which is
  location-only. Same total-vs-per-axle halving applies (`Leves` is still the flat
  2-axle car price). ~30 plazas were skipped on purpose: free-flow/gantry tolling (billed
  per km driven, not a fixed per-crossing price — doesn't fit this app's
  `tarifaPorEixo × eixos` model) or explicitly marked "(Desativado)". **Gotcha that
  silently zeroed out 21 of 23 files on the first pass**: the folder name holding each
  concession's toll plazas varies by file — "Praças de Pedagio_25" (no accent) in one,
  "Praça de Pedágio" (accented á) in most others — a regex that only matched the
  unaccented form found real data in just 2 files until the accent was added to the
  character class.
- **State-by-state survey completed — most states genuinely have no state-level toll
  network to cover**, not just unresearched: São Paulo (done, 151 plazas), Rio de
  Janeiro (done except one plaza, below), and now Rio Grande do Sul (done, below) are
  the state networks with real data. Paraná has one too but its DER is actively
  *shutting down* state tolls, and no toll-specific open dataset was found there. Minas
  Gerais, Bahia, Santa Catarina, Goiás, and the rest have no meaningful state toll
  network at all — their tolls are on federal (BR-xxx) highways, already covered by the
  ANTT dataset above.
- **Rio Grande do Sul (21 new plazas + 7 enriched)**: the original DAER server
  (`i3geo.daer.rs.gov.br`) kept refusing every connection across sessions, but the same
  dataset turned out to be published by a *different* government service — IEDE/RS
  (Infraestrutura Estadual de Dados Espaciais), a public ArcGIS FeatureServer at
  `iede.rs.gov.br/server/rest/services/DAER/pracas_pedagios/FeatureServer/0` that
  answers HTTP 200 and returns clean GeoJSON (33 plazas, exact coordinates, highway/km,
  concession, and a `url_tarifa` link per concession). Lesson: when a government
  geospatial service is down, look for the same dataset republished on a state spatial
  data infrastructure (IDE) portal before giving up — it's common for the same data to
  live in more than one service. Of the 33 plazas, only 21 are genuinely state-level
  (ERS-/RSC- highway prefix): 10 EGR, 6 CSG, 5 Sacyr. The other 12 (ECOSUL 5, CCR ViaSul
  7) run on federal highways (BR-101/116/290/386/392) and were **already** in the
  federal ANTT dataset — confirmed by name/coordinate before adding anything, to avoid
  duplicating a plaza.
  - **CSG** (6 plazas, ERS-122/240/446): "free-flow" tolling (electronic gantry, no
    physical booth) but with a **fixed price per gantry**, unlike São Paulo's free-flow
    plazas which bill per km driven and were left uncurated for that reason. Confirmed
    by two independent, mutually consistent sources (the Feb/2025 adjustment article and
    the Apr/2026 one, whose deltas match).
  - **Sacyr/Rota de Santa Maria** (5 plazas, RSC-287): single confirmed uniform tariff
    across the whole stretch, R$5.40/car (2026), moto R$2.70 flat.
  - **EGR** (10 plazas): tariff varies a lot per plaza (e.g. Gramado R$7.10 vs. Coxilha
    R$4.40 in 2024 data) and the only official table is published as an **image**, not
    extractable text, with no 2026 text source found — left uncurated on purpose, same
    treatment as EcoRioMinas.
  - **CCR ViaSul (now "Motiva")**: already existed in the federal dataset (concession
    "Via Sul", 7 plazas, no tariff) — enriched with the confirmed uniform value from
    multiple consistent, dated local news sources: R$6.60/car = R$3.30/axle, effective
    2026-06-26.
  - **Ecosul**: already existed (5 plazas, BR-116/392), left uncurated on purpose — the
    sources found were genuinely conflicting (R$12.30 on one ANTT portal page vs.
    R$19.60→R$22.20 in dated news), and ANTT itself confirmed the R$22.20 adjustment was
    approved but has "no immediate impact for users," with the concession contract due
    to end March 2026 (this data is from July 2026 — the concession may have already
    ended). Preferred leaving it uncurated over guessing a possibly-wrong or
    possibly-defunct number.
- **RJ-124 (Via Lagos) now has real weekday/weekend pricing**: the only toll found in
  this entire effort with genuine day-of-week variation (R$18.40 total/car Mon–Fri vs.
  R$30.60 weekends and holidays, moto exempt). `TollPlaza` gained a nullable
  `tarifaPorEixoFimDeSemana` (null on every other plaza in the dataset), and
  `TollCostCalculator.calculate` takes the trip's `LocalDate` as an explicit parameter —
  Saturday/Sunday use the weekend rate when one is set, otherwise the normal
  `tarifaPorEixo` applies. The date is threaded through as a real parameter rather than
  called via `LocalDate.now()` inside the calculator, mirroring the pure/testable-with-
  synthetic-input pattern already used by the Flutter-side detectors
  (`deviation_detector.dart`, `traffic_detector.dart`) — lets tests exercise a fixed
  Tuesday/Saturday/Sunday deterministically instead of depending on the day the suite
  happens to run. **National holidays are not detected** — only Saturday/Sunday; a full
  Brazilian holiday calendar (fixed + movable/Easter-based dates) is disproportionate
  effort for the one toll plaza that needs it, documented as an accepted simplification.

See `TollPlazaSeeder`'s Javadoc for the full per-concession sourcing (dates, confirmed
values, and the complete state-by-state survey) and `SeedersIntegrationTest` for the
regression assertions (332-row count, EcoRioMinas and EGR and Ecosul correctly
uncurated, Fernão Dias/Autoban/CSG/Sacyr/Via Sul correctly curated at the halved rate,
Ecoponte still direction-constrained, Itaboraí still bidirectional, Via Lagos's
weekday/weekend/moto values).

## Navigation (turn-by-turn, voice, rerouting)

- The ORS directions response already includes `segments[].steps[]` (turn-by-turn) —
  parsed by `OpenRouteServiceClient`. **The `language` query param is ignored by this
  ORS deployment** (verified directly against the API) — instructions are built in
  Portuguese from the numeric `type` maneuver code + `name` (street name) via
  `domain/geo/ManeuverTranslator.java`, not from ORS's own text.
- `app/lib/domain/navigation/route_progress.dart` (pure, no Flutter/network) matches a
  live GPS position to the nearest point on the route geometry and the current
  instruction — this is what both the instruction banner and the deviation detector
  read from.
- `app/lib/domain/navigation/deviation_detector.dart` (also pure) requires **3
  consecutive GPS readings** more than 60m from the route before triggering a reroute —
  a single reading doesn't (GPS noise/multipath). Rerouting reuses the *same*
  `/api/trips/estimate` endpoint with the live GPS position as `origem` — no new backend
  endpoint was needed, since `GeocodingService.resolve()` already special-cases
  `"lat,lon"` strings and skips geocoding. Rerouting is **not offered for round trips**
  (`_breakdownIdaEVolta`) — the combined ida+volta breakdown concatenates both legs'
  geometry and there's no simple way to know which leg was deviated.
- Voice (`flutter_tts`, pt-BR) speaks a step only when it *changes* (tracked index), not
  on every GPS tick. After a reroute, that tracked index **must** reset to null — it's
  relative to the new `passosRota` list, not the old one.
- **Background operation** (Android): `app/lib/domain/navigation/navigation_task_handler.dart`
  runs the whole position→instruction→voice→recálculo loop inside a
  `flutter_foreground_task` service (separate isolate, persistent notification) —
  survives the screen turning off or the user switching to another app.
  `NavigationScreen` branches by platform: Android starts the service and listens via
  `addTaskDataCallback`; other platforms (Windows) keep the original direct
  `Geolocator.getPositionStream()` subscription, since the plugin only declares Android/iOS
  support — calling any of its methods elsewhere throws `MissingPluginException`.
  **Gotcha**: the background isolate has no Flutter binding by default — any
  MethodChannel-based plugin (`flutter_tts`, `geolocator`) throws
  `Cannot set the method call handler before the binary messenger has been initialized`
  unless `WidgetsFlutterBinding.ensureInitialized()` runs first in the isolate's
  `@pragma('vm:entry-point')` entry function.

## Road alerts (community-reported hazards)

Anyone can report a hazard (pothole, police checkpoint, fog, broken-down car, accident,
roadwork) — **no login required**, same "accounts unlock extras, never gate core
features" philosophy as the rest of the app. `RoadAlertService` (backend) has no external
data source — it's purely `POST /api/road-alerts` (report) and `POST
/api/road-alerts/nearby` (query by point + radius), backed by the persistent Postgres
from Fase 6.4a.

- **Expiration is time-based, not confirmation-based** — no "is this still here?" voting
  system (v1 scope decision). Each `RoadAlertType` has a default validity duration (a
  documented guess, not sourced data, same honesty convention as `custoDesgastePorKm`):
  transient events (BLITZ/CARRO_QUEBRADO/ACIDENTE) expire in 2–3h, NEBLINA in 4h,
  OBRA_NA_VIA in 14 days, BURACO in 30 days. A daily `@Scheduled` job deletes expired rows
  (`RotaCustoApplication` needs `@EnableScheduling` for this to actually run).
- **Two delivery paths**: alerts near the route at calculation time ride along in
  `TripCostBreakdownDTO.alertasNaRota` (same pattern as tolls/fuel stations — a third
  parallel `CompletableFuture` in `TripEstimationService`). Alerts reported *after* the
  trip was calculated arrive via a separate live poll
  (`app/lib/domain/navigation/navigation_task_handler.dart` /
  `navigation_screen.dart`'s Windows path, `Timer.periodic` every 3 min, 5km radius) — the
  two lists get merged by id in `TripMap`.
- **Gotcha (found live-testing, not by compile/test)**: the first poll must NOT fire in
  `onStart()`/`_iniciar()` directly — at that point no GPS reading has arrived yet, so
  "current position" is still null and the fetch silently no-ops. It has to fire from
  inside the position-stream callback, gated on "is this the first reading" (see
  `_onPosicao`/`_onPosicaoWindows`), otherwise the app doesn't know about nearby alerts
  until the first periodic tick, several minutes late.
- Voice announcement (`RoadAlertProximityChecker`, pure, mirrors `DeviationDetector`)
  fires once per alert per navigation session (tracks announced ids) when within 500m —
  reuses the same TTS instance as turn-by-turn instructions, so it never talks over
  itself.
- Reporting is a **UI-triggered action** (`RoadAlertPicker` bottom sheet + a FAB on
  `NavigationScreen`), not something the background isolate does on its own — unlike
  polling/voice, it isn't duplicated into `NavigationTaskHandler`, since it always needs
  someone physically tapping a button on Windows or Android.
- **Confirmation/reputation** ("ainda está lá?"/"já foi resolvido"): the proximity
  SnackBar in `_mostrarAvisoDeAlerta` grew two icon buttons wired to
  `RoadAlertService.vote()`. Confirming re-applies the type's default duration to
  `expiraEm` (same map `report()` already uses); denying accumulates until
  `rotacusto.road-alerts.negative-votes-to-expire` (2) distinct devices have denied,
  then `expiraEm` is set to now — reusing the same visibility mechanism instead of a new
  `ativo` flag. One vote per device per alert, enforced by a unique constraint on
  `RoadAlertVote(road_alert_id, device_id)` — `device_id` is a UUID generated once and
  persisted locally (`device_id.dart`, same `shared_preferences` pattern as
  `auth_controller.dart`), never tied to an account.
- **Gotcha, found only by running with a real Spring context**: `limparExpirados()`'s
  bulk `@Modifying` DELETE query threw `TransactionRequiredException` every time it ran
  — `@Scheduled` methods don't get a transaction for free, and Mockito-backed unit tests
  never noticed because mocks don't care about transactions. Only surfaced once a
  `@SpringBootTest` with a real database happened to run soon after app startup (a
  `fixedRate` `@Scheduled` fires almost immediately on boot, unlike a cron tied to a
  specific wall-clock hour). Both `RoadAlertService.limparExpirados()` and
  `TrafficReportService.limparExpirados()` needed `@Transactional` added — the second
  one had silently had this same bug since Fase 6.7 shipped, just never exercised by a
  test run.
- **Known verification gap**: the vote buttons were confirmed to render with the right
  label/icons/tap bounds (via `uiautomator dump`, exact `content-desc` match), and the
  backend endpoint was fully verified via curl (confirm extends, duplicate vote → 409,
  2 denies expire immediately, missing id → 404) — but actually tapping a button on the
  live SnackBar during an emulator session wasn't captured on video/screenshot: the
  SnackBar auto-dismisses after 7s, and every attempt to script a tap via `adb shell
  input tap` lost the race against that window (confirmed by the FAB — which reflows
  into the same screen position once the SnackBar is gone — receiving the tap instead).
  Not a code defect, just a testing-tooling limitation with this interaction specifically.

## Automatic traffic-jam detection (community-shared)

Waze-style, but the report is **automatic**, not a button tap: the app compares live GPS
speed (`Position.speed`, from `geolocator`, unused until now) against the *expected*
speed of the current route step (`passosRota[i].distanciaM / duracaoS` — ORS already
computes this per step, so no new data source is needed and there's no coverage gap like
OSM's `maxspeed` tag would have in Brazil). `TrafficDetector` (pure, mirrors
`DeviationDetector`) classifies the ratio into LEVE/MEDIO/INTENSO after 3 consecutive slow
readings (avoids firing on a single stoplight stop) and enforces a cooldown between
auto-reports (avoids POSTing on every GPS tick during a sustained jam).

- **Same point+radius model as `RoadAlert`**, not a real line/segment — `TrafficReport`
  is just `(severidade, lat, lng, criadoEm, expiraEm)`. To "color the road" on the map
  without inventing segment geometry or OSM way-id matching, `buildTrafficSegments`
  (`app/lib/domain/navigation/traffic_overlay.dart`, pure, tested with synthetic
  geometry) finds the nearest point on `geometriaRota` to each report and takes a ~150m
  window around it as the colored `Polyline` — an approximation, not an exact road cut.
- **TTL is short and fixed (15 min)**, not per-type like `RoadAlertType` — a traffic jam
  doesn't have a "category" with different physical persistence the way a pothole vs.
  roadwork does. Cleanup runs every 10 min (`@Scheduled(fixedRate = ...)`), much more
  often than `RoadAlertService`'s daily cron, since a 15-minute TTL would otherwise leave
  expired rows piling up for most of a day between cleanups.
- **Gotcha confirmed live on the emulator**: the polling fetch (`_pollNearbyTraffic`/
  `_pollNearbyTrafficWindows`) is fire-and-forget — called without `await` so it doesn't
  block the synchronous position callback — and the `sendDataToMain`/`setState` that
  carries the fetched list to the map fires **before** that fetch resolves. With a real
  moving car this self-corrects within the next GPS tick or two and is invisible; but
  when testing with a single static `adb emu geo fix`, the map can show nothing until a
  *second* position update arrives (even a tiny nudge) to re-send the by-then-completed
  list. Not a bug to fix — the design already accepts "a tick or two of staleness" the
  same way `_pollNearbyAlerts` does — just a testing-methodology gotcha worth remembering
  for the next live verification.
- **Known verification gap, documented rather than overclaimed**: the automatic
  detection-by-GPS-speed path itself (`TrafficDetector` fed by live `Position.speed`) is
  covered by synthetic unit tests, but wasn't confirmed live on the emulator — the
  classic `adb emu geo fix` console command has no velocity parameter, and it's unclear
  whether the emulator's mock location provider synthesizes a non-zero `Position.speed`
  from consecutive fixes at all. The *reporting and display* halves of the feature (what
  happens once a severity is detected) reuse the exact same call path already proven live
  by manually POSTing a report via curl, which is the strongest verification available
  without deeper emulator GPS tooling.

## Offline mode

Calculating a **new** trip always needs connectivity (geocoding/routing/tolls are
external services) — that's a hard limit, not something to work around. What's actually
offline-resilient is a trip **already calculated**:

- **Map tiles** (`app/lib/presentation/widgets/trip_map.dart`) go through a deliberate
  disk cache (`app/lib/data/tile_cache.dart`, `flutter_map_cache` +
  `http_cache_file_store`, stored under `getApplicationSupportDirectory()` — not
  `getTemporaryDirectory()`, which the OS can clear). This is separate from flutter_map's
  own built-in cache (on by default since v8.2, but explicitly documented as offering "no
  guarantees" — not something to rely on for real offline behavior). Tiles never
  previously seen just don't render; no crash, no error.
- **Resuming after the app closes**: `app/lib/data/last_trip_cache.dart` persists the
  last successfully calculated breakdown (`shared_preferences`, reusing
  `TripCostBreakdown.toJson()`/`fromJson()` from the background-navigation work above) —
  `HomeScreen` shows a dismissible "Retomar navegação" card that jumps straight into
  `NavigationScreen` with zero network calls. Verified live on the emulator with WiFi and
  mobile data both disabled (`adb shell svc wifi disable` / `svc data disable`): resuming,
  the map, GPS position matching, and voice all worked with the connection fully off.
- Turn-by-turn guidance itself was already offline-safe before this — `route_progress.dart`
  and `deviation_detector.dart` are pure Dart with no network calls, and a failed reroute
  attempt already degrades to a `SnackBar` instead of crashing.

## iOS support

**Compiles, nothing beyond that** — this environment has no macOS access (Xcode is
macOS-only, no workaround), so `ios/` was scaffolded (`flutter create --platforms=ios .`,
safe to run from any OS) and CI got a new `ios` job (`.github/workflows/ci.yml`,
`runs-on: macos-latest`, a free GitHub-hosted Mac) running `flutter build ios --release
--no-codesign`. That job going green is the *only* verification possible right now — it
confirms the Dart code and every plugin in use compile for the iOS target, nothing more.
Nobody has run this on a simulator or real device, and nothing about signing,
provisioning, TestFlight, or App Store Connect has been touched.

- `ios/Runner/Info.plist` got `NSLocationWhenInUseUsageDescription` and
  `NSLocationAlwaysAndWhenInUseUsageDescription` (mirrors the `ACCESS_FINE_LOCATION`/
  `ACCESS_COARSE_LOCATION`/`ACCESS_BACKGROUND_LOCATION` already in
  `android/app/src/main/AndroidManifest.xml`) — required for `geolocator` to not crash
  requesting permission, plus a defensive entry for `permission_handler`'s native side
  even though the "always" branch is only ever reached on Android
  (`NavigationScreen._isAndroid`).
- **No code changes were needed for navigation itself** — `_isAndroid` already routes
  everything that isn't Android (Windows today, iOS now) through the same
  foreground-only branch: direct `Geolocator.getPositionStream()` subscription, no
  `flutter_foreground_task` service. iOS background navigation (the equivalent of the
  Fase 6.3 Android foreground service) would need entirely different APIs
  (`UIBackgroundModes`) and its own App Store review scrutiny — deliberately not
  attempted this round.
- **What only the user can unlock next**: an Apple Developer Program membership
  (US$99/year, requires their own identity and payment — not something that can be done
  on their behalf). Once that exists, code signing / provisioning profiles can be set up
  (including scripted via Fastlane in the same `macos-latest` CI job), and only then does
  running on a simulator/device, TestFlight, or App Store submission become possible.
- **App icon** (`assets/icon/icon.png`, generated to every platform via
  `dart run flutter_launcher_icons`, config in `pubspec.yaml`): a simple white map-pin on
  the app's Material 3 seed color (`#0E8C7F`) — replaces the default Flutter logo, which
  Android and web had also never had replaced until now (not iOS-specific, fixed
  everywhere in the same pass since it's the same one source image).
  `remove_alpha_ios: true` strips the alpha channel, which the App Store rejects icons
  for having. To change the icon later: edit `assets/icon/icon.png`, re-run the same
  command — don't hand-edit the generated per-platform files.

### Checklist for once the Apple Developer Program membership exists

Nothing below this line can be done without it — this is the literal next-actions list,
not a "someday" wishlist:

1. **Register the app** in developer.apple.com: create an App ID matching the bundle
   identifier already set (`com.rotacusto.rotacustoApp` — `flutter create` derived it
   from the Android `applicationId`, already consistent, no need to change it), then
   create the corresponding app record in App Store Connect.
2. **Code signing**: simplest path is opening `ios/Runner.xcworkspace` in Xcode on a
   real Mac once one is available and letting Xcode's "Automatically manage signing"
   handle certificates/provisioning profiles against the new account. For CI-driven
   signing instead (no Mac needed even at this stage), use Fastlane `match` to generate
   and store certificates, add them as encrypted GitHub secrets, and extend the `ios` CI
   job to do a signed `flutter build ipa` instead of `--no-codesign`.
3. **First real device/simulator run** — only possible after step 2. This is also the
   first point where genuine iOS-specific bugs might surface (none have been found yet,
   since nothing has run beyond compiling).
4. **TestFlight**: upload a signed build (`fastlane pilot upload` or Xcode Organizer),
   internal testing needs no review; external testing needs a quick beta app review.
5. **App Store submission checklist**: screenshots for required device sizes, app
   description/keywords, support URL, a **privacy policy URL** (mandatory — the app
   requests location access, must be disclosed), the App Privacy "nutrition label"
   questionnaire in App Store Connect (location data collected, not sold or used for
   tracking/ads), and an age rating questionnaire. Review is typically 24–48h.

## Default origin, recent/favorite destinations, route alternatives

Three small features shipped together (Fases 8–10), all client-visible on the home
screen, one gotcha discovered live against the real ORS API.

- **Origem defaults to current location** (`home_screen.dart`): `initState()` calls
  `_usarLocalizacaoAtual(auto: true)` alongside the existing default text ("Copacabana,
  Rio de Janeiro, RJ"). The `auto` flag makes every failure path silent — no `SnackBar`,
  and it refuses to overwrite the field if the user already started typing something
  else before the GPS lookup resolved (`if (auto && _origemController.text !=
  _origemPadrao) return;` right before the winning `setState`). A manual tap on "Usar
  localização atual" (`auto: false`, unchanged) still always overwrites and always shows
  errors — the auto path exists specifically to avoid a bad first impression (permission
  prompt or error banner) on cold start.
- **Recent/favorite destinations** — local-only (`shared_preferences`, no login, same
  philosophy as `last_trip_cache.dart`/`device_id.dart`): `app/lib/data/recent_destinations.dart`
  (cap 5, dedup by `displayName`, most-recent-first) and
  `app/lib/data/favorite_destinations.dart` (cap 20, toggle). `AddressField` gained
  optional `favoritos`/`recentes` params (default `const []`, so Origem/paradas are
  unaffected) — shown in place of the live suggestions dropdown whenever the field is
  empty; already re-evaluates on every keystroke because the existing `_onChanged`
  already calls `setState`. Only destinations picked from the autocomplete are eligible
  (`AddressSuggestion` always requires resolved `lat`/`lon`) — free-text destinations
  never resolved client-side can't be recent/favorited, an accepted limitation, not a bug.
  Destino gained a star `IconButton` toggling favorite state; a successful `_calcular()`
  call also records the destination as recent.
- **Route alternatives** (`POST /api/trips/estimate/alternatives`) — only offered for
  simple origin→destination trips (no paradas, no round trip): the ORS
  `alternative_routes` parameter only works with exactly 2 waypoints, not with
  intermediate stops. `OpenRouteServiceClient.getRoutes()` requests up to 2 alternatives
  beyond the primary route (`target_count: 2, weight_factor: 1.6, share_factor: 0.6`)
  and returns one `RouteResult` per feature in the response — may legitimately return
  just 1 if the ORS doesn't find a genuinely different path, not an error.
  `TripEstimationService.estimateAlternatives()` builds a full breakdown (tolls, fuel,
  alerts, traffic — everything, not an abbreviated version) per route. On the frontend,
  `HomeScreen._calcular()` sorts the results by `total` ascending and, when there's more
  than one, sets `_alternativas` instead of `_breakdown` and returns — `RouteAlternativesPicker`
  then renders **inline**, inside a `SectionCard` right below the "Calcular" button (not a
  bottom sheet — that hid the map and didn't let the user switch options without
  recalculating). Tapping an option calls `_selecionarAlternativa`, which reuses the
  origem/destino/preço captured at the moment the alternatives were fetched (not
  whatever's currently in the form fields, which the user may have started editing) and
  calls the shared `_finalizarViagem` — the same finishing step (set `_breakdown`, record
  the destination as recent, `saveLastTrip`) used by every other calculation path. The
  chosen option stays highlighted in the list (`RouteAlternativesPicker.selecionada`,
  compared by `identical`) and the list itself stays visible below the button, so the user
  can pick a different alternative afterward without recalculating — picking one feeds
  into the exact same downstream flow (map, navigate, save-to-history) as the single-route
  path.
  **Gotcha found live against the real API, not documented anywhere beforehand**: ORS
  rejects `alternative_routes` with HTTP 400 (`error.code: 2004`) once the approximate
  route distance exceeds 100km ("Request parameters exceed the server configuration
  limits... approximated route distance must not be greater than 100000.0 meters") — the
  alternatives algorithm doesn't scale to long intercity trips, which is most of what a
  cost calculator is for in a country the size of Brazil. `getRoutes()` combines two
  independent sources instead of relying on `alternative_routes` alone: (1) ORS's own
  `alternative_routes` when it doesn't 400 (short/medium trips, genuinely distinct
  geometry), and (2) a second directions request with `options.avoid_features:
  ["tollways"]` — an ordinary single-route request, not gated by the same distance limit,
  so it works at any distance. The avoid-tollways result is only added if its distance
  differs from what's already collected by more than 0.5km — a route that had no tolls to
  begin with comes back identical, and isn't added as a fake alternative. This also
  happens to be a more useful alternative for this app specifically than an arbitrary
  ORS-computed detour, since it's directly the tradeoff the app already prices: verified
  live for Copacabana→Guarapari (485km) — default route 72.60 BRL in tolls/R$393.80
  total vs. a 528km toll-free route at R$370.20 total (cheaper overall despite ~43km more
  driving, since the toll saved outweighs the extra fuel). If the avoid-tollways request
  itself fails (network/ORS hiccup), it's swallowed silently — whatever `alternative_routes`
  already produced (or the single fallback route on a >100km 400) still stands.

## Frontend conventions (`app/`)

- `lib/domain/models/` mirror backend DTOs; `lib/domain/navigation/`,
  `lib/domain/geo/`-equivalent logic is kept **pure** (no Flutter imports) specifically
  so it's testable with synthetic data without a device/emulator — follow this split for
  any new client-side calculation.
- `ApiClient`'s base URL is platform-aware (`lib/data/api_client.dart`): defaults to
  `localhost:8080` everywhere **except the Android emulator**, which needs the special
  alias `10.0.2.2` (its own loopback is not the host machine) — a real physical Android
  device on the same Wi-Fi would need the host's LAN IP instead, not currently handled.
- Theme toggle (`lib/theme/theme_controller.dart`) is a global `ValueNotifier<ThemeMode>`
  — starts following the OS, becomes manual on first tap of the sun/moon icon.
- Prefer `Wrap` over horizontal `ListView`/`SegmentedButton` scrolling for option rows
  on this app: `SegmentedButton` wrapped in `SingleChildScrollView` was found to break
  hit-testing on segments past the first visible one (confirmed on a real Android
  emulator — visually fine, taps silently no-op), and a horizontal scroll's *existence*
  isn't discoverable on desktop without a visible scrollbar. `ChoiceChip` + `Wrap` (or
  `ListView` only when vertical space is genuinely tight) avoids both problems.
- `DropdownButtonFormField` needs `isExpanded: true` or it sizes to the intrinsic width
  of the selected item's text, overflowing on narrow (phone) layouts even inside an
  `Expanded` parent.

## Testing conventions

- Backend: **no controller-level (MockMvc) tests anywhere in this project** — the
  established pattern is service-level unit tests (Mockito) for logic + a manual
  `curl` pass against a running `spring-boot:run` instance to verify the HTTP layer
  end-to-end. Keep following this rather than introducing a new test style.
- Pure domain logic (cost calculators, `Bearing`, `HaversineDistance`,
  `ManeuverTranslator`, and the Flutter-side `route_progress.dart`/
  `deviation_detector.dart`) gets thorough unit tests with synthetic fixtures — this is
  deliberately where the test investment concentrates, since it's cheap to test and
  carries the actual cost-calculation correctness risk.
- `SeedersIntegrationTest` (`@SpringBootTest`) is the guardrail for the vehicle catalog
  itself — total row count, per-type presence, and the real-vs-estimated field
  distinctions (`cilindradaCC`/`pbtKg` null-ness) are all asserted there. Update the
  expected count deliberately whenever the catalog JSON changes size.
- CI (`.github/workflows/ci.yml`) runs both suites on every push/PR to `master`. If you
  add a new committed shell script, check `git ls-files -s <path>` shows `100755` — this
  repo was created on Windows, and `backend/mvnw` shipped as `100644` (non-executable)
  for a long time without anyone noticing, because Git Bash on Windows doesn't enforce
  the bit; it silently broke the CI backend job with exit code 126 until fixed via
  `git update-index --chmod=+x`.

## Environment notes for this machine (may not generalize to a fresh clone)

- Flutter SDK, Android SDK (cmdline-tools + emulator + an AVD named `rotacusto_test`),
  and `nuget.exe` (required by `flutter_tts` on Windows) were all installed as
  **plain downloads to user-writable folders** (`C:\flutter`, `C:\Android`, `C:\nuget`)
  rather than through installers, because this sandbox blocks UAC elevation prompts.
  `JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT` and the relevant `bin`/`platform-tools`
  dirs are on the permanent user `PATH`/env vars.
- The Android emulator's WHPX acceleration **does** work in this sandbox (confirmed
  live) even though Docker Desktop's WSL2 backend does not — don't assume one implies
  the other.
- Screenshotting/inspecting a **native Windows** app (the built `.exe`) isn't possible
  with the tools available in this environment — verification of Windows-specific UI
  relies on the user's own eyes plus backend logs/network requests. The Android emulator
  *can* be screenshotted (`adb exec-out screencap`) and is the primary way live features
  (GPS navigation, voice, permissions) get verified end-to-end in this project.
- `url_launcher` was added then fully removed (Fase 3→post-launch) once the
  "report a missing vehicle" flow moved from opening a GitHub issue to an in-app
  dialog — most users aren't familiar with GitHub. Don't reintroduce it without a
  concrete new need.
- **`embedded-postgres` two gotchas hit during setup, both fixed and worth remembering**:
  (1) Spring Boot 3.3.5 manages `commons-lang3` at a version older than what
  `embedded-postgres`'s `commons-compress` needs, breaking binary extraction with
  `NoSuchMethodError: SystemProperties.getUserName` — fixed by explicitly overriding
  `commons-lang3` to 3.17.0 in `pom.xml`. (2) The builder's default behavior treats the
  data directory as disposable and re-runs `initdb` on every start, which fails (or
  would silently wipe data) on an already-initialized directory — fixed with
  `.setCleanDataDirectory(false)`. (3) Separately, a JPA `@Lob` on a `String` field maps
  to Postgres's legacy OID large-object type, which requires an explicit transaction and
  breaks reads with "Objetos Grandes não podem ser usados no modo de efetivação
  automática" — use `@Column(columnDefinition = "TEXT")` instead for any future
  arbitrary-length text column (see `TripHistoryEntry.breakdownJson`).

## Known gaps (not started, or deliberately out of scope)

Recálculo de rota ✅, navegação em segundo plano ✅ (Android, foreground service),
roteiro com múltiplas paradas ✅, banco persistente (Postgres) ✅, contas de usuário +
histórico de viagens ✅, modo offline ✅ (resistência durante uma viagem já calculada —
calcular uma viagem nova sempre exige conexão, isso não muda) — suporte iOS 🟡 (compila
via CI num Mac na nuvem, ver "iOS support" acima; falta o usuário assinar a Apple
Developer Program pra destravar assinatura/device/TestFlight/loja) e distribuição em
loja (Play Store/Microsoft Store) ❌ (precisa de conta de desenvolvedor/assinatura —
Play Store é o caminho mais rápido, só US$25 e Android já funciona 100%; ainda não
pedida). Preço de combustível/energia em tempo real é permanentemente fora de escopo —
não existe fonte gratuita de preço por posto no Brasil.
