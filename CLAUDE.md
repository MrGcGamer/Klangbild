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
  Progress is the sundial (the brighter side of the halo wanders around the cover) plus a
  time line in words.
- **One style: Halo.** A lamp behind the print, offset ~24 dp towards the sundial's sun,
  lights the wall in the cover's leading colour (hue groups, area over brightness; warm hues
  → tungsten, neutral → warm white). 50 % at the print's edge, peak 8–14 dp out,
  exponential fall-off; cos³ sundial lobe. No light within 36 dp of the screen edges, far
  wall pure black. Night 0.28× day, saturation ≤ 50 %; dimmed light loses chroma.
  `render/DesignRulesTest` guards these numbers.
- **Light per frame:** pool sprite (translated) × rotating sweep mask × static wall mask ×
  tint colour filter – one rectangle (two while a print fades). Never add per-frame image
  processing.
- **Grid** (960 × 540 dp): cover slot 320 dp at 108 dp margin; text column 360 dp; clock cap
  on the cover top, title grows upward from 44 dp above the artist baseline (350 dp), time
  line on the cover bottom. Layout mirrors per album (burn-in), whole stage drifts ±12 dp.
- **Honest resolution:** ≤ 1.1 source px per 1080p px (`HaloLayout.MAX_SOURCE_SCALE_1080P`).
- **Per frame only cheap work:** blur/palette/resampling happen once per track in
  `SceneBuilder`; animations only change alpha/transform/colour filters, read in draw lambdas.
- **Typography:** Newsreader (titles, italics), Jost (spaced caps, figures). No caps, tracking
  or italics for CJK/Arabic (`isSpacingFriendly`); there the artist outranks the album.
  Titles ≤ 3 lines, prefer breaks after section dashes; artists on one line ("u. a." in
  italics); no mid-word breaks (`breaksInsideWord`); edge emoji dropped (`Emoji.trimEdges`),
  inner ones dimmed; one separator `TimeLine.SEPARATOR`; `TrackText.typeset` for dashes and
  narrow no-break spaces after abbreviations.
- **Calm motion:** track change 2.7 s in sequence (old print out 700 ms, its shadow fading with
  it → empty lit wall 500 ms while the lamp dips and morphs colour in OKLCH → new print in
  900 ms → text); clock and unchanged lines stay. Same picture (album) → only text. Pause
  dims lamp, cover and text over 2.4 s; time line cross-fades 600 ms. Screens switch by
  fading through dark.
- **Generated art:** Chladni plates must stay mirror-symmetric (no chiral pinwheels).
- **Rest screen:** serif clock ("H:mm") under a dim lamp that moves with it; re-hung every
  15 min; night: black wall, only the time.
- **German UI texts**, 24 h clock, numbers up to twelve in words in the time line.
- Never claim device behaviour that was not verified on the device (frame rate, CEC wake,
  dream on Google TV) – mark it as unverified.
