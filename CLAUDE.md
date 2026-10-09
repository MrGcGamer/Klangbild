# Klangbild – Notes for Claude

Android TV "now playing" display for Home Assistant + Music Assistant. Runs on a Google TV
Streamer (Android TV 14, 4K, weak PowerVR GPU, OLED TV). Sideload only.
Docs for humans are German (`README.md`, `docs/`); code and comments are English.

## Build & test

```bash
./gradlew :app:testDebugUnitTest                               # all JVM tests (unit + Robolectric screenshots)
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true  # same, writes PNGs to app/build/screenshots/
./gradlew :app:testDebugUnitTest --tests '*HaloStageTest*' -Proborazzi.test.record=true   # one class
./gradlew :app:assembleRelease                                 # signed, R8-minified APK
python3 tools/make_launcher_assets.py                          # re-render banner + icons (Pillow)
```

- Local SDK: `local.properties` → `sdk.dir=/opt/android-sdk` (not committed). compileSdk 37.
- Maven Central rate-limits shared IPs (HTTP 429): Gradle and Robolectric use Google's
  mirror first (`settings.gradle.kts`, `robolectric.dependency.repo.url` in `app/build.gradle.kts`).
- Screenshots are **not** committed as baselines; tests render to `app/build/screenshots/`
  (CI uploads them as an artifact). Curated renderings for the README live in `docs/screens/`.
- Tests with infinite animations (buffering) must use `createComposeRule()` with
  `mainClock.autoAdvance = false` – `captureRoboImage { }` waits for idle forever.
- Look at renders with the Read tool; build contact sheets with Pillow for motion.

## Architecture (data source → state model → UI)

```
data/ha/        HaConnection (WebSocket: auth, subscribe_entities, call_service, ping, backoff)
                EntityStore (compressed diff applier), PlayerParser (entity + MA get_queue),
                HaUrls, LocalNetwork (cleartext only in LAN), ArtworkUrls (CDN hi-res rewrites)
                HaSource (NowPlayingSource: player + sun.sun + MA queue enrichment)
data/demo/      DemoSource (invented catalogue, synthetic covers via demo://cover/NAME)
data/           NowPlayingSource/SourceState/LinkState, ArtworkLoader, Backoff
domain/         TrackInfo, PlayerSnapshot, TrackText (typographic cleanup), Progress +
                SmoothedPosition, TimeLine (time in words), ScreenStateMachine (Screen + keep-on/sleep)
color/          Oklab, CoverPalette (k-means in Oklab → ScenePalette), Lighting (time of day)
render/         HaloLayout (grid), HaloLight (light map, light colour/tint, sundial mask),
                SceneBuilder (per-track precompute: Lanczos cover at exact px, light map, palette),
                ImageOps
art/            SyntheticCovers (test/demo covers), ChladniArt (missing-cover art), Noise
app/            NowPlayingController (single source of truth → StateFlow<UiState>), KlangbildApp/AppGraph
setup/          MiniHttp, SetupHandler (one-time code, API), SetupPage (HTML), SetupServer (+ QR, discovery)
ui/             KlangbildRoot (screen switch, entrance fade, OLED drift), stage/ (HaloStage layers,
                StageTheatre transitions), RestScreen, SetupScreen, Motion, DebugOverlay/FrameStats
dream/          KlangbildDream (DreamService with ComposeView)
config/         Settings + SettingsStore (DataStore), LaunchExtras (am start extras)
```

## Design principles (do not regress)

