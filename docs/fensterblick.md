# Fensterblick

Ist die Musik angehalten (oder auf Wunsch auch während sie läuft), wird der Fernseher zu
einem Fenster: eine Landschaft aus geschnittenem Papier – Berge, ein See, zwei bewaldete
Hänge, eine Wiese mit einem Laubbaum, dunkles Ufer im Vordergrund. Ihr Licht folgt der
echten Tageszeit, Sonne, Mond und Sterne stehen, wo sie gerade stehen, die Wolken folgen
dem Wetter, und über Wochen wechseln die Jahreszeiten. Unten links (oder rechts) liegt
klein, was gerade läuft.

![Ein Jahr: Frühling, Sommer, Herbst, Winter – jeweils Sonnenaufgang, Mittag, goldene Stunde, Dämmerung, blaue Stunde, Nacht](fensterblick/jahr.jpg)

*Ein Jahr in Berlin: Zeilen Frühling (26. April), Sommer (16. Juli), Herbst (10. Oktober),
Winter (22. Januar); Spalten Sonnenaufgang, Mittag, goldene Stunde, Dämmerung, blaue
Stunde, 23:30 Uhr.*

## Inhalt

1. [Kurs](#kurs)
2. [Recherche](#recherche)
3. [Richtungen und Entscheidung](#richtungen-und-entscheidung)
4. [Bildaufbau](#bildaufbau)
5. [Licht und Farbe](#licht-und-farbe)
6. [Himmel](#himmel)
7. [Jahreszeiten](#jahreszeiten)
8. [Bewegung](#bewegung)
9. [Einbindung in Klangbild](#einbindung-in-klangbild)
10. [Technik](#technik)
11. [Messung](#messung)
12. [Politur: Mängelliste](#politur-mängelliste)
13. [Bewusst weggelassen](#bewusst-weggelassen)
14. [Entscheidungen](#entscheidungen)

## Kurs

Der erste Entwurf ging in Richtung Farbholzschnitt mit möglichst echtem Himmel: Wolken
mit Volumen, ein spiegelnder See. Das Ergebnis wirkte wie eine misslungene Fotografie –
„Comic-Wolken und ein seltsam echt wirkender See“. Auf diesen Einwand hin wurde der Ansatz
vollständig ersetzt: kein Versuch mehr, Natur nachzubilden, sondern ein Bild aus flachen
Papierbögen in sorgfältig gemischten Farben, mit Verläufen nur dort, wo Licht zwischen den
Bögen liegt. Alles Weitere in diesem Dokument beschreibt diesen zweiten Ansatz.

## Recherche

| Frage | Möglichkeiten | Ergebnis |
| --- | --- | --- |
| Womit zeichnen? | AGSL-Shader (ab API 33), OpenGL/Filament, Lottie/Rive, Vektorpfade pro Bild, vorgebackene Bitmaps | Vorgebackene Bitmaps, eingefärbt per Farbmatrix. Shader rechnen jedes Pixel in jedem Bild – auf der schwachen PowerVR-GPU bei 4K das teuerste Mittel. Lottie und Rive brauchen Assets und laufen in Schleifen. Pfade pro Bild kosten CPU für die Tesselierung. |
| Wie sieht der Himmel aus? | Physikalische Modelle (Preetham, Hosek-Wilkie), gemischte Farbtafeln wie bei Farbholzschnitt-Serien und Papier-Leuchtkästen | Gestaltete Farbtafeln je Tageszeit. Physik ergab trübe Mischtöne und genau den „echten“ Look, der nicht gewollt ist. |
| Sonne, Mond, Sterne | Nur `sun.sun` aus Home Assistant, eigene NOAA-Formeln, commons-suncalc | commons-suncalc 3.11 (Apache 2.0, reines Java, etwa eine Minute genau): Sonnen- und Mondstand, Mondphase. `sun.sun` kennt den Mond nicht. Sternzeit nach der Standardformel. |
| Wo steht der Fernseher? | Home Assistant `get_config`, Zeitzone | `get_config` (Breite, Länge); ohne Home Assistant die Hauptstadt der Zeitzone (Europa) oder Breite 50° und die Länge aus dem UTC-Versatz. |
| Bewegung ohne Schleife | Feste Animationen, Zufall, Rauschen | Summen aus Perlin-Rauschen mit inkommensurablen Perioden (Wind, Krone, Sterne, Lichtsplitter); Wolkenfolge aus einem Hash ihrer Nummer. Nichts wiederholt sich erkennbar. |

## Richtungen und Entscheidung

Drei Richtungen wurden abgewogen – A gebaut und verworfen, B gebaut, C nur durchdacht:

- **A – Farbholzschnitt, naturnah** (nach Hiroshi Yoshida, Kawase Hasui): Holzmaserung,
  weich modellierte Wolken, spiegelndes Wasser. Verworfen – beim Versuch, Natur
  nachzuahmen, landet man im Unheimlichen.
- **B – Scherenschnitt, Papier-Leuchtkasten**: gestaffelte Bögen, jeder eine Farbe, weiche
  Schlagschatten zwischen ihnen, Licht als Verlauf am Fuß jedes Bogens. **Gewählt.**
- **C – Reiseplakat der 1930er** (flächige Gouache): kräftig, aber lebt von gemalten
  Texturen und einer festen Stimmung; bei wechselndem Licht wirkt es schnell wie ein
  eingefärbtes Plakat. Nicht gebaut.

**Begründung:** Papier verspricht keine Wirklichkeit – Licht und Jahreszeit dürfen sich im
Bild zeigen, ohne dass etwas „echt“ aussehen muss. Die Technik entspricht dem Material:
Jeder Bogen wird einmal geschnitten und nur noch eingefärbt, was auf schwacher Hardware
billig ist. Flächen statt Details halten das Bild ruhig genug, um stundenlang neben der Musik
zu stehen.

## Bildaufbau

Von hinten nach vorn (Raster 960 × 540 dp, 18 dp Überstand für die Drift):

1. **Himmel** – vier Farbstufen von oben bis zum Horizont.
2. **Sterne, Mond, Sonne**, Leuchten am Horizont und um die Sonne.
3. **Wolken** – Papierstreifen, die sich zu Spitzen verjüngen.
4. **Ferne Kette** – vier Gipfel, Schneekappen mit gezackter Schneegrenze.
5. **Nahe Kette** – runder, mit weicher Schneegrenze.
6. **Fernes Ufer** – fein geschnittener Waldsaum.
7. **See** – nah heller, fern im Himmelslicht; Lichtsplitter unter Sonne oder Mond.
8. **Hänge links und rechts** – Tannenreihen auf dem Kamm, dazwischen Laubbäume.
9. **Wiese** mit dem Laubbaum (Eiform, untere Hälfte als zweites, dunkleres Papier).
10. **Ufer vorn** – dunkel, mit Gras, Schilf und der großen Tanne.

Unter dem Mini-Player ist die Uferbank breiter und fast eben geschnitten; ist er
ausgeschaltet, fällt das Ufer auf beiden Seiten niedrig ab. Die Komposition passt sich also
an, wo die Beschriftung liegt.

## Licht und Farbe

**Farbtafeln.** Für jede Tageszeit gibt es eine gemischte Tafel: vier Himmelsfarben und
eine Rampe von sieben Landfarben, vom fernsten Bogen (nahe dem Himmel) bis zum dunklen
Ufer. Morgens und abends haben eigene Tafeln (Nacht −16°, blaue Stunde −7,5°, Dämmerung
−3°, Sonnenauf- und -untergang 1°, goldene Stunde 6,5°, Vor- und Nachmittag 18°, Tag 40°),
zwischen denen nach Sonnenhöhe überblendet wird; um Mittag gehen Morgen- und Abendreihe
ineinander über. Jeder Bogen wird an seinem Fuß heller, zum Bogen dahinter hin – das Licht
zwischen den Papieren.

- **Mond:** je nach Phase und Höhe eine eigene, kühle Mondlicht-Tafel.
- **Wetter:** Bewölkung dämpft – Sättigung bis −65 %, Helligkeiten rücken zusammen, Leuchten
  verblassen, ab dichter Bewölkung verschwinden Sonnen- und Mondscheibe. Nie dunkler,
  nur stiller. Regen, Schnee und Gewitter zählen nur als „bedeckt“.
- **Schatten** fallen von der Lichtquelle weg: seitlich nach Sonnen- oder Mondazimut,
  länger bei tiefem Licht.
- **Kontrast:** Die Schrift des Mini-Players wird aus der Farbe der Uferbank berechnet und
  hält in jeder Lage 4,5:1 (getestet über Tageszeit, Wetter, Mond und Jahreszeit).

![Wetter und Mond: klar, leicht bewölkt, bewölkt, Regen, Vollmond über Schnee, ohne Mini-Player](fensterblick/wetter.jpg)

## Himmel

- **Blick nach Süden:** Osten links, Westen rechts, 3 dp pro Grad Azimut – der ganze
  Sonnenbogen eines Tages passt ins Bild.
- **Höhen ab dem Bergkamm:** Die Höhe über dem Horizont wird vom geglätteten Kamm der fernen
  Kette aus gemessen, nach oben hin etwas enger. So geht die Sonne genau dann über dem Kamm
  auf, wenn das Licht morgendlich wird, und versinkt mit den Abendfarben dahinter – auch
  die tiefe Dezembersonne steht mittags über den Gipfeln.
- **Sterne:** 420 gleichmäßig verteilte Sterne in drei Helligkeiten, gedreht nach der
  Sternzeit; sie flackern in vier Gruppen langsam.
- **Mond:** echte Phase, die beleuchtete Seite zur Sonne, die dunkle Scheibe angedeutet.
- **Wolken:** vier Bahnen in verschiedenen Höhen und Tempi (0,5–1,25 dp/s), sechs
  Streifenformen, Reihenfolge aus einem Hash: kein Muster wiederholt sich. Mehr Bewölkung
  heißt mehr Streifen; sie entstehen und vergehen langsam an Ort und Stelle.

## Jahreszeiten

Nach Kalender (auf der Südhalbkugel um ein halbes Jahr versetzt), jede Änderung über
Wochen:

| | Frühling | Sommer | Herbst | Winter |
| --- | --- | --- | --- | --- |
| Farben | frischere Wiese und Hänge | Grundtafel | Wiese strohgelb, Hänge kaum wärmer | kühler, ruhiger |
| Laubbaum | Blüte (rosa), dann frisches Grün | Grün | Rost-Orange, dann lichter | kahl, Äste zeigen sich |
| Laubbäume am Kamm | hellgrün, einige blühend | etwas heller als die Tannen | Gold, Orange, Rost | verschwunden, nur Tannen |
| Schnee | Kappen weichen zurück | nur der höchste Gipfel | erster Schnee auf der fernen Kette | ganze Hänge, Schnee auf den Tannen |

Formen (Schnee, Laub, Blüte) werden in Stufen von 1/16 neu geschnitten und über 20 Sekunden
überblendet – am Bildschirm sieht das niemand, die Stufen liegen Tage auseinander.

## Bewegung

Wenige, dauerhafte, langsame Bewegungen – keine Schleife, kein Sprung:

- **Licht** folgt der echten Uhrzeit (alle zehn Sekunden neu berechnet, jeder Schritt unter
  einer Farbstufe).
- **Wolken** ziehen von Westen nach Osten.
- **Gras und Schilf** wiegen sich in einem Wind, der als Böe über das Bild läuft.
- **Die Krone** des Laubbaums schwankt kaum merklich.
- **Sterne** flackern, **Lichtsplitter** auf dem Wasser atmen.
- **Drift:** Der ganze Bildschirm wandert wie überall in Klangbild um ±6 dp gegen Einbrennen.

20 Bilder pro Sekunde genügen dafür (die Vorschau zeichnet mit 30, weil dort das Licht
gleitet).

![Zeitraffer: ein Tag in zwei Minuten, alle fünf Sekunden ein Bild](fensterblick/zeitraffer.jpg)

## Einbindung in Klangbild

**Wann er erscheint** (`VistaPolicy`):

- **Pause oder nichts spielt:** nach der Zeit unter *Fensterblick → Bei Pause*
  (Standard drei Minuten; 5 s bis 10 min oder aus). Bis dahin zeigt die Bühne das pausierte
  Cover bzw. die Ruhe-Uhr. Ist der Fensterblick aus, wird ein pausiertes Cover wie bisher
  nach drei Minuten zur Uhr.
- **Während der Musik:** nur wenn unter *Während der Musik* eine Zeit gewählt ist
  (1–30 Minuten ohne Tastendruck).
- **Nie über Bildschirmen, die etwas zu sagen haben:** Verbindung weg, Home Assistant nicht
  erreichbar, Lautsprecher unbekannt, Einrichtung. Und nicht, solange das Menü offen ist.
- **Ausschalten:** „Ausschalten nach“ gilt weiter – ohne Musik gibt Klangbild den
  Bildschirm nach der eingestellten Zeit frei, auch wenn der Fensterblick läuft.

**Fernbedienung:**

- Jede Taste beendet den Fensterblick weich (Blende durch das Dunkle) und startet die
  Wartezeit neu; die Taste selbst bewirkt sonst nichts. Medientasten tun zusätzlich, was sie
  sagen. Zurück beendet ihn, statt die App zu schließen.
- **Pfeil runter lange drücken** zeigt ihn sofort (nicht, wenn hoch/runter die Lautstärke
  regeln – dort bleibt das gehaltene Runter die Lautstärke).
- Aus Home Assistant per `am start`: `--es fensterblick an|aus|zeitraffer`, außerdem
  `fensterblick_pause`, `fensterblick_musik` (Sekunden oder „aus“), `mini_player`
  (`links|rechts|aus`) und `weather` (Entität, `auto` oder `keine`). Ein
  `klangbild://`-Link darf dasselbe – nur nicht die Wetter-Entität.

**Menü → Fensterblick:** Bei Pause, Während der Musik, Mini-Player (Unten links / Unten
rechts / Aus), Vorschau.

**Vorschau:** OK auf *Vorschau* zeigt den Fensterblick. Links/rechts verstellen die Stunde,
hoch/runter den Monat, OK das Wetter (wie draußen, klar, leicht bewölkt, bedeckt), OK lange
(oder die Play-Taste) lässt einen Tag in zwei Minuten vergehen, Zurück führt ins Menü.
Statt des Mini-Players steht dort, was gezeigt wird.

![Vorschau im Juli, 21 Uhr](fensterblick/vorschau-juli.jpg)

**Mini-Player:** Cover (56 dp), Titel in Jost 24 sp, Künstler in DM Sans 17 sp, ein schmaler
Fortschrittsbalken (pausiert gedimmt); nachts wird das Cover mit dem Bild dunkler.

![Mini-Player bei Tag, bei Nacht und rechts](fensterblick/mini-player.jpg)

**Einrichtung am Handy:** Abschnitt *Fensterblick* mit der Wetter-Entität: *Automatisch*
(`weather.forecast_home` oder `weather.home`, wie sie die Met.no-Integration anlegt), eine
gefundene Entität oder *Keines* (immer ein heiterer Himmel). Den Standort liefert Home
Assistant selbst.

## Technik

- **Gebacken, nicht gezeichnet:** Jeder Bogen wird einmal pro Bildschirmgröße und
  Jahreszeit-Form gerastert (exaktes Anti-Aliasing, Dithering gegen Streifen). Gespeichert
  werden keine Farben, sondern Gewichte von drei Tinten (Rot, Grün, Blau) und die Deckung
  (Alpha). Pro Bild färbt eine Farbmatrix jeden Bogen ein – Licht ändert sich, ohne dass
  etwas neu gezeichnet wird.
- **Nur was zu sehen ist:** Jeder Bogen hat seine eigene Box und reicht nur so weit nach
  unten, wie ihn nichts verdeckt. Pro Bild werden etwa vier Bildschirmflächen gezeichnet
  (vorher neun).
- **Speicher:** 24 MB bei 1080p, 97 MB bei 4K (Zahlen aus dem Backen; beim Wechsel der
  Jahreszeit-Form kurz doppelt).
- **Pro Bild auf der CPU:** Wolkenplätze und Wind (JVM: unter 1 µs); alle zehn Sekunden
  Sonne/Mond (15 µs), Farben (36 µs), Sterne (62 µs).
- Code: `vista/` (Motiv, Backen, Licht, Himmel, Welt, Uhr, Wolken), `ui/vista/`
  (Zeichnen, Bildschirm, Mini-Player), `domain/VistaPolicy`.

## Messung

Ein Gerät stand nicht zur Verfügung. Gemessen wurde

- **auf der JVM** (Robolectric/Desktop): Backen bei 1080p etwa 0,7 s, bei 4K etwa 1,4 s
  (auf dem Gerät vermutlich mehrfach länger, läuft im Hintergrund, das alte Bild bleibt so
  lange stehen);
- **im Android-TV-Emulator der CI** (Android 14, Software-GPU SwiftShader), zuerst mit dem
  Mess-Prototyp (gleiche Ebenenzahl, Platzhalterformen; 90 s ruhig, 90 s Zeitraffer, 1080p):
  3–5 Bilder pro Sekunde (SwiftShader), 21–26 % eines CPU-Kerns ruhig, 74 % im Zeitraffer,
  63–88 MB PSS ohne Wachstum. Das 4K-Profil des Emulators zeichnet die Oberfläche in 1080p
  (`wm size`: 3840 × 2160, Override 1920 × 1080); sein Zeitraffer-Lauf blieb im Emulator
  hängen (deshalb haben die Messskripte jetzt Zeitlimits). Der Messlauf „15 Minuten“ mit dem
  fertigen Fensterblick (15 Minuten ruhig, 2 Minuten Zeitraffer) läuft in der CI; seine Werte
  werden hier eingetragen.

Die Bildrate des Emulators sagt nichts über den Google TV Streamer: SwiftShader rechnet
alles auf der CPU. Aussagekräftig sind Speicher (wächst er?) und dass nichts abstürzt.
**Nicht am Gerät geprüft:** Bildrate und GPU-Last auf dem Streamer, ob die App dort in
1080p oder 4K zeichnet, Backzeit auf dem Gerät, Einbrennverhalten auf dem OLED.

## Politur: Mängelliste

Nach dem Durchsehen aller Tages- und Jahreszeiten (Galerie oben) und der Bildfolgen:

| # | Mangel | Behoben durch |
| --- | --- | --- |
| 1 | Schneekappen wie Zuckerguss-Tropfen | gezackte Schneegrenze, unregelmäßiger Rhythmus |
| 2 | Laubbaum wie ein Pilz | Eiform, untere Hälfte als dunkleres Papier |
| 3 | Herbstwälder kupfern und trüb | Hänge bleiben Nadelwald; dazwischen Laubbäume in Gold, Orange, Rost; Wiese strohgelb |
| 4 | Winter grau und leblos | ganze Hänge verschneit, Tannen dunkel, Schnee auf den Ästen |
| 5 | Zickzack-Band über der nahen Kette | weiche Schneegrenze; die nahe Kette bekommt Schnee später |
| 6 | Tannenreihe rechts wie ein Zaun, Gras schwebte über dem Ufer | Fehler behoben (Linien von rechts nach links gaben falsche Höhen), Reihe dichter |
| 7 | Nacht ohne Tiefe | Nachttafel neu: Berge dunkler als der Himmel am Horizont |
| 8 | Laubkrone bei Sonnenuntergang grau | Krone nimmt abends den Ton des Waldes an |
| 9 | Mond als Ei | dunkle Scheibe angedeutet |
| 10 | Lichtreflexe wie „≡“ | schmale Lichtsplitter |
| 11 | Uferplateau unter dem Mini-Player zu wuchtig | niedrige, flache Uferbank |
| 12 | Titel im Mini-Player zu früh gekürzt | Textspalte 262 dp, Ufer entsprechend geschnitten |
| 13 | Cover im Mini-Player nachts zu hell | dimmt mit dem Bild |
| 14 | Wintersonne den ganzen Tag hinter den Bergen | Höhen ab dem Bergkamm |
| 15 | Schnee–Fels–Schnee-Streifen im Frühling | Schnee reicht bei tiefer Schneegrenze bis zum Fuß der fernen Kette |
| 16 | Sonnenscheibe bei Regen | Scheiben und Leuchten verblassen bei dichter Bewölkung |
| 17 | Blütenkrone leuchtet nachts | Blüte sinkt mit dem Licht |
| 18 | Zu viel Zeichenarbeit (9 Flächen pro Bild, 53 MB) | Boxen je Bogen: 4 Flächen, 24 MB |
| 19 | Menütext begann eine Zeile mit „–“ | geschütztes Leerzeichen |

## Bewusst weggelassen

- Regen, Schnee, Blitze, Nebel, Partikel – das Wetter ist nur Stimmung.
- Wind, Temperatur, Luftfeuchte.
- Ein Ereigniskatalog – und auch der eine erlaubte seltene Moment: Jede Figur, die durchs
  Bild zieht, holt den Blick von der Musik weg und wäre irgendwann die bekannte Figur.
- Spiegelungen im See, Papiertextur (auf dem OLED wirkt Körnung wie Rauschen), Tiere,
  Häuser, Menschen.
- Ebenen-Parallaxe: Die Drift verschiebt alles gemeinsam; einzeln verschobene Ebenen
  würden die Linien der Komposition gegeneinander laufen lassen.

## Entscheidungen

- **Scherenschnitt statt Holzschnitt** – siehe Kurs und Begründung oben.
- **Gestaltete Farbtafeln statt Himmelsphysik**, Morgen und Abend getrennt, um Mittag
  überblendet.
- **Höhen ab dem Bergkamm**: Sonnenauf- und -untergang am Kamm, wenn die Farben wechseln.
- **Die Komposition folgt dem Mini-Player** (eigene Uferform je Seite).
- **Pause → Fensterblick nach drei Minuten** – dieselbe Zeit, nach der bisher die Uhr kam.
  Die Uhr bleibt für die ersten Minuten ohne Musik und für den ausgeschalteten Fensterblick.
- **Während der Musik standardmäßig aus:** Das Cover ist dann das Bild.
- **Pfeil runter lange** als Taste: Die Fernbedienung des Streamers hat keine freie Taste,
  Pfeil runter zeigt sonst nur Informationen. Mit Lautstärke auf hoch/runter entfällt sie.
- **OK lange startet in der Vorschau den Zeitraffer**, weil der Streamer keine Play-Taste
  hat; die Play-Taste anderer Fernbedienungen tut dasselbe.
- **Wetter automatisch:** die Entitäten, die Home Assistants Standard-Wetter anlegt; sonst
  auf der Einrichtungsseite wählen.
- **Standort aus Home Assistant**, ohne ihn aus der Zeitzone geschätzt.
- **20 Bilder pro Sekunde**, Vorschau 30.
- **Jahreszeiten nach Kalender**, auf der Südhalbkugel versetzt.
