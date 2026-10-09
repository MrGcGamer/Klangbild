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
python3 tools/make_readme_images.py                            # docs/screens/ from the Readme* tests
```

- Local SDK: `local.properties` → `sdk.dir=/opt/android-sdk` (not committed). compileSdk 37.
- Maven Central rate-limits shared IPs (HTTP 429): Gradle and Robolectric use Google's
  mirror first (`settings.gradle.kts`, `robolectric.dependency.repo.url` in `app/build.gradle.kts`).
- Screenshots are **not** committed as baselines; tests render to `app/build/screenshots/`
  (CI uploads them as an artifact; the plugin copies them there at the end of the task).
  Curated renderings for the README live in `docs/screens/`.
- The progress bar refreshes in a loop of its own, so a stage never goes idle: stills wrap
  their content in `Fixtures.Still { }` (provides `LocalStill`), animated sequences use
  `createComposeRule()` with `mainClock.autoAdvance = false`.
- Look at renders with the Read tool; build contact sheets with Pillow for motion.
- The emulator smoke test (CI) talks to a stand-in Home Assistant: `ci/fake_ha.py`.

## Architecture (data source → state model → UI)

```
data/ha/        HaConnection (WebSocket: auth, subscribe_entities + a second swappable one for
                group members via watch(), render_template via watchTemplate(), call_service,
                ping, backoff), EntityStore (compressed diff applier), PlayerParser (entity +
                MA get_queue), HaUrls, LocalNetwork (cleartext only in LAN), ArtworkUrls (CDN
                hi-res rewrites), HaSource (NowPlayingSource: player + sun.sun + MA queue +
                speaker count for "Überall")
data/           NowPlayingSource/SourceState/LinkState, ArtworkLoader, Backoff
domain/         TrackInfo, PlayerSnapshot (+ SpeakerGroup, volume), SpeakerLabel ("Wohnzimmer + 5",
                "Überall", room names, "35 %"), TrackText (typographic cleanup), Progress +
                SmoothedPosition, TimeLine (clock format, separator), ScreenStateMachine
