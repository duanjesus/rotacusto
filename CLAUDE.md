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
ORS_API_KEY=<your-openrouteservice-key> ./mvnw spring-boot:run   # free key: openrouteservice.org

# App
cd app
flutter run -d windows   # or -d chrome/edge; Android needs an emulator/device + `adb reverse` or 10.0.2.2 (handled automatically, see api_client.dart)
```

H2 is **in-memory only** (`jdbc:h2:mem`, `application.yml`) — the vehicle/toll catalogs
re-seed from the JSON files in `backend/src/main/resources/data/` on every restart,
which is what makes it safe to keep editing those files. User-submitted "missing
vehicle" reports do **not** go through H2 for this exact reason (see below) — they'd
vanish on every restart.

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
- Out of scope, deliberately: background operation (needs the app open, screen on — no
  foreground service yet), background rerouting.

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

## Known gaps (not started, or deliberately out of scope)

Recálculo de rota ✅, navegação em segundo plano ❌ (precisa de foreground service
Android), banco persistente (H2→Postgres) ❌, contas de usuário/histórico de viagens ❌
(depende do item anterior), suporte iOS ❌ (precisa de Mac, fora do alcance deste
ambiente), distribuição em loja (Play Store/Microsoft Store) ❌ (precisa de conta de
desenvolvedor/assinatura, fora do alcance), roteiro com múltiplas paradas ❌, modo
offline ❌. Preço de combustível/energia em tempo real é permanentemente fora de
escopo — não existe fonte gratuita de preço por posto no Brasil.