- **Object, not app.** No buttons, no bars, no icons for their own sake, no harsh white.
  Progress is the sundial (the lamp's pool wanders around the cover) plus a time line in
  words.
- **One style: Halo.** A lamp hidden behind the print, 40 dp (0.125 × print size) inside its
  edge on the sundial's side, throws a round pool whose *lightness* falls as (1 − (r/R)²)^1.5
  (R = 110 dp; 1.25× along a side, not at corners) in the cover's leading colour (hue groups,
  area over brightness; warm hues → ~3300 K tungsten, neutral and yellow-green (95–150°) →
  warm white, cool hues keep their own hue at chroma 0.03 by day and become lamp light in
  the evening – never grey or olive on the way). Brightest at the print's edge, about half
  the luminance at 25 dp, gone at 70 dp – it fits the 72 dp of free wall; near an outer edge
  the lamp moves deeper behind the print rather than being clipped. The 36 dp next to the
  screen edges stay black in stage coordinates (`EdgeBandTest` checks rendered pixels; the
  OLED drift moves the stage up to 12 dp). The far side gets nothing – a dark print gets an
  8 dp wash with square corners in the lamp's hue (`rimMap`, 0.8 %, fading to 0.35 % away
  from the lamp). Night 0.28× day, saturation ≤ 50 %; dimmed light loses chroma; a cover at
  night gains at most 0.004 chroma; white prints top out near L* 88; evening text takes the
  lamp's hue. `render/DesignRulesTest` guards these numbers.
- **Light per frame:** lamp sprite (moved, rotated, stretched by its local matrix) × static
  wall mask × tint colour filter – one rectangle (two while a print is taken down), plus the
  rim drawn with `Lighten` (never `Plus`: sums of sRGB values overshoot). Shaders are made
  once per scene. Never add per-frame image processing.
- **Grid** (960 × 540 dp): cover slot 320 dp at 108 dp margin; text column 360 dp; clock cap
  on the cover top, title grows upward from 44 dp above the artist baseline (350 dp), time
  line on the cover bottom. Layout mirrors per album (burn-in), whole stage drifts ±12 dp.
- **Honest resolution:** ≤ 1.1 source px per 1080p px (`HaloLayout.MAX_SOURCE_SCALE_1080P`);
  small covers sit optically centred in a charcoal mat with grain and a 1 dp white bevel (a
  light mat would be the largest static bright area on the OLED).
- **Per frame only cheap work:** blur/palette/resampling happen once per track in
  `SceneBuilder`; animations only change alpha/transform/colour filters, read in draw lambdas.
- **Typography:** Newsreader (titles, italics), Jost (spaced caps, figures). No caps, tracking
  or italics for CJK/Arabic (`isSpacingFriendly`); there the artist outranks the album.
  Titles ≤ 3 lines, prefer breaks after section dashes; artists on one line ("u. a." in
  italics); no mid-word breaks (`breaksInsideWord`); edge emoji dropped (`Emoji.trimEdges`),
  inner ones dimmed; one separator `TimeLine.SEPARATOR` (its dot lifted next to RTL text; an
  all-RTL up-next line is one right-to-left run); `TrackText.typeset` for dashes and narrow
  no-break spaces after abbreviations; in UI prose a short word opening a sentence never ends
  a line, and articles and adjectives stay with their noun.
- **Calm motion:** track change 2.7 s in sequence (old print out 700 ms while the lamp goes
  down to 22 % → empty wall 500 ms, a dim round pool ≈ 1.6 %, colour morphs in OKLCH → new
  print in 900 ms with the light → text). A fading print hides the lamp while its opacity is
  above 12 % (never tracing paper). Unchanged lines stay, each compared on
  its own (same text, place and colour); a clock that has to move holds at 60 % through the
  dark moment and cross-dissolves to its new place (never below 60 %). Same picture (album)
  → only text; same track in new light → old text fades on top. Pause dims lamp, cover and text over 2.4 s; time line cross-fades 600 ms. Screens
  switch by fading through dark.
- **Generated art:** Chladni plates are ring modes – two or three nodal circles inside 0.64 of
  the radius – made wavy by a second mode (3–6 lobes, rounded: amplitude ≤ 1.5 r/(n² − 1),
  share capped so circles never touch), mirror-symmetric, no sand beyond 0.74–0.84 of the
  radius. No nodal diameters (crosses, wheels, rosettes), no single circle or two lobes ("O").
- **Rest screen:** serif clock ("H:mm") under a dim lamp that moves with it; re-hung every
  15 min; night: black wall, only the time.
- **German UI texts**, 24 h clock, numbers up to twelve in words in the time line.
- Never claim device behaviour that was not verified on the device (frame rate, CEC wake,
  dream on Google TV) – mark it as unverified.