color/          Oklab, CoverPalette (k-means in Oklab → ScenePalette, text inks), Lighting (time of day)
render/         HaloLayout (the grid), WallGlow (the artwork's light on the wall), HaloLight
                (PrintShape; lamp tint for rest and menu), SceneBuilder (per-track precompute:
                Lanczos cover at exact px, glow map, palette), ImageOps
art/            ChladniArt (missing-cover art), Noise
app/            NowPlayingController (single source of truth → StateFlow<UiState>), KlangbildApp/AppGraph
setup/          MiniHttp, SetupHandler (one-time code, API), SetupPage (HTML), SetupServer (+ QR, discovery)
ui/             KlangbildRoot (screen switch, OLED drift, menu overlay, peek), stage/ (Stage layers,
                StageTheatre transitions, ProgressBar, StageModel + StageTexts), theme/Type
                (Mona Sans, `mona()`), MenuScreen, RestScreen, SetupScreen, Motion, DebugOverlay
dream/          KlangbildDream (DreamService with ComposeView)
config/         Settings (+ DisplayOptions, VolumeDisplay, LightLevel) + SettingsStore (DataStore),
                SettingsMenu (entries, values, German explanations, steps), LaunchExtras (am start extras)
test only       SyntheticCovers + Catalogue (generated test covers and tracks, test://cover/NAME)
```

## Design principles (do not regress)

- **Object, not app – but a well-made one.** No buttons, no icons, no chips, no harsh white.
  The artwork is the heaviest element and always the tallest: the text block beside it takes
  at most 62 % of its height (`BLOCK_SHARE`, measured from the first cap height to the last
  baseline, never from text boxes).
- **Grid** (960 × 540 dp, `HaloLayout`): artwork 368 dp at 96 dp from the left, group lifted
  8 dp above the centre (optical centre); 64 dp gap; text column 352 dp; 80 dp right margin.
  Text block centred on the artwork; the status line ("21:47 · Wohnzimmer + 5") hangs with its
  cap height from the artwork's top edge, "Als Nächstes" stands with its baseline on the
  bottom edge; every line starts on the column's left edge. No mirroring; the whole stage
  drifts ±12 dp against burn-in. Square artwork has 8 dp corners.
- **One typeface: Mona Sans** (GitHub release with the optical-size axis, OFL, shipped
  unmodified), via `mona(size, weight, …)` – optical size = size in sp. Sentence case only:
  no capitals, no letter-spaced labels, no italics, no second family (those read as "AI
  template"). Title 44/40 sp (2 lines, weight 440), then 36–26 sp (3 lines, 450–460), then
  narrower (wdth 90, 82); artist 21 sp/520; album, work 17 sp; labels, times, status, next
  15 sp – nothing smaller. No tabular figures (Mona's slash the zero). Mona lacks U+202F,
  U+2009 and U+2002 – use U+00A0. Titles ≤ 3 lines, prefer breaks after section dashes, no
  mid-word breaks (`breaksInsideWord`); artists on one line (smaller, then narrower, then
  "u. a."); the year leaves the album line before anything is cut; edge emoji dropped
  (`Emoji.trimEdges`), inner ones dimmed; one separator `TimeLine.SEPARATOR`;
  `TrackText.typeset` for dashes and no-break spaces after abbreviations; CJK/Arabic fall back
  to system fonts (RTL lines isolated); in UI prose a short word opening a sentence never ends
  a line, and articles and adjectives stay with their noun.
- **Text colour:** near-neutral with a breath of the cover's hue (chroma ≤ 0.009, a little
  warmer in the evening), tiers like white at 100 / 80 / 63 / 59 / 55 % – never #FFF, never
  ivory. Even the quietest line keeps 4.5 : 1 on the brightest glow (`DesignRulesTest`).
- **Light (`WallGlow`):** the artwork glows onto the black wall in the colours of its own
  edges; each wall point takes the nearest edge colour averaged over a stretch that grows
  with distance (diffuse, no streaks). 0.045 relative luminance at the edge, falling as
  exp(−(d/σ)^1.7), σ = 0.19 × artwork size, so the text stands on dark wall; dark edges glow
  at 32 %, chroma ≤ 0.13, night dimmer and paler; the 36 dp next to the screen edges stay
  black (`EdgeBandTest` checks rendered pixels). One bitmap per scene, drawn as one rectangle,
  dimmed through a colour matrix.
- **Progress:** a classic bar – 4 dp, round ends, no knob, track 20 %, fill 90 % of the ink,
  elapsed left, "−remaining" right. Refreshed ~30×/s aligned to frames (only its own draw);
  jumps > 1.5 s glide in 350 ms; paused, the fill dims. Hidden by setting, back on a key press.
- **Per frame only cheap work:** blur/palette/resampling/glow happen once per track in
  `SceneBuilder`; animations only change alpha/transform/colour filters, read in draw lambdas.
- **Honest resolution:** ≤ 1.3 source px per 1080p px (`HaloLayout.MAX_SOURCE_SCALE_1080P`);
  small covers sit optically centred in a charcoal mat with grain and a 1 dp white bevel.
- **Calm motion** (`Motion`): track change 1.1 s – the new artwork fades over the old in
  600 ms and settles from 2 % larger, its light morphs in 900 ms, old text leaves in 250 ms,
  new lines rise one after another from 300 ms; same picture (album) → only text; same track
  in new light → quiet cross-fade. Pause (600 ms): the artwork recedes to 96.5 % with its
  light, light 45 %, artwork 72 %, text 70 %; mute and a lost link only dim. Opaque pictures
  never cross-fade against the wall; nothing slides, nothing bounces. Screens switch by
  fading through dark.
- **States say it in words**, in the status line: "Pausiert", "Stumm", "Lautstärke 35 %"
  (3.5 s), "Überall"; "Verbindung wird wiederhergestellt …" in the progress bar's place.
- **Settings menu** (OK long-press / Menu): the stage's grid, heading and the chosen line's
  explanation on the left, lines on the right; the chosen line sits at the screen's middle in
  a static pool of light, the list glides through it and fades with distance. Values are
  words, changes apply at once (left/right clamp, OK wraps), closes itself after 60 s.
  Options show or hide parts and dim the light – no fonts, colours or layout variants.
- **Generated art:** Chladni plates are ring modes – two or three nodal circles inside 0.64 of
  the radius – made wavy by a second mode (3–6 lobes, rounded: amplitude ≤ 1.5 r/(n² − 1),
  share capped so circles never touch), mirror-symmetric, no sand beyond 0.74–0.84 of the
  radius. No nodal diameters (crosses, wheels, rosettes), no single circle or two lobes ("O").
- **Rest screen:** large light clock ("H:mm", 132 sp) under a dim pool of light that moves
  with it; re-hung every 15 min; night: black wall, only the time.
- **German UI texts**, 24 h clock.
- Never claim device behaviour that was not verified on the device (frame rate, CEC wake,
  dream on Google TV) – mark it as unverified.
