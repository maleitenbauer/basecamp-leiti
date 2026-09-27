# CS2 2D demo analysis

Gaming → Counter-Strike 2 → Analysis. Upload a `.dem` from a match, get kill/death positions (v1: a plain
coordinate-grid scatter) plus data stored for an animated 2D replay later.

## Why this architecture

Real .dem parsing has no mature JVM-native library — the ecosystem is Go, Rust and Python. Of the two CS2-capable,
MIT-licensed options researched (`demoinfocs-golang` and `demoparser2`), this uses
[demoinfocs-golang](https://github.com/markus-wa/demoinfocs-golang) (Go), compiled to a small static binary the
Kotlin API shells out to via `ProcessBuilder`. That keeps the *running* container free of any extra language
runtime — only a compiled executable — matching how `api/` (Gradle → JRE) and `web/` (pnpm → nginx) already work:
build with the full toolchain, ship only the output.

`analysis-parser/` is therefore a **third top-level toolchain**, alongside `api/` and `web/`. `api/Dockerfile`'s
build context is the **repo root** (not `api/`), specifically so it can also `COPY analysis-parser/`; see the
comment at the top of that Dockerfile.

## What v1 does and doesn't do

Implemented: upload, background parsing, per-round kills (attacker/victim position, weapon, headshot), round
results, grenade throws/detonations (with full flight path already stored), and periodic player position
snapshots (~1/second) for every player — all scoped per user, per upload. The kill scatter view supports
filtering to a single player (kills by them / deaths of them), defaulting to the viewer's own profile when their
Steam64 ID (set on the Matches page) appears in the demo.

**Real map overlay.** `web/src/modules/gaming/cs2/analysis/radarCalibration.ts` has each map's `pos_x`/`pos_y`/`scale`
calibration constants — Valve's own published numbers (ported from demoinfocs-golang's bundled copies, MIT
licensed repo), which convert world coordinates to radar-image pixels correctly. Currently covers the active
competitive pool: dust2, mirage, inferno, nuke, overpass, vertigo, ancient, anubis, train. For any other map,
the view falls back to a plain grid scaled to whatever's currently visible, clearly labeled as such — never a
silently-wrong overlay.

The radar **images** themselves are still not bundled (Valve's art, not ours to redistribute — same reasoning as
the Matches map banners). Put your own at `web/static/maps/radar/<slug>.png` (slug = map name without the
`de_`/`cs_` prefix, e.g. `mirage.png`) and it's picked up automatically; without an image, calibrated demos still
plot on the correct real-world-relative grid, just with no background art.

Deliberately not implemented, listed here so nobody assumes it silently works:

- **No animated replay UI.** The position-snapshot data needed for it is already being collected and stored
  (`gaming.cs2_demo_position`), so this is a frontend-only follow-up, not a data-model change.
- **No damage events, bomb plant/defuse, or full per-tick resolution** (positions are sampled, not every tick).

## Local development

The Go binary only exists once built by Docker — it is **not** part of the normal Windows dev workflow
(`scripts\dev.ps1`). `GET /api/gaming/cs2/analysis/available` reports `false` locally; uploads are still accepted
and correctly end up `FAILED` with a clear message, rather than the app breaking.

To test the parser itself locally, install Go and run it directly against a real demo:

```powershell
cd analysis-parser
go run . -demo C:\path\to\match.dem > out.json
```

To test the *full* upload → parse → store pipeline locally, build the binary and point the API at it:

```powershell
cd analysis-parser
go build -o analysis-parser.exe .
$env:BASECAMP_ANALYSIS_PARSER_BIN = "$PWD\analysis-parser.exe"
```
then restart the API with `scripts\dev.ps1 api restart` in the same shell (or set it as a permanent user
environment variable first).

## Dependency: demoinfocs-golang v5, not v6

Uses `github.com/markus-wa/demoinfocs-golang/v5` (currently `v5.2.0`), verified as the correct module straight
from the library's own README ("Counter-Strike 2 + Live Broadcast Parsing" → `go get -u .../v5/pkg/demoinfocs").
An earlier draft of this file depended on `v6.0.0`, which turned out not to exist — `v6` only has a single
`v6.0.0-alpha.0` pre-release tag on the module proxy, so `go build`/`go mod tidy` failed with "unknown revision"
until this was caught while testing the local (non-Docker) dev flow below. Every API call this parser uses —
`Kill`, `RoundEnd`, `GrenadeProjectileDestroy`, the `ServerInfo` net message, `events.FrameDone`,
`GameState().Participants().Playing()`, `(*common.Player).Health()`/`.IsAlive()` — has been checked against
v5.2.0's real example source (`examples/print-events`, `examples/heatmap`, `examples/nade-trajectories`) and
`pkg/demoinfocs` source directly, not just paraphrased docs. CI runs `go build`/`go vet`/`go test` on every PR
(see `.github/workflows/ci-deploy.yml`) so a dependency or API break is caught before merge.

Worth knowing for later: v5's own `examples/heatmap` and `examples/nade-trajectories` use a bundled `ex.Map`
helper (`examples/map_metadata.go`) with real per-map `TranslateScale` calibration and radar images
(`examples/_assets/radar/`) — that's exactly the "pos_x/pos_y/scale" calibration data called out as missing
above for a real map overlay. Worth a look if/when that's implemented, license permitting.

## Storage

Raw `.dem` files live on the `demodata` Docker volume (`/data/demos` in the container), **not** in Postgres —
they're deleted automatically once parsing succeeds; only the extracted data (rounds/kills/grenades/positions)
is kept in the database. A file is only kept on disk if parsing *failed*, so it can be retried later without
re-uploading. Back up `demodata` the same way as `pgdata` if you want failed-but-retryable uploads to survive a
server rebuild — losing it just means re-uploading, no data in Postgres is affected either way.
