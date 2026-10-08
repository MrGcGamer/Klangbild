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
render/         HaloLayout (grid), HaloLight (wall glow + sundial sun), SceneBuilder (per-track
                precompute: Lanczos cover at exact px, backdrop, palette), ImageOps
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
  Progress is the wandering "sun" behind the cover plus a time line in words.
- **One style: Halo.** Cover backlit by its own leading colour on a near-black wall; glow
  never reaches the screen edge; wall stays dark behind the cover.
- **Grid** (960 × 540 dp): cover slot 320 dp at 108 dp margin; text column 360 dp; clock cap
  on the cover top, title grows upward from 44 dp above the artist baseline (350 dp), time
  line on the cover bottom. Layout mirrors per album (burn-in), whole stage drifts ±12 dp.
- **Honest resolution:** ≤ 1.1 source px per 1080p px (`HaloLayout.MAX_SOURCE_SCALE_1080P`).
- **Per frame only cheap work:** blur/palette/resampling happen once per track in
  `SceneBuilder`; animations only change alpha/transform/colour filters, read in draw lambdas.
- **Typography:** Newsreader (titles, italics), Jost (spaced caps, figures). No caps, tracking
  or italics for CJK/Arabic (`isSpacingFriendly`). Emoji smaller and glued to words.
  No mid-word line breaks (`breaksInsideWord`).
- **Calm motion:** track change 3.6 s (filament: old light cools to amber), pause dims the
  lamp over 2.4 s, screens switch by fading through dark.
- **German UI texts**, 24 h clock, numbers up to twelve in words in the time line.
- Never claim device behaviour that was not verified on the device (frame rate, CEC wake,
  dream on Google TV) – mark it as unverified.
