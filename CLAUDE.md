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
  `createComposeRule()` with `mainClock.autoAdvance = false`. The static stages for stills
  (`Stage`, `FlowStage`) live in the test sources; the app only has its theatres.
- Motion is judged as contact sheets: `FlowMotionTest`/`TransitionTest` render frames to
  `app/build/screenshots/…`; tile them with Pillow and look at them.
- Look at renders with the Read tool; build contact sheets with Pillow for motion.
- The emulator smoke test (CI) talks to a stand-in Home Assistant: `ci/fake_ha.py`.

## Architecture (data source → state model → UI)

```
data/ha/        HaConnection (WebSocket: auth, subscribe_entities + a second swappable one for
                group members via watch(), render_template via watchTemplate(), call_service,
                ping, backoff), EntityStore (compressed diff applier), PlayerParser (entity +
                MA get_queue), HaUrls, LocalNetwork (cleartext only in LAN, every redirect hop),
                ArtworkUrls (CDN hi-res rewrites), HaSource (NowPlayingSource: player + sun.sun +
                MA queue + speaker count for "Überall" + the optional MA queue window)
data/ma/        MaClient (Music Assistant's own API, optional: POST /api with a long-lived token),
                MaQueue (queue items around the current one → QueueWindow, pictures via imageproxy)
data/           NowPlayingSource/SourceState/LinkState, ArtworkLoader (size limits), Backoff
domain/         TrackInfo (+ pictureKey), PlayerSnapshot (+ SpeakerGroup, QueueInfo), SpeakerLabel
                ("Wohnzimmer + 5", "Überall", room names, "35 %"), TrackText (typographic cleanup),
                Progress + SmoothedPosition, TimeLine (clock format, separator), ScreenStateMachine,
                StageView (SINGLE / FLOW), CoverStrip (the cover flow's cards: one per picture, ids kept)
color/          Oklab, CoverPalette (k-means in Oklab → ScenePalette, inks, both walls), Lighting
                (time of day), Appearance (LIGHT / DARK, by the sun)
render/         HaloLayout (the grid, per view), WallGlow (the artwork's light on the dark wall),
                WallShade (its shadow on paper), HaloLight (PrintShape; lamp tint for rest and
                menu), SceneBuilder (per-track precompute: Lanczos cover at exact px, wall, palette,
                flow card; relight), FlowGeometry (the shared perspective), FlowCards (baked card
                textures), FlowLight (the pool of light above the cards), ImageOps
art/            ChladniArt (missing-cover art), StableHash
app/            NowPlayingController (single source of truth → StateFlow<UiState>), Scenes (scene
                cache + builds off the main thread), KlangbildApp/AppGraph
setup/          MiniHttp, SetupHandler (one-time code, API, token bound to its address), SetupPage
                (HTML), SetupServer (+ QR, discovery)
ui/             KlangbildRoot (screen switch, OLED drift, menu overlay, peek), stage/ (Stage layers,
                Typesetting, StageTheatre transitions, ProgressBar, StageModel + StageTexts),
                flow/ (FlowStrip: cards as quads; FlowTheatre: keyed strip transitions),
                theme/Type (Jost + DM Sans, `titleType()`/`textType()`, Figures), MenuScreen,
                RestScreen, SetupScreen, Motion, Changes (`playChange`, `RisingText`: one change
                at a time, hurrying, held lines), DebugOverlay
dream/          KlangbildDream (DreamService with ComposeView)
config/         Settings (+ DisplayOptions, VolumeDisplay, LightLevel, AppearanceMode) + SettingsStore
                (DataStore), SettingsMenu (entries, values, German explanations, steps), LaunchExtras
                (am start extras)
test only       SyntheticCovers + Catalogue (generated test covers and tracks, test://cover/NAME),
                Fixtures, the static stages for stills
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
- **Two faces, one voice:** Jost (Owen Earl, OFL) for titles ≥ 24 sp, the large clock,
  headings and all figures (`titleType()`); DM Sans (Colophon, OFL, optical size = size in
  sp) for everything ≤ 21 sp (`textType()`). Both shipped unmodified, both cap height 0.70.
  Sentence case only: no capitals, no letter-spaced labels, no italics, no third family
  (those read as "AI template"); the platform's Google Sans is out (it reads as Google TV's
  own UI). Title 44/40 sp (2 lines, weight 400, −1 %), then 34/30 sp (3 lines, 420, −0.5 %),
  26 sp (3 lines, 430), 24 sp (4 lines); Japanese/Chinese/Korean titles at 0.88×. A version
  ("Radio Edit") is a quiet subtitle line (DM Sans). Artist 21 sp/500; album, work 17 sp;
  labels, times, status, next 15 sp/440 – nothing smaller. Large lines are pulled left by
  their extra side bearing (`opticalIndent`). Times are set in Jost with `Figures`: digits in
  equal cells (DM Sans has no tabular figures and a narrow 1), colon raised 0.123 em; the
  rest clock keeps natural figures and dissolves into each new minute. Neither face has
  U+202F, U+2009 or U+2002 – use U+00A0.
  Titles ≤ 3 lines, prefer breaks after section dashes, no mid-word breaks
  (`breaksInsideWord`), a word of one to three letters stays with the next ("Spiegel / im
  Spiegel"); artists on one line (smaller, then "u. a."); the year leaves the album line
  before anything is cut; "Als Nächstes" drops the artist before it cuts; edge emoji dropped
  (`Emoji.trimEdges`), inner ones dimmed; one separator `TimeLine.SEPARATOR`;
  `TrackText.typeset` for dashes and no-break spaces after abbreviations; RTL lines isolated;
  in UI prose a short word opening a sentence never ends a line, and articles and
  adjectives stay with their noun.
- **Text colour:** three levels only – title; artist, clock and the bar's fill; everything
  else (like white at 100 / 80 / 62 %; on paper Oklab L 0.24 / 0.37 / 0.49, the quietest
  still 4.5:1) – near-neutral with a breath of the cover's hue (chroma ≤ 0.009, a little
  warmer in the evening, never above 0.018), never #FFF, never ivory.
- **Light and dark wall (`Appearance`):** automatic by the sun – light from sunrise to sunset
  (elevation −0.833°, 0.5° hysteresis; the local clock without `sun.sun`) – or fixed. The
  light wall is warm paper (Oklab L 0.915) with a breath of the cover's colour pulled towards
  warm and kept within hues 50–90° (a cool cover gives less colour, never mint), never white; there the artwork casts a soft shadow (`WallShade`) instead of light, a
  white cover gets a dark hairline, small covers an ivory mat. A wall change cross-fades the
  stage in 2.4 s.
- **Light (`WallGlow`):** a tight halo in the colours of the artwork's own edges, the same on
  every side: each wall point takes the nearest edge colour, averaged over a stretch that
  grows with distance (diffuse, no streaks); at most L* ≈ 17 at the edge and never more than
  0.4 × the edge's own luminance (a dark cover gives little light, a matted one only its
  mat's), falling as a Gaussian with σ = 26 dp, gone before the text column (which stands on
  black). It fills 2 dp under the artwork's edge so the anti-aliased rim never shows black.
  The 36 dp next to the screen edges stay black (`EdgeBandTest` checks rendered pixels),
  faded in as a product of a horizontal and a vertical ramp (a min() of edge distances
  creases along the diagonals). One bitmap per scene, drawn as one rectangle, dimmed through a
  colour matrix.
- **Progress:** a classic bar – 4 dp, round ends, no knob, track 16 %, fill at the artist's
  level, elapsed left, the track's length right (only one number ticks). Refreshed ~30×/s
  aligned to frames, on its own layer; jumps > 1.5 s glide in 350 ms; paused, the fill dims.
  Hidden by setting, back on a key press.
- **Per frame only cheap work:** blur/palette/resampling/glow happen once per track in
  `SceneBuilder`; animations only change alpha/transform/colour filters, read in draw lambdas.
- **Honest resolution:** ≤ 1.3 source px per 1080p px (`HaloLayout.MAX_SOURCE_SCALE_1080P`);
  small covers sit optically centred in a near-black mat with grain and a 1 dp white bevel.
- **Calm motion** (`Motion`): a track change (1.25 s) dips through dark instead of
  double-exposing – the old artwork goes dark (0–320 ms), the new one comes up (300–900 ms)
  and settles from 1.5 % larger, the light turns to the new colours (0–900 ms); old text
  leaves in 200 ms, new lines rise one after another from 560 ms. Same picture (album) → only
  text; same track in new light → quiet cross-fade. Pause (600 ms) dims light, artwork and
  text – nothing moves, so the lines hung on the artwork's edges stay aligned. Mute is only
  a word; a lost link dims like a pause. Nothing slides, nothing bounces. Screens switch by
  fading through dark. **One change at a time:** what arrives during a change waits until it
  has landed, then the newest goes next (`snapshotFlow … conflate()`). Only another *song*
  hurries the running change (`playChange`, ≤ 350 ms) – a card joining the strip, a queue
  update or a better cover just wait. Lines on their way up stop where they are when a change
  hurries and leave from there (`RisingText`): the skipped song's lines never come up; if the
  skip comes to nothing (back to the same song), they rise the rest of the way.
- **Cover Flow** (`StageView.FLOW`): middle cover 300 dp centred 272 dp from the left, two
  cards per side turned 60° in one shared perspective (outer edges 222.5/255 dp beside the
  centre, 92 % height), mirrored on a glossy floor; column from 570 dp, 50 dp right margin;
  every other rule holds. A card is one textured quad (print, corners and – on the dark gloss
  only – its reflection baked; a half-size copy against shimmer – HWUI has no mipmaps). Side
  cards at 55 % / 40 %, and on the dark wall a much lighter card steps back further beside a
  dark middle one. Dark: a soft oval of light above the middle card in its top edge's colour,
  ≤ L* 8 and following that edge's lightness (`FlowLight`); paper: no reflection, a soft
  shadow below each card; night: side cards step back into the dark. The strip turns over in
  1.3 s (`Motion.Turn`, peak ≤ 2× average speed); leaving cards swing aside early so crossing
  cards never intersect (tested); cards that make way dissolve first (300 ms), then new ones
  roll in opaque from beyond the strip's end, their centre crossing the screen at an even
  pace (`FlowGeometry.rolled`) – never two covers on top of each other. Cards are pictures,
  not songs (`CoverStrip`, `TrackInfo.pictureKey`); ids are kept so every change animates
  card by card.
- **States say it in words**, in the status line, place first and state last: "Wohnzimmer
  · Pausiert", "Küche · Stumm", "Lautstärke 35 %" (3.5 s), "Überall";
  "Verbindung wird wiederhergestellt …" in the progress bar's place.
- **Settings menu** (OK long-press / Menu): the stage's grid, heading and the chosen line's
  explanation on the left, lines on the right; the chosen line sits at the screen's middle in
  a soft, low band of light (σ 120 × 12 dp, ≈ L* 10) that lights it alone; the list glides
  through it and fades with distance. Values are words, changes apply at once (left/right
  clamp, OK wraps), closes itself after 60 s. Options show or hide parts, choose the view and
  the wall, and dim the light – no fonts, colours or layout variants.
- **Music Assistant** is optional: without it Klangbild works from Home Assistant alone; with
  it (address + long-lived token on the setup page, checked before storing) the cover flow
  shows the whole queue and the album line its year. Tokens only ever go to the address they
  were made for; a new address without its token drops the old one.
- **Generated art:** Chladni plates are ring modes – two or three nodal circles inside 0.64 of
  the radius – made wavy by a second mode (3–6 lobes, rounded: amplitude ≤ 1.5 r/(n² − 1),
  share capped so circles never touch), mirror-symmetric, no sand beyond 0.74–0.84 of the
  radius. No nodal diameters (crosses, wheels, rosettes), no single circle or two lobes ("O").
- **Rest screen:** large light clock ("H:mm", 132 sp, `Figures`) under a dim pool of light
  that moves with it; re-hung every 15 min; one plain sentence when something is wrong
  ("Home Assistant ist nicht erreichbar."); night: black wall, only the time.
- **German UI texts**, 24 h clock. The phone's setup page follows the same rules: one
  (system) sans face, sentence case, no italics, light or dark as the phone is set.
- Never claim device behaviour that was not verified on the device (frame rate, CEC wake,
  dream on Google TV) – mark it as unverified.
