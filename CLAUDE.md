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

`backend/src/main/resources/data/tollplazas-br101.json` is a small curated seed
(coordinates + `tarifaPorEixo`, `tarifaMoto`) combined at runtime with **live OSM
Overpass** queries (`TollService`, national coverage, but no real tariff — falls back to
a configured default per axle). Two entries are **directional** (`cobraApenasIndo` +
`refLat`/`refLng`, compared against the route's bearing via `domain/geo/Bearing.java`)
— many Brazilian toll plazas only physically exist on one carriageway of a divided
highway. Toll cost is always `tarifaPorEixo × numeroEixos`, except motorcycles which use
a separate `tarifaMoto` field when present.

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
