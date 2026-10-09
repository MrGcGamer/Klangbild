# Entscheidungen

Jede Entscheidung mit Begründung. Neueste Abschnitte unten.

## 1. Name: Klangbild

„Klangbild“ ist in der Hi-Fi-Sprache der Charakter eines Klangs – und wörtlich das Bild
des Klangs. Das passt zum Anspruch (hochwertiges Hi-Fi-Gerät, Kunstdruck) und zum
Ersatzbild für fehlende Cover (Chladni-Figuren, siehe 6).

## 2. Toolchain

| Baustein | Version | Anmerkung |
|---|---|---|
| Gradle | 9.8.1 | Wrapper mit SHA-256-Prüfsumme |
| Android Gradle Plugin | 9.4.1 | „Built-in Kotlin“: kein `kotlin-android`-Plugin mehr |
| Kotlin | 2.4.20 | 2.4.21 erschien am Tag des Projektstarts und war auf dem Maven-Spiegel noch nicht verfügbar |
| Compose BOM | 2026.09.00 | verlangt `compileSdk 37` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 31 | Zielgerät hat API 34 |
| Roborazzi / Robolectric | 1.76.0 / 4.17 | JVM-Screenshots mit Native Graphics |
| OkHttp | 5.5.0 | WebSocket und Bilder |

- **Maven-Central-Spiegel:** `repo1.maven.org` drosselt geteilte Egress-IPs (HTTP 429). Gradle
  und Robolectric nutzen zuerst Googles offiziellen Spiegel
  (`maven-central.storage-download.googleapis.com`), Maven Central bleibt als Rückfall.
- **Keine TV-Material-Bibliothek:** Die App hat keine Bedienelemente; `androidx.tv:tv-material`
  brächte nur Gewicht. Compose Foundation reicht.
- **JSON ohne Serialization-Plugin:** HA-Nachrichten werden über `JsonElement` gelesen. Das ist
  robust gegen Schemaänderungen und braucht keine R8-Regeln.

## 3. Screenshot-Pipeline: Roborazzi statt Paparazzi

Paparazzi gibt es für AGP 9 nur als Alpha (2.0.0-alpha05). Roborazzi auf Robolectric
Native Graphics rendert Compose mit echter Skia-Pipeline, inklusive Systemschriften für
Japanisch, Arabisch und Emoji (geprüft). Effekte, die nur der GPU-Renderer kann
(`RenderEffect`), nutzt die App bewusst nicht – alles Teure wird auf der CPU vorberechnet
und ist damit auch im Test identisch.

## 4. Gestaltungsrichtung: Halo

Gerendert wurden drei Richtungen (siehe `docs/design/richtungen.jpg`):

- **Halo** – das Cover als hinterleuchtetes Objekt im dunklen Raum; seine Farben fallen als
  Licht auf die Wand.
- **Galerie** – gerahmter Druck mit Passepartout an einer Museumswand, Werkschild daneben.
- **Feuilleton** – Zeitschriftendoppelseite mit großer, schmaler Serifenschrift.

Bewertung (eigene und die eines unabhängigen Subagenten, der nur die Bilder kannte – beide
kamen zum selben Ergebnis):

- **Halo gewinnt.** Es ist die einzige Richtung, die aus Licht und Dunkelheit gebaut ist. Auf
  einem OLED sind große Flächen wirklich schwarz, das Cover leuchtet wie eine Lampe. Die
  Schwächen waren App-Reste (Fortschrittsbalken mit Zeitstempeln, verstreute Elemente), die
  sich entfernen lassen.
- **Feuilleton** hat die selbstbewussteste Typografie, ist aber laut und bricht bei langen
  Klassik-Titeln und nicht-lateinischer Schrift.
- **Galerie** scheitert am OLED: das helle Passepartout ist die größte statische helle Fläche,
  nachts blendet es, und der Rahmen wirkt wie ein digitaler Bilderrahmen.

Nur Halo wird ausgearbeitet. Die anderen Richtungen bleiben nicht wählbar, weil sie das
Niveau nicht erreichen (siehe Brief: weitere Stile nur bei gleichem Niveau).

Übernommen aus der Kritik (Sonnenuhr und Zeit in Worten seit Version 2 abgelöst, siehe §23):
- Kein Fortschrittsbalken, keine Zeitstempel. Stattdessen: **das Licht als Sonnenuhr** – eine
  gerichtete Lichtquelle hinter dem Cover wandert im Laufe des Titels von links unten über
  oben nach rechts unten (unmerklich langsam, nach Minuten deutlich). Dazu eine Zeile in
  Worten: „In 3 Minuten · nächster Titel“. Genaue Zeiten erscheinen nur kurz nach einem
  Tastendruck.
- Halo in einer sauberen Leitfarbe (statt einer gemittelten, schlammigen Mischung), eng um
  das Cover, erreicht nie den Bildschirmrand.
- Strenges Raster: zwei Blöcke (Cover und Textspalte), gleiche Außenränder, Uhr oben in der
  Textspalte, Titel wächst nach oben, Künstlerzeile fest.
- Kein hartes Weiß, auch nicht in hellen Covern (Lichter werden sanft komprimiert).
- Nachts werden Zeilen weggelassen statt nur gedimmt.

## 5. Schriften

*Seit Version 2 abgelöst: eine Schrift, Mona Sans – siehe §23.*

Newsreader (Production Type) für Titel – ruhig, literarisch, mit optischen Größen, sodass
die Display-Variante fein und die Textvariante robust ist. Jost (indestructible type*) für
Kapitälchen-Labels und Ziffern – Futura-Anmutung, die Stimme guter Hi-Fi-Geräte. Beide unter
SIL Open Font License, Lizenzen in `app/src/main/assets/licenses/`. Verglichen wurden u. a.
Fraunces, Cormorant, Bodoni Moda, Playfair, Instrument Serif, Inter, Manrope, Geist.
Japanisch, Arabisch und Emoji kommen aus den Systemschriften (Noto); für diese Schriften
gibt es keine Versalien, keine Sperrung und keine Kursive.

## 6. Fehlendes Cover: Chladni-Figuren

Statt eines Noten-Icons malt die App eine Chladni-Figur – das Muster, das Sand auf einer
schwingenden Platte bildet. Buchstäblich ein Bild des Klangs. Jede Station bzw. jedes Album
bekommt eine eigene, stabile Figur und eines von sechs kuratierten Farbpaaren. Die Figur
wird in exakter Bildschirmauflösung erzeugt, ist also nie hochskaliert.

Die erste Fassung (quadratische Platte, Kosinus-Moden) sah aus wie Tic-Tac-Toe-Tapete. Jetzt
liegt eine **runde Platte** von oben gesehen auf dunklem Grund, mittig eingespannt: zwei fast
gleich hohe Schwingungsmoden (Besselfunktion im Radius, Kosinus im Winkel) überlagern sich –
das ergibt die geschwungenen, blütenartigen Figuren echter Sandfotos. Die Linien sind über
|f| / |∇f| überall gleich breit und aus einzelnen Sandkörnern gestippelt. Rechenzeit:
etwa 40 ms für 400 × 400 px auf dem Entwicklungsrechner.

## 7. Ehrliche Auflösung

Ein Cover wird höchstens mit 1,1 Quellpixeln pro 1080p-Pixel gezeigt (bei 4K also maximal
2,2-fach, per Lanczos-3 einmal pro Titel auf die exakte Pixelgröße gebracht). Aus drei
Metern Abstand liegt das unter dem Auflösungsvermögen des Auges. Ein 300-px-Senderlogo
bleibt dadurch klein und scharf statt groß und matschig. Bekannte CDNs (Spotify, Deezer,
Apple, Tidal, Qobuz, Google, Music Assistants Imageproxy) werden nach der größten Variante
gefragt; die Original-URL bleibt Rückfall.

## 8. Licht statt Effekte – Performance auf schwacher GPU

Der Streamer rendert in 4K mit einer PowerVR GE9215. Deshalb:
- **Vorberechnung pro Titel** (CPU, Hintergrund-Thread, nacheinander): Cover per Lanczos-3 auf
  die exakte Pixelgröße, Farbanalyse, ein **Graustufen-Sprite für den Lichtkegel** (512 × 512)
  und eine **Wandmaske** in 960 × 540 (Schatten des Bildes, Abstand zum Bildschirmrand) –
  analytisch, in linearem Licht, mit Dithering gegen Banding, gestreckt gezeichnet.
- **Licht pro Frame:** ein einziges Rechteck mit `ComposeShader(Lichtkegel × Wandmaske)` und
  einem Farbfilter. Für die Sonnenuhr wird nur die lokale Matrix des Lichtkegels gesetzt
  (verschieben, drehen, strecken); die Shader entstehen einmal pro Szene. Der Farbfilter ist
  die Lichtfarbe (kann beim Titelwechsel durch OKLCH wandern). Das Dithering ist so stark
  gewählt, dass nach dem Einfärben noch etwa ein Ausgabe-Level Rauschen übrig bleibt.
- **Pro Frame** nur Transparenz, Transformationen und Farbmatrizen; animierte Werte werden in
  Draw-/Layer-Lambdas gelesen, sodass Animationen nie neu komponieren.
- **Nichts animiert dauerhaft.** Wenn nichts passiert, wird nichts gezeichnet. Sonnenuhr und
  OLED-Drift ticken alle fünf Sekunden (wenige Pixel), die Zeitzeile ändert sich höchstens
  alle 15 Sekunden. Nur Übergänge (2,7 s) laufen mit voller Bildrate.
- Kein AGSL, kein `RenderEffect`: nicht nötig, und so sehen Tests und Gerät gleich aus.
- Ob es auf dem echten Gerät durchgehend 60 fps sind, ist **nicht geprüft** (kein Gerät
  verfügbar). Dafür gibt es `--ez debug true` (Bilder/s, langsamster Frame, verspätete Frames).

## 9. Signierung und versionCode

- **Keystore im Repo** (`keystore/klangbild-release.jks`, Passwort im Build-Skript). Das Repo ist
  privat, der Schlüssel wurde nur für diese Sideload-App erzeugt und schützt nichts anderes.
  GitHub Secrets konnte ich aus dieser Umgebung nicht setzen; so signieren lokale Builds und
  CI identisch, und `adb install -r` funktioniert immer.
- **versionCode = Minuten seit 2026-01-01 bis zur Commit-Zeit von HEAD.** Steigt mit jedem
  Commit automatisch, ist pro Commit deterministisch, braucht keinen vollständigen Git-Verlauf
  (CI checkt flach aus) und hält bis weit über 2100. `versionName` = Datum + Commit-Hash.
- Keine `abiFilters`: Die mitgelieferten nativen Bibliotheken (DataStore, Compose) enthalten
  `armeabi-v7a`, `arm64-v8a`, `x86` und `x86_64` (geprüft mit `aapt2 dump badging`).

## 10. Klartext nur im Heimnetz

Android kann in der Network-Security-Config keine IP-Bereiche ausdrücken. Deshalb erlaubt die
Plattform Klartext, und ein OkHttp-Interceptor (`LocalNetwork.CleartextGuard`) lässt `http://`
nur zu privaten/link-lokalen Adressen, `.local`/`.lan`/`.home.arpa`, Namen ohne Punkt und
Namen, die ausschließlich auf private Adressen auflösen. Alles andere braucht `https://`.

## 11. Token-Speicherung

DataStore im App-eigenen Speicher, `allowBackup=false` und Ausschluss aus Cloud-Backup und
Geräteübertragung. Bewusst ohne Android-Keystore-Verschlüsselung: Auf manchen TV-Geräten geht
der Keystore-Schlüssel bei Updates verloren, dann wäre der Token weg – die App-Sandbox ist für
ein privates Gerät der passende Schutz. Der Token geht nur an den HA-Host, nie an Cover-Server.

## 12. Einrichtung per QR-Code

Ein eigener Mini-HTTP-Server (ein Request pro Verbindung, harte Größenlimits, nur
`Content-Length`) statt eines Web-Frameworks: wenig Code, wenig Angriffsfläche, testbar. Er
läuft nur, solange der Einrichtungsbildschirm sichtbar ist. Jeder API-Aufruf braucht den
sechsstelligen Code vom TV (im QR-Code enthalten); nach acht Fehlversuchen wird ein neuer
gezogen. Der gespeicherte Token wird nie an den Browser zurückgegeben. Die Lautsprecherliste
kommt über die REST-API von HA (`/api/states`), Music-Assistant-Player zuerst.

## 13. Ruhe und Bildschirm

- Pause: Licht dimmt sofort sanft (2,4 s); nach 3 Minuten macht die Bühne der stillen Uhr
  Platz; nach der Ruhezeit (Standard 10 Minuten) wird `FLAG_KEEP_SCREEN_ON` gelöscht.
- **Standard „Bildschirm freigeben“** statt „App schließen“: Der Fernseher folgt dann seinem
  eigenen Bildschirmschoner-/Schlaf-Timer, und wenn die Musik vorher weitergeht, ist Klangbild
  noch da. „App schließen“ ist wählbar.
- Die Einrichtung bekommt die dreifache Ruhezeit (jemand tippt am Handy), aber nie unendlich.
- Verbindungsabbrüche: 20 Sekunden lang läuft die Bühne einfach weiter (die meisten Aussetzer
  sind kürzer), danach erklärt der Ruhebildschirm in einem Satz, was los ist.

## 14. Screenshots nicht als Baselines

Die Screenshot-Tests rendern alle Zustände bei jedem CI-Lauf (und laden sie als Artifact
hoch), vergleichen aber nicht pixelgenau mit eingecheckten Referenzbildern: Während der
Gestaltung ändern sich die Bilder ständig, und ~40 PNGs pro Runde würden das Repo aufblähen.
Kuratierte Bilder für die README liegen in `docs/screens/`.

## 15. Songtexte: bewusst (noch) nicht

Home Assistant reicht keine Songtexte durch. Music Assistant kennt sie
(`metadata/get_track_lyrics`, auch synchronisiert als LRC), aber nur über eine direkte,
authentifizierte Verbindung zum MA-Server mit eigenem Token. Für Version 1 überwiegt der
Einrichtungsaufwand – siehe Ideen in der Abschlussnotiz.

## 16. Remote-Steuerung

OK = Pause/Weiter, ◀/▶ = Titel zurück/vor über `media_player.*`-Aktionen in HA. Unsichtbar,
bis man sie braucht; als einzige Rückmeldung erscheinen kurz die genauen Zeiten. Abschaltbar.

## 17. Dritte Runde: Licht wie Licht, Wechsel im Dunkeln

Die zweite unabhängige Kritik fand das Leuchten zu sehr „Box-Shadow/Ambilight“, nachts zu
orange, die Glows oft braun, den Ruhebildschirm konzeptlos und den Titelwechsel als
Doppelbelichtung. Umgesetzt:

- **Lichtprofil wie bei hinterleuchteten Buchstaben:** direkt an der Kante 50 % (das Bild
  verschattet sein eigenes Licht), Maximum 8–14 dp außerhalb, dann exponentieller Abfall mit
  weichem Beginn (in sRGB ein gleichmäßiges Ausklingen statt eines flachen Bandes). Ein
  schwaches, breites Licht neigt sich zur offenen Wand. **36 dp vor jedem Bildschirmrand ist
  kein Licht mehr**, weit weg vom Bild ist die Wand **reines Schwarz** (OLED: kein graues
  Fast-Schwarz mit Mura oder Treppen).
- **Sonnenuhr als Maske:** die beleuchtete Seite ist in 8-Bit-Werten etwa 2,5-mal heller als
  die abgewandte (linear ~7×), mit einer schmalen Keule – man sieht, woher das Licht kommt.
- **Lichtfarbe:** Cluster ähnlicher Farbtöne zählen zusammen, Fläche zählt mehr als
  Helligkeit (der violette Himmel statt der kleinen orangen Sonne). Orange und Gelb werden
  zu gesättigtem Bernsteinrot gezogen – dunkles, mattes Orange liest sich als Braun. Neutrale
  Cover bekommen warmweißes Lampenlicht statt eines orangen Rahmens. Wärmer wird es erst nach
  Sonnenuntergang; nachts höchstens 0,3 × Tageshelligkeit und höchstens 50 % Sättigung.
- **Das Bild als Objekt:** ein ±4-%-Streiflicht über dem Cover aus Richtung der Sonnenuhr,
  eine Haarlinie Licht an den beleuchteten Kanten, Schatten an den abgewandten.
- **Titelwechsel nacheinander statt übereinander:** altes Bild 700 ms ins Dunkel, die leere
  Wand hält, während die Lampe auf 30 % dimmt und ihre Farbe durch OKLCH wechselt, neues Bild
  1200 ms herauf, der Text folgt. Die Uhr bleibt stehen. Folgt ein Titel desselben Albums
  (gleiches Bild), wechselt nur der Text.
- **Pause:** Die Zeitzeile blendet über (600 ms) statt zu springen, der Text dimmt mit.
- **Typografie:** Titel höchstens drei Zeilen, Umbruch bevorzugt nach den Gedankenstrichen
  eines Satzes; Künstler auf einer Zeile („… u. a.“, kursiv statt in Versalien, die wie
  Initialen aussähen); Countdown kleiner und leiser als die Künstlerzeile; ein einziges
  Trennzeichen „ · “; schmale geschützte Leerzeichen in „Nr. 9“, „op. 125“; Versionsnotiz
  klein hinter dem Titel; Emoji am Titelrand weg, im Titel gedämpft; japanische Titel
  0,86-mal so groß, in Schriften ohne Versalien ist der Künstler größer als das Album.
- **Ruhebildschirm:** Uhr in der Serifenschrift auf einer schwach lampenbeleuchteten Wand,
  deren hellste Stelle mit der Sonne wandert; der Block wird jede Viertelstunde umgehängt;
  nachts nur die Uhr.

Nicht übernommen (bewusst): monochrome Emoji-Schrift (Größe, Lizenzpflege – Rand-Emoji
entfallen ohnehin), Mincho/Naskh-Schriften für Japanisch und Arabisch (mehrere MB; die
Systemschriften bleiben), animierte Sandfiguren beim Titelwechsel (schön, aber erst nach
einer Messung auf dem echten Gerät).

## 18. Vierte Runde: die Lampe hinter dem Bild

Die dritte Kritik (wieder ohne Kenntnis des Codes) fand das Licht noch immer „wie einen
UI-Effekt“: überall derselbe abgerundete Rahmen, nur heller oder dunkler. Umgesetzt:

- **Eine Lampe, keine Aura.** Das Licht ist jetzt ein Lichtfleck der Lampe hinter dem Bild,
  um etwa 24 dp zur Sonne versetzt: auf der Sonnenseite fällt es weit, auf der anderen kaum
  (cos³-Keule, im 8-Bit-Bild etwa 4:1). Technisch: ein Graustufen-Sprite pro Szene, pro Frame
  nur verschoben und mit der gedrehten Maske multipliziert; die statische Wandmaske (Schatten
  des Bildes, Abstand zum Rand) bleibt am Bild.
- **Kein schwarzes Feld beim Wechsel.** Während das alte Bild verblasst, verschwindet auch
  sein Schatten: die leere Wand ist ein ruhiger Lichtfleck. Wechselt das Bild die Seite,
  geht das alte Licht, bevor das neue kommt. Zeiten: 700 ms aus (Sinus), 500 ms leere Wand
  mit Farbwechsel, 900 ms ein, Text ab 60 % des Einblendens. Zeilen, die gleich bleiben
  (Künstler, Album, Zeitzeile), bewegen sich nicht.
- **Glühlampe statt Rost:** warme Farbtöne werden zu wenig gesättigtem Glühlampenlicht
  (Farbton ≥ 64°, Chroma ≤ 0,045); gedimmtes Licht verliert zusätzlich Farbe (Chroma fällt
  mit dem Quadrat der Helligkeit).
- **Sandfiguren ohne Drehsinn:** beide Schwingungsmoden teilen eine Achse, jede Figur ist
  spiegelsymmetrisch – keine Figur kann wie ein Hakenkreuz wirken (eine tat es; ein Test
  sichert das ab). Die Platte hängt frei an der Wand, mit rundem Licht.
- **Kleine Cover im Passepartout:** dunkler Karton mit heller Schnittkante füllt den Platz,
  das Bild bleibt in ehrlicher Größe; das Raster hält.
- **Stimmen der Textspalte:** Album, Uhr/Zeitzeile und „als Nächstes“ in drei leiseren Stufen;
  nachts etwas dunklerer Text (bei 4,5:1 Kontrast als Untergrenze).
- **Ruhebildschirm:** das Lampenlicht hängt über der Uhr und zieht mit ihr um; nachts ist die
  Wand schwarz. Uhr ohne führende Null („2:13“).
- **Texte:** „Home Assistant kennt „…“ nicht.“, „Zugangstoken“ wie in Home Assistant, kein
  Umbruch in „Home Assistant“ und in kurzen Klammern, feinere Größenstufen für lange Titel.

Weiterhin bewusst nicht übernommen: eigene Serifenschriften für Japanisch und Arabisch (mehrere
MB; auf dem Gerät greifen die System-Noto-Schriften).

## 19. Fünfte Runde: ein Lichtkegel statt eines Rahmens

Die vierte Kritik maß das Licht nach: am Bild 1,8 %, dann ein flaches Band 30–60 px weit,
dann ein steiler Abfall – der Umriss lief parallel zum Bild mit abgerundeter Ecke, wie ein
Box-Shadow. Umgesetzt:

- **Eine Lampe mit rundem Lichtkegel.** Die Lampe steht 0,2 × Bildgröße innerhalb der
  Sonnenseite hinter dem Bild und wirft einen Lichtfleck I = (1 − (r/R)²)³ mit
  R = 0,6 × Bildgröße, entlang der Kante 1,25-fach gestreckt: am Bildrand am hellsten, 50 dp
  weiter etwa 40 % davon, nach 128 dp nichts mehr; die abgewandte Seite bleibt dunkel. Pro
  Frame wird nur die lokale Matrix des Sprites gesetzt (verschieben, drehen, strecken); die
  Shader entstehen einmal pro Szene.
- **Leere Wand ohne Kachel.** Beim Wechsel wird die Lampe auf 32 % heruntergedreht; die leere
  Wand zeigt nur den runden Lichtkegel (Spitze ≤ 2,5 %), keine Scheibe, keinen Umriss.
- **Rand zum Bildschirm als langer Verlauf.** Das Licht läuft zwischen 108 und 16 dp vor dem
  Bildschirmrand aus. Der frühere kurze Verlauf (84 → 36 dp) schnitt den größeren Lichtkegel
  oben gerade ab.
- **Dunkle Cover verlieren keine Kanten:** ein feiner Lichtsaum rundum (mindestens 0,8 %,
  doppelte Randhelligkeit des Covers, höchstens 1,5 %, nach etwa 20 dp vorbei). Er wird mit
  „Lighten“ gezeichnet, nicht addiert: Summen von sRGB-Werten überschießen – addiert hätte er
  die Sonnenseite auf 11 % statt 5,5 % gebracht.
- **Lichtfarbe:** warme Cover ≈ 3300 K (Chroma/Helligkeit wie rgb(76, 55, 33), Farbton
  64–72°), neutrale warmweiß, kühle zurückhaltend (Chroma/Helligkeit ≤ 0,1).
- **Seitenwechsel ohne Schwarz:** muss die Uhr umziehen, bleibt sie gedimmt (60 %) an ihrem
  Platz, bis das neue Licht kommt, und ist weg, bevor das neue Bild dort erscheint. Das alte
  und das neue Licht überlappen sich ein wenig.
- **Zeilen einzeln vergleichen:** das Album des nächsten Titels bleibt stehen, auch wenn der
  Titel einen Gastkünstler hat; eine Zeile bleibt nur, wenn Text, Platz und Farbe gleich sind.
  Beim selben Titel in neuem Licht blendet der alte Text oben aus, statt zu springen.
- **Klangplatten als gewellte Ringe:** statt Durchmessern (Kreuze, Räder, Rosetten) nur
  Knotenkreise, die eine zweite Schwingung mit drei bis sechs Bögen sanft wellt; der Sand
  dünnt am Rand aus, keine Linie endet abrupt an der Kante.
- **Typografie:** der Trennpunkt neben arabischem Text sitzt etwas höher (sonst liest er sich
  als Satzpunkt); „Auf der Seite“ bricht nicht nach „Auf“ um.

## 20. Sechste Runde: der Lichtkegel passt ins Raster

Die fünfte Kritik bestätigte leere Wand, stehende Albumzeile und Lichtfarbe, fand aber den
Lichtkegel zu groß für das Raster: zwischen Bild und schwarzem Randstreifen liegen nur 72 dp
Wand. Bei Covern auf der linken Seite schnitt der Rand den Kegel ab, die Höhenlinien wurden zu
abgerundeten Rechtecken – wieder der Box-Shadow. Umgesetzt:

- **Kleinerer Kegel, Abfall in Helligkeit:** Lampe 40 dp innerhalb der Kante, R = 110 dp; die
  empfundene Helligkeit fällt wie (1 − (r/R)²)^1,5 (das Sprite speichert sie direkt als
  sRGB-Wert): an der Kante am hellsten, 25 dp weiter etwa halbe Leuchtdichte, nach 70 dp ohne
  Knick vorbei. Ein Abfall in linearem Licht fällt in Helligkeit wie eine Gerade und wirkt
  wie eine unscharfe Scheibe.
- **Randstreifen wieder 36 dp, wirklich schwarz:** Nahe einem Außenrand rückt die Lampe tiefer
  hinter das Bild (dort etwas dunkler), statt abgeschnitten zu werden; an Ecken wird der Kegel
  nicht gestreckt. Ein neuer Test prüft die gerenderten Pixel aller Demo-Szenen am Anfang, in
  der Mitte und am Ende des Titels: im Randstreifen ist jeder Wert 0.
- **Saum in der Farbe der Lampe** (halbe Sättigung) und in Helligkeit über 20 dp auslaufend,
  damit er nicht als zweites Licht wirkt.
- **Kühles Licht** wird in OKLab zu 40 % zum warmweißen Lampenlicht gezogen (abends weiter),
  mit wenig Chroma – ein Hauch der Coverfarbe statt LED-Leiste. Der Weg führt über Neutral,
  nie über Grün.
- **Nachts gewinnt das Cover kaum Farbe:** höchstens +0,004 Chroma Richtung Bernstein statt
  eines olivgrünen Stichs.
- **Uhr beim Seitenwechsel:** alte und neue Uhr blenden gleichzeitig über, zusammen nie unter
  60 %.
- **Klangplatten:** zwei oder drei Knotenkreise innerhalb von 68 % des Radius, drei- bis
  achtzählig gewellt, der Anteil der zweiten Schwingung so begrenzt, dass sich keine Kreise
  berühren; zwischen 82 und 90 % des Radius dünnt der Sand auf null aus. Kein einzelner Kreis
  und keine zweizählige Welle (beides liest sich als „O“).
- **Passepartout:** bleibt dunkel (ein helles wäre die größte statische helle Fläche auf dem
  OLED, siehe Abschnitt 4), bekommt aber eine feine Körnung und eine 1 dp breite weiße
  Schnittkante – ein Museums-Passepartout statt einer leeren Fläche.
- **Typografie:** lange Satzbezeichnungen dürfen eine Größenstufe kleiner werden, wenn sie dann
  nur nach den Gedankenstrichen umbrechen; „den Code“ bleibt zusammen.

## 21. Siebte Runde: Farbe statt Dunst, Licht nur, wo es hingehört

Die sechste Kritik bestätigte den Lichtkegel im Raster, den schwarzen Randstreifen, die Uhr
beim Seitenwechsel und die Nachtfarbe. Umgesetzt wurde der Rest:

- **Kühle Cover:** Die Mischung zum warmweißen Lampenlicht ergab grauen oder olivfarbenen
  Dunst. Jetzt behalten kühle Cover tagsüber ihren eigenen Farbton bei fester, kleiner Sättigung
  (Chroma 0,03 bei der Referenzhelligkeit, an der Bildkante etwa 0,017); abends wird ihr Licht
  über eine kurze Strecke der Dämmerung zu Glühlampenlicht. Gelbgrüne Töne (95–150°) bekommen
  gleich warmweißes Licht – als schwaches Licht wirken sie olivgrün.
- **Saum statt Glow:** nur noch 8 dp breit, mit eckigen Ecken wie das Bild, 0,8 % auf der Seite
  der Lampe, zur abgewandten Seite bis 0,35 % auslaufend.
- **Kein Pauspapier:** Ein verblassendes Bild verdeckt die Lampe, solange es zu mehr als 12 %
  deckt; beim Titelwechsel ist die Lampe außerdem auf 22 % gedimmt, die leere Wand zeigt nur
  einen Hauch Licht (≈ 1,6 %).
- **Klangplatten:** drei bis sechs runde Bögen (Höhe ≤ 1,5 r/(n² − 1), sonst entstehen Spitzen),
  äußerster Ring bei 64 % des Radius, Sand nur innerhalb von 74–84 %.
- **Abendtext** nimmt den Farbton der Lampe an statt den des Covers (ein Rosa unter
  Bernsteinlicht wirkt pink). Weiße Cover enden bei etwa L* 88, damit sie den Raum nicht
  aufhellen und die Helligkeitsbegrenzung des Fernsehers nicht anspringt (nicht auf dem Gerät
  geprüft).
- **Passepartout** optisch zentriert (oben etwas weniger Karton als unten).
- **Typografie:** Artikel und Adjektive bleiben bei ihrem Nomen („das Zugangstoken“, „ein neues
  langlebiges Zugangstoken“); eine „Als Nächstes“-Zeile ganz in Arabisch läuft von rechts nach
  links, Titel zuerst.

Damit endet die Reihe der Kritikrunden vorerst: Die verbleibenden Fragen (wie das OLED die
Stufen knapp über Schwarz zeigt, Bildrate, Helligkeitsbegrenzung) lassen sich nur auf dem
echten Gerät beantworten.


## 22. Nach dem ersten Test: Gruppe, Einstellungen, Lautstärke, Releases

Rückmeldung vom Gerät: Klangbild soll zeigen, ob der Wohnzimmer-Lautsprecher allein oder
mit allen anderen spielt, sich per Fernbedienung einstellen lassen und als normales Release
erscheinen.

- **Lautsprechergruppe in der Datumszeile.** Die Uhr über dem Titel wird zur Zeile wie über
  einem Zeitungsartikel: „21:14 · Wohnzimmer + 5“. Das Muster „Raum + Anzahl“ ist das von
  Sonos und Spotify und damit sofort lesbar; bei genau einem weiteren Lautsprecher stehen beide
  Namen da („Wohnzimmer + Küche“). Kein Chip, kein Symbol: dieselben gesperrten Versalien wie
  die Uhr, dieselbe leise Farbe. Spielt der Lautsprecher allein, steht nichts da – der
  Fernseher hängt ja in diesem Raum. Die Gruppe kommt aus `group_members` (Home Assistant
  listet dort alle Mitglieder samt Leiter, auf jedem Mitglied gleich); die Namen der anderen
  liefert ein zweites `subscribe_entities`-Abo, das ausgetauscht wird, wenn sich die Gruppe
  ändert. Gerätewörter („Lautsprecher“, „Sonos One“, „(MA)“) fallen aus den Namen. Ändert sich
  die Gruppe, blendet nur dieser Teil der Zeile über; die Uhr bleibt stehen. Die Mitglieder
  eines *Gruppen-Players* von Music Assistant nennen einander nicht in `group_members`; sie
  spielen aber aus dessen Warteschlange, und `get_queue` liefert die aktive Warteschlange mit
  Namen. Weicht der vom eigenen Namen ab, steht der Name der Gruppe da („Erdgeschoss“), wie bei
  Sonos und Roon benannte Gruppen ihren Namen tragen.
- **Lautstärke** erscheint, wenn sie sich ändert – egal wo –, für dreieinhalb Sekunden an der
  Stelle der Restzeit („Lautstärke 35 %“, wie die genaue Zeit nach einem Tastendruck, auch
  nachts). Wer sie ständig sehen will, bekommt sie in die Datumszeile; ein Balken kommt nicht
  in Frage. Pfeil hoch/runter können die Lautstärke des Lautsprechers ändern – aus
  Voreinstellung aus, weil die Tasten sonst die genaue Zeit zeigen und ein versehentlicher
  Druck das ganze Haus lauter machen kann.
- **Einstellungen per Fernbedienung** statt nur QR-Code: ein Bildschirm im Raster der Bühne.
  Links Überschrift und die Erklärung der gewählten Zeile, rechts die Zeilen; die gewählte
  steht immer in der Bildschirmmitte im Licht einer Lampe, die Liste gleitet hindurch und
  verblasst nach oben und unten – kein Rahmen, kein Schalter, keine Bildlaufleiste. Werte sind
  Wörter („Bei Änderung“, „Dezent“). Jede Änderung gilt sofort, es gibt nichts zu bestätigen;
  links/rechts laufen eine Skala entlang und halten an ihren Enden, OK geht im Kreis. Nach einer
  Minute ohne Taste schließt das Menü (OLED). Optionen: Uhrzeit, Restzeit, Als Nächstes, Album,
  Lautsprechergruppe, Lautstärke, Lampe (kräftig 1,45×, normal, dezent 0,5×, aus), Tag und
  Nacht, Musik steuern, hoch/runter, Ruhezeit, danach, Home Assistant (QR-Code), Demo. Bewusst
  nicht: Schriftgrößen, Farben, Layout-Varianten – die gestalterischen Entscheidungen bleiben
  Entscheidungen.
- **Link `klangbild://`** für die Android-TV-Remote-Integration (ohne ADB). Ein Link darf nur
  Anzeige-Optionen, Ruhezeit und Demo setzen: Jede Webseite im Browser des Fernsehers könnte
  ihn öffnen, Adresse, Token und Lautsprecher bleiben deshalb ADB und der QR-Seite vorbehalten.
- **Releases:** Ein Tag `vX.Y.Z` (oder ein manueller CI-Lauf mit `release_tag`) erzeugt ein
  normales Release, das GitHub auf der Startseite als „Latest“ zeigt; nur ein Tag mit
  Bindestrich (`v1.1.0-beta.1`) wird eine Vorabversion. Der Versionsname der APK ist dann der
  des Tags („1.0.0“). Ein Release wartet auf den Smoke-Test im Emulator.

## 23. Version 2: das Bild zuerst, ein Balken, eine Schrift

Rückmeldung vom Gerät zu 1.0: Die Sonnenuhr war ein Fehlschlag – das Licht sprang sichtbar
von Stelle zu Stelle, und vor allem verstand niemand, dass es die Zeit zeigt. Das Cover war
niedriger als der Text daneben, obwohl es das schwerste Element ist. Der Demo-Modus ist für
eine private App Ballast. Und Serifenschrift, gesperrte Versalien und Elfenbein auf Schwarz
sahen nach „KI-Vorlage“ aus statt nach einer gestalteten App. Vor der Überarbeitung lagen
zwei Recherchen: wie die besten Now-Playing-Ansichten gebaut sind (Apple Music und tvOS,
Tidal, Spotify auf Google TV, Sonos, Naim, Roon, Plexamp – vermessen an offiziellen
Screenshots) und welche Schrift hochwertig wirkt, ohne nach KI auszusehen.

- **Warum die Sonnenuhr ruckelte:** Die Lage der Lampe wurde nur gelesen, wenn ohnehin neu
  gezeichnet wurde – und das löste ein Fünf-Sekunden-Takt aus. Bei einem Vier-Minuten-Titel
  sind das Sprünge von 5,6°. Die Ursache wäre behebbar gewesen, die Idee aber nicht: Eine
  Zeit, die man erst deuten muss, ersetzt keinen Balken, den jeder ohne Nachdenken liest.
- **Fortschritt als Balken**, wie bei Apple, Sonos, Spotify und tvOS: 4 dp hoch, runde
  Enden, kein Knopf (nichts wird berührt), Spur 16 %, Füllung in der Helligkeit des Künstlers,
  darunter links die gespielte Zeit, rechts die Länge des Titels – so tickt nur eine Zahl
  (eine Restzeit rechts hätte eine zweite ticken lassen). Er wird etwa 30-mal pro Sekunde, im
  Takt des Bildschirms, neu gezeichnet – nur seine eigene Ebene, nichts wird neu
  zusammengesetzt; die Zahlen ändern sich einmal pro Sekunde. Sprünge über 1,5 s (Spulen)
  gleiten in 350 ms, kleine Korrekturen verschwinden wie bisher weich. Pausiert wird die
  Füllung dunkler. Die Zeit in Worten entfällt.
- **Ziffern:** Jede Ziffer steht in einer Zelle so breit wie die breiteste und mittig darin
  (über die Laufweite, die Android gleichmäßig links und rechts verteilt) – Monas eigene
  Tabellenziffern kommen nicht in Frage (durchgestrichene Null). Der Doppelpunkt wird um
  0,095 em angehoben; auf der Grundlinie hängt er unter der Mitte der Ziffern. So verrutscht
  weder die laufende Zeit noch die Uhr.
- **Raster v2.** Das Cover misst 368 dp, zwei Drittel der Höhe – mehr als bei allen
  Referenzen (43–56 %), weil das Bild hier das Objekt ist. Der Textblock darf höchstens 62 %
  der Coverhöhe einnehmen, gemessen von der Versalhöhe der ersten bis zur Grundlinie der
  letzten Zeile (nicht an den Textkästen), und steht mittig neben dem Cover. Die Statuszeile
  hängt mit ihrer Versalhöhe an der Oberkante, „Als Nächstes“ steht mit der Grundlinie auf der
  Unterkante; alle Zeilen beginnen auf einer linken Kante. Die ganze Gruppe sitzt 8 dp über
  der Mitte – die optische Mitte, aus demselben Grund, aus dem ein Passepartout unten breiter
  ist. Abstände: 96 dp links, 64 dp zwischen Bild und Spalte, Spalte 352 dp, 80 dp rechts.
  Das Spiegeln je Album entfällt: Ein festes Raster ist ruhiger, gegen Einbrennen genügt die
  langsame Drift der ganzen Komposition.
- **Eine Schrift: Mona Sans** (GitHubs variable Grotesk, Release 2.027, SIL OFL, unverändert
  eingebunden). Newsreader steht auf Anthropics eigener Liste „unverwechselbarer“ Schriften
  und ist dadurch zum Erkennungszeichen von KI-Oberflächen geworden; gesperrte
  Versalien-Etiketten, große Kursive und Elfenbein auf Schwarz gelten Gestaltern inzwischen als
  typische Merkmale. Die vermessenen Produkte setzen ihre Oberflächen ausnahmslos in einer
  Grotesk im normalen Satz und stufen über Größe, Gewicht und Helligkeit ab. Mona Sans hat
  als einzige der geprüften freien Schriften eine Achse für optische Größen (wie Apples SF
  Text und Display): Große Titel bekommen den engeren Display-Schnitt, kleine Zeilen den
  offeneren Text-Schnitt. Dazu eine Breitenachse – sehr lange Titel werden erst schmaler,
  dann kleiner – und ein vollständiger deutscher Zeichensatz. Abgewogen: Schibsted Grotesk
  (keine optischen Größen), TikTok Sans (zu bekannt aus Videos), Zalando Sans (Werbeschrift
  eines Händlers), Host Grotesk; ausgeschlossen: Inter, Geist und Instrument Sans (die
  Standardschriften generierter Oberflächen) und Google Sans Flex (die Stimme von Google TV
  selbst). Keine Tabellenziffern: Monas Tabellen-Null ist durchgestrichen und die Eins hat
  einen Fuß – Gewohnheiten einer Code-Schrift. Mona fehlen schmale Leerzeichen (U+202F,
  U+2009, U+2002); wo vorher eines stand, steht ein geschütztes Leerzeichen.
- **Satz:** Ein Wort aus ein bis drei Buchstaben bleibt im Titel beim folgenden Wort
  („Spiegel / im Spiegel“, nie „Spiegel im / Spiegel“); passt „Als Nächstes“ mit Künstler
  nicht in die Zeile, steht nur der Titel da, statt den Künstler abzuschneiden.
- **Schriftgrößen:** Titel 44 oder 40 sp in höchstens zwei Zeilen (Gewicht 440), sonst 34,
  30, 26 sp in drei Zeilen (450–460). Erst wenn auch das nicht reicht, wird der ganze Block
  schmaler (Breite 87,5) – nie eine einzelne Zeile, sonst sähe er nach zwei Schriften aus.
  Japanische, chinesische und koreanische Titel stehen 12 % kleiner (ihre Zeichen sind höher
  als Versalien). Eine Version („Radio Edit“) ist eine leise Unterzeile unter dem Titel statt
  eines Anhangs in Titelgröße. Künstler 21 sp, Gewicht 520; Album 17 sp; Zeiten,
  Statuszeile, „Als Nächstes“ 15 sp. Der große, leichte Titel und der kleinere, etwas
  kräftigere Künstler halten sich die Waage. Nichts unter 15 sp. Große Zeilen rücken um
  ihren größeren Vorbreitenabstand nach links, damit alle Zeilen optisch auf einer Kante
  stehen.
- **Textfarben** fast neutral – ein Hauch des Coverfarbtons (Chroma ≤ 0,009), abends etwas
  wärmer – in drei Stufen: Titel; Künstler, Uhr und Füllung des Balkens; alles andere (wie
  Weiß zu 100 / 80 / 62 % auf Schwarz). Feinere Stufen nebeneinander sehen nach Versehen aus,
  nicht nach Ordnung. Nie reines Weiß.
- **Licht v2:** Statt einer Lampe hinter dem Bild leuchtet das Bild selbst, in den Farben
  seiner Ränder, als enger Hof – auf allen Seiten gleich. Jeder Punkt der Wand nimmt die
  Farbe des nächsten Randpunkts, gemittelt über eine Strecke des Randes, die mit der
  Entfernung wächst (wie diffuses Licht – ohne diese Mittelung zeichneten sich Streifen ab).
  Am Rand höchstens 0,022 relative Leuchtdichte (≈ L* 17) und nie mehr als 40 % der
  Leuchtdichte des Randes selbst: Ein Bild wirft höchstens so viel Licht, wie es hell ist –
  ein dunkles Cover fast keins, ein kleines im Passepartout nur das des dunklen Kartons.
  Dann fällt es als Gauß-Kurve mit σ = 26 dp und ist vor der Textspalte verschwunden, die
  Schrift steht auf Schwarz. Das Licht reicht 2 dp unter die Kante des Bildes, damit dessen
  geglätteter Rand nie dunkel umrandet wirkt. Am Bildschirmrand bleibt es schwarz –
  eingeblendet als Produkt einer waagerechten und einer senkrechten Rampe (das Minimum der
  Randabstände knickte sichtbar entlang der Diagonalen, wie eine Gehrung). Eine erste Fassung
  (heller, σ = 0,19 × Bildgröße) wirkte wie ein grauer Rahmen: zur Seite der Textspalte weit,
  zu den nahen Bildschirmrändern abgeschnitten. Eine Bitmap je Szene, gezeichnet als ein
  Rechteck, gedimmt über eine Farbmatrix – pro Frame passiert nichts.
- **Abgerundete Ecken:** 8 dp, gut 2 % der Seitenlänge (gemessen bei Apple, Tidal, Sonos:
  1,7–3,2 %).
- **Bewegung:** Ein Titelwechsel (1,25 s) geht kurz durchs Dunkle, statt zwei Bilder
  übereinanderzublenden: Das alte Cover verlischt in 320 ms, das neue kommt von 300 bis
  900 ms und setzt sich aus 1,5 % Übergröße, das Licht wechselt über 900 ms in die neuen
  Farben; die alten Zeilen gehen in 200 ms, die neuen steigen ab 560 ms nacheinander an ihren
  Platz. Eine Pause dimmt Licht, Bild und Text (600 ms) – nichts bewegt sich, damit die an
  den Kanten des Bildes hängenden Zeilen bündig bleiben. Stumm ist nur ein Wort,
  Verbindungsverlust dimmt wie eine Pause.
- **Statuszeile** „21:47 · Wohnzimmer + 5“ oben links in der Spalte, in normaler
  Schreibweise; erst der Ort, dann der Zustand: „Wohnzimmer · Pausiert“, „Küche · Stumm“, für
  ein paar Sekunden die Lautstärke.
- **Demo entfernt.** Die erzeugten Testcover leben nur noch in den Tests. Der Smoke-Test im
  Emulator spricht stattdessen mit einem kleinen Ersatz-Home-Assistant (`ci/fake_ha.py`) –
  das prüft den echten Weg der Daten statt einer Attrappe.
- **Alltag** (aus [`IDEEN.md`](IDEEN.md)): „Überall“, wenn alle Lautsprecher spielen; „Stumm“
  in der Statuszeile; ein Tastendruck blendet Ausgeblendetes kurz ein. Was dort sonst steht,
  braucht Entitäten aus dem eigenen Home Assistant oder Messungen am Gerät und bleibt
  vorerst Idee (Begründung in `IDEEN.md`).
- **Unabhängige Kritik.** Ein Prüfer, der nur die Bilder kannte, bestätigte Raster,
  Mittelung und Satz und benannte, was den „Wow“-Eindruck verhinderte: das Licht als grauer
  Rahmen, das Doppelbild beim Titelwechsel, zitternde Zahlen, zu viele Graustufen, gemischte
  Laufweiten, das Zurückweichen bei Pause, das die Kanten-Zeilen verrutschen ließ. Alles davon
  ist oben eingearbeitet, dazu: ein kleinerer, weicherer Lichtstreifen hinter der gewählten
  Menüzeile, der nur sie beleuchtet, und ein Satz statt Etikett, wenn Home Assistant nicht
  erreichbar ist.

## 24. Version 3: Jost und DM Sans statt Mona Sans

Rückmeldung zu 2.0: Mona Sans sah aus wie eine zwanzig Jahre alte Systemschrift. Gesucht war
eine wirklich schöne Schrift – unabhängig recherchiert, nicht aus einer Vorlage abgeleitet.

- **Vorgehen:** 43 frei lizenzierte Schriften mit den echten Zeilen der App in TV-Größen
  gesetzt; zehn davon in der Bühne, hell und dunkel; große Detailbögen; fünf Paarungen.
  Zuletzt eine Blindkritik durch einen unabhängigen Prüfer, der die Namen nicht kannte.
- **Ausgeschlossen:** Fontshare-Schriften (ihre Lizenz verbietet die Weitergabe über
  Repositorys und jede Veränderung) – nur SIL OFL; Google Sans, die Stimme von Google TV
  selbst. Die neutralen Grotesken (Inter, Geist, Fixel, Schibsted, Public Sans, Onest,
  Golos, Hanken, Libre Franklin, Mona …) sehen einander zum Verwechseln ähnlich: kompetent,
  aber genau der „Standard“-Eindruck, der kritisiert wurde.
- **Gewählt, Platz 1 der Blindkritik:** **Jost** (Owen Earl, indestructible type*, OFL,
  v3.710) – eine geometrische Grotesk in der Linie der Futura – für Titel ab 24 sp, die
  große Uhr, Überschriften und alle Ziffern; **DM Sans** (Colophon Foundry, OFL, v4.004, mit
  optischen Größen 9–40) für alles bis 21 sp. Das klassische Prinzip aus Auszeichnungs- und
  Textschrift (wie Futura und Avenir). Beide haben eine Versalhöhe von 0,70 em, die
  Ausrichtungen des Rasters an Versalhöhen gelten also über beide hinweg. Jost allein fiel
  durch (kleine Zeilen zu schwach, x-Höhe 0,46), DM Sans allein wirkte wie die
  Web-Vorlage von heute.
- **Feinschliff aus der Kritik:** Titel 400 auf zwei Zeilen, 420–430 ab drei; Laufweite −1 %
  bei 44/40 sp, −0,5 % bei 34/30, 0 darunter; Zeilenabstand 1,13–1,16 (ein Umlaut stößt
  nicht an die Unterlänge darüber); Künstler 21/500, Album 17/400, 15-sp-Zeilen 440 mit
  +1,5 %. Die Versionszeile steht in DM Sans – Jost nie unter 24 sp außer bei Ziffern.
- **Ziffern:** DM Sans hat keine Tabellenziffern und eine sehr schmale Eins (0,34 gegen
  0,66 em) – in festen Zellen klafften Lücken („4:1 1“). Josts Ziffern sind gleichmäßig
  (0,45–0,60 em): Zeiten stehen in Jost in festen Zellen, der Doppelpunkt um 0,123 em
  angehoben (DM Sans 0,10 em). Die große Uhr im Ruhebildschirm (132 sp) behält ihre
  natürlichen Ziffern; jede neue Minute blendet weich über (700 ms), damit die zentrierte
  Zeile nie springt.
- **Folgen:** Die schmale Ersatzbreite entfällt (keine Breitenachse); die Titelleiter endet
  bei 24 sp auf vier Zeilen. Zeichenabdeckung: Jost Latin Ext-A 99 % und Kyrillisch, DM Sans
  Latin Ext-A 88 % (es fehlen nur Esperanto-Buchstaben und Raritäten). Die APK ist rund 1 MB
  kleiner.

## 25. Hell am Tag, dunkel am Abend

Gewünscht: tagsüber hell, abends und nachts dunkel – automatisch, in den Einstellungen
festlegbar. Gewählt wurde die einfachste robuste und übliche Regel.

- **Automatisch folgt der Sonne** – die Regel, nach der auch Android und Apple TV ihr dunkles
  Design schalten: hell von Sonnenaufgang bis Sonnenuntergang (Elevation −0,833°) laut
  `sun.sun` aus Home Assistant, ohne diese Entität nach der Uhr (eine Näherung der
  Sonnenhöhe aus Tageszeit). Ein halbes Grad Hysterese verhindert Flackern in der Dämmerung.
  Einstellung *Erscheinungsbild*: Automatisch (Standard), Hell, Dunkel.
- **Die helle Wand ist warmes Papier** (Oklab L 0,915), nie Weiß, mit einem Hauch der
  Coverfarbe – zum Warmen gezogen, damit ein rotes Cover Pfirsich gibt statt Rosa; das
  *Wandlicht* stellt ein, wie viel (Aus = reines Papier). Statt des Lichts wirft das Bild
  einen weichen Schatten (10 dp Versatz, σ 16 dp, höchstens 20 % dunkler, dazu ein feiner
  Kontaktschatten) – wieder eine Bitmap je Szene.
- **Schrift** in denselben drei Stufen, gespiegelt (Oklab L 0,24 / 0,37 / 0,49); auch die
  leiseste hat auf Papier mindestens 4,5 : 1. Ein weißes Cover bleibt eine Spur weißer als
  das Papier und bekommt eine dunkle Haarlinie; kleine Cover liegen in einem elfenbeinfarbenen
  Passepartout statt einem schwarzen; eine Pause dimmt das Bild weniger (es würde sonst
  schmutzig).
- **Der Wechsel** blendet die ganze Bühne in 2,4 s über, wie das Licht draußen; andere
  Bildschirme gehen durchs Dunkle. Ruhe, Menü (sein Lichtband wird ein etwas weißeres Band)
  und Einrichtung folgen der Wand.

## 26. Cover Flow

Die große Aufgabe dieser Runde: eine zweite Ansicht wie das Cover Flow aus iTunes – das
aktuelle Cover in der Mitte, links die vorherigen, rechts die nächsten, gedreht im Raum, mit
Spiegelung auf glänzendem Boden –, mit sehr sanften Übergängen, bis zu den Randfällen
durchdacht. Einstellung *Ansicht*: Einzelbild (Standard) oder Cover Flow.

- **Geometrie** aus dem Entwurf vermessen: das mittlere Cover 300 dp (die Mitte 272 dp vom
  linken Rand), zwei Karten je Seite, um 60° zur Mitte gedreht, ihre Außenkanten 222,5 und
  255 dp neben der Mitte, in 92 % der Höhe – alle in *einer* gemeinsamen Perspektive. Die
  Textspalte beginnt bei 570 dp; alle Regeln des Einzelbilds gelten weiter. Die Lage einer
  Karte ist eine einzige Zahl (0 = Mitte, ±1, ±2 …); daraus folgen pro Frame vier Ecken und
  ein texturiertes Viereck (`Matrix.setPolyToPoly`) – nichts wird gemessen oder angelegt.
- **Karten als fertige Texturen:** Bild, runde Ecken und Spiegelung sind in eine Bitmap
  gebacken, so ist jede Karte ein einziges Viereck, ohne Offscreen-Ebenen. Android zeichnet
  Bitmaps ohne Mipmaps; eine weit gedrehte Karte würde in Bewegung flimmern – darum gibt es
  jede Karte auch in halber Auflösung, und ab etwa zwei Dritteln Stauchung wird überblendet.
- **Licht:** Im Dunkeln ein breiter, schwacher Lichtfleck über den Karten in der Farbe des
  aktuellen Covers, vor der Textspalte verschwunden, der Boden bleibt schwarz, damit die
  Spiegelungen stehen. Auf Papier werfen die Karten weiche Schatten, und was zur Seite steht,
  tritt ein wenig ins Papier zurück – wie Dinge, die weiter vom Fenster stehen. Nachts treten
  die Seitenkarten ganz ins Dunkel zurück.
- **Bewegung:** Kommt ein anderes Bild in die Mitte, dreht sich die Reihe in 1,3 s weiter –
  sanfter Anfang, eine ruhige Mitte (höchstens doppelt so schnell wie im Schnitt), langes
  weiches Ankommen. Eine Karte, die die Mitte verlässt, schwenkt früh zur Seite – drehen und
  ausweichen zugleich –, so sind zwei Karten, die sich in der Mitte begegnen, schon
  auseinander, wenn sie aneinander vorbeiziehen: Sie schneiden sich nie, und die
  Zeichenreihenfolge darf einfach wechseln (ein Test prüft das über die ganze Bewegung). Die
  alten Zeilen gehen zuerst, die neuen steigen ab 620 ms, wenn das neue Cover ankommt.
- **Kein Bild steht je halb durchsichtig vor einem anderen:** Neue Karten rollen deckend von
  jenseits des Reihenendes herein, im Verband; Karten, die nicht mehr gebraucht werden,
  lösen sich auf, bevor etwas an ihnen vorbeizieht. Ein Sprung zu etwas Neuem rollt das neue
  Cover vom Ende herein; wird ein Titel dazwischengeschoben oder die Warteschlange
  umgestellt, wird die kommende Seite als Ganzes ersetzt.
- **Ein Wechsel nach dem anderen:** Kommt während einer Bewegung schon der nächste Titel
  (schnelles Weiterschalten), landet die laufende Bewegung zuerst, dann geht es direkt zum
  neuesten – keine abgebrochene Bewegung, kein Sprung. Das Einzelbild macht es jetzt genauso.
- **Die Reihe** (`CoverStrip`) kennt Bilder, nicht Songs: Die Lieder eines Albums sind eine
  Karte, ein Radiosender auch. Jede Karte behält ihre Identität, solange sie in der Reihe
  steht – so wird jede Änderung Karte für Karte animiert: Die nächste Karte kommt aus der
  rechten Reihe, ein Schritt zurück holt die linke, alles andere ist eine neue Karte. Solange
  die Warteschlange zum neuen Titel noch nicht bekannt ist, bleibt rechts stehen, was dort
  stand. Ein anderer Lautsprecher beginnt eine neue Reihe.
- **Szenen** werden außerhalb des Hauptthreads vorbereitet (die Mitte zuerst, dann was kommt,
  dann was war) und zwischengespeichert. Ändert sich nur das Tageslicht, behalten die Karten
  ihre Texturen; ändert sich die Wand (hell ↔ dunkel), wechselt die ganze Reihe auf einmal,
  sobald jede Karte bereit ist – nie eine Reihe aus zwei Welten.

## 27. Music Assistant direkt (freiwillig)

Home Assistant reicht von Music Assistants Warteschlange nur den aktuellen und den nächsten
Titel weiter (`music_assistant.get_queue`). Für Cover Flow – zwei Karten rechts, die
Vorgeschichte links – braucht es die ganze Warteschlange, und die kennt nur Music Assistant
selbst.

- **Freiwillig:** Ohne Music Assistant funktioniert alles wie bisher (rechts der nächste
  Titel, links merkt sich Klangbild, was lief). Mit Music Assistant: zwei Karten rechts,
  echte Vorgeschichte links ab dem ersten Titel, dazu das Erscheinungsjahr des Albums, das
  Home Assistant nicht weitergibt.
- **Weg:** Music Assistants eigene API (`POST <adresse>/api`, Befehl
  `player_queues/items`), mit einem langlebigen Zugangstoken – seit Version 2.7 ist eine
  Anmeldung Pflicht; Home Assistants eigene Zugangsdaten taugen dafür nicht. Gelesen werden
  40 Titel vor und nach dem aktuellen (genug, um auch hinter einem Album mit zwanzig Liedern
  das nächste zu finden), immer wenn Home Assistants Warteschlange sich ändert, und nur,
  wenn der aktuelle Titel dort derselbe ist, den Home Assistant meldet. Bilder kommen über
  Music Assistants Bildproxy (ohne Anmeldung) oder, wenn öffentlich, direkt vom Dienst.
- **Einrichtung** auf der Einrichtungsseite: Die Adresse errät Klangbild aus den Bildlinks
  (`…:8095/imageproxy/…`), das Token kommt aus dem eigenen Profil in Music Assistant; beides
  wird vor dem Speichern geprüft. Im Menü zeigt *Verbindung → Music Assistant* den Zustand.

## 28. Aufräumen, Leistung, Sicherheit

- **Ein Token geht nur an die Adresse, für die es gemacht ist.** Ändert sich die Adresse
  (auf der Einrichtungsseite oder per `am start`-Extra) ohne neues Token, wird das alte
  verworfen. Vorher hätte jede andere App auf dem Fernseher die Startaktivität mit einer
  eigenen `ha_url` aufrufen und so das gespeicherte Token an sich schicken lassen können.
- **Cover mit Grenzen:** Ein Download über 16 MB wird nicht gelesen; wie seltsam ein Bild
  auch geformt ist, im Speicher wird es nie größer als ein Quadrat von 2048 px. Antworten von
  Music Assistant sind auf 4 MB begrenzt.
- **Klartext nur im Heimnetz – auch nach Weiterleitungen:** Die Prüfung läuft jetzt auch für
  jeden Weiterleitungsschritt, nicht nur für die erste Anfrage.
- **Kein toter Code:** Die statischen Bühnen für Standbilder liegen bei den Tests, die App
  hat nur ihre animierten Bühnen; der Satz der Bühne (Stile, Titelleiter, Zeilenumbrüche)
  steht in einer eigenen Datei.
- **Tests:** 212, etwa 40 s auf zwei Prozessen; die Bewegungen werden als Bildfolgen geprüft
  (Cover Flow vor, zurück, Sprung, eingeschobener Titel, geänderte Warteschlange, schnelles
  Weiterschalten, Papier).
- **Einrichtungsseite** im Stil der App: eine Schrift (die des Handys), normale Schreibweise,
  keine Kursive, keine gesperrten Versalien; hell oder dunkel, wie das Handy eingestellt ist.

## 29. Kritik an Version 3 – Version 3.0.1

Ein unabhängiger Prüfer, der nur die Bilder und Bildfolgen kannte, fand sieben echte Mängel.
Behoben:

- **Doppelbilder beim Sprung und beim eingeschobenen Titel.** Ursache war auch ein Fehler: Kamen
  mehrere Karten herein, begann die erste nicht hinter dem Reihenende, sondern schon sichtbar
  in der Reihe – über den Karten, die gerade gingen. Jetzt gehen Karten, die Platz machen,
  zuerst (300 ms, die äußeren etwas früher), dann dreht sich die Reihe; neue Karten rollen
  unsichtbar vom Ende herein.
- **Der Sprung stockte.** Die fernen Plätze stehen eng beieinander; gleichmäßig in der Position
  bewegt, kroch eine Karte dort und stürzte dann in die Mitte. Hereinrollende Karten folgen
  jetzt ihrer Mitte auf dem Bildschirm in gleichmäßigem Tempo (`FlowGeometry.rolled`) – sie
  erreicht höchstens 31 dp je 100 ms, kaum mehr als ein gewöhnliches Weiterblättern, und
  durchdringt die gehende Karte nie (in einer Simulation über die ganze Bewegung geprüft).
- **Schnelles Weiterschalten.** Wartet schon ein anderer Titel, beeilt sich der laufende
  Wechsel (höchstens 350 ms Rest), und die Zeilen des übersprungenen Titels erscheinen nicht –
  in beiden Ansichten. Nur ein anderer Titel drängt: Eine Karte, die kurz nach dem Wechsel
  fertig wird und dazukommt, eine neue Warteschlange von Music Assistant oder ein besseres
  Cover warten, bis die Bewegung gelandet ist – sonst würde fast jede Drehung im Cover Flow
  abgekürzt, denn die übernächste Karte kommt meist ein paar hundert Millisekunden später
  (per Test geprüft: zwei Bühnen nebeneinander, eine bekommt die Karte, beide bleiben
  pixelgleich). Zeilen, die beim Beeilen gerade aufstiegen, bleiben stehen und gehen von dort
  aus – nichts springt; führt das Weiterschalten doch zurück zum selben Titel, steigen sie
  sofort zu Ende.
- **Der Lichtschein war eine braune Fläche:** flach, mit geraden Kanten und in der
  Durchschnittsfarbe des Covers (Violett, Pink und Türkis ergaben Rost). Jetzt ein weiches Oval
  über dem mittleren Cover in der Farbe seiner Oberkante, höchstens ≈ L* 8 und mit ihr heller
  oder dunkler – wie im Entwurf ein violetter Schein über dem Synthwave-Cover, über einem fast
  schwarzen Cover nur ein Hauch.
- **Seitenkarten waren zu hell.** Wie im Entwurf auf 55 % und 40 % gedämpft; im Dunkeln tritt
  eine viel hellere Karte neben einem dunklen mittleren Cover zusätzlich zurück, sodass die
  Mitte das schwerste Bild bleibt.
- **Papier:** Keine Spiegelung mehr (Papier glänzt nicht), der Schatten liegt unter der Karte
  statt als grauer Rand darüber, die Karten verblassen am Rand kürzer. Die Papierwand bleibt
  im warmen Bereich (Farbton 50–90°): Ein kühles Cover gibt ihr weniger Farbe, nie Mint.
- **Nachts** war die Schrift khakifarben (viermal so farbig wie am Tag); das Licht des Abends
  färbt sie jetzt höchstens doppelt so stark.
- **Einstellungsmenü:** Sein Lichtband reichte bis an den Bildschirmrand; jetzt endet es vor
  dem schwarzen Randstreifen (getestet).
- **Einrichtungsseite:** Tinte statt Gold, kein Größengriff am Textfeld, keine
  Monospace-Schrift, kein Umbruch mitten im Wort.
- **Neue Zeilen** steigen etwas später (ab 780 ms), wenn sich das neue Cover dem Raum
  zuwendet.

Nicht übernommen: Das Raster des Cover Flow (Textspalte bei 570 statt 528 dp, 50 dp rechts)
folgt dem Entwurf, nicht dem Einzelbild – beim Wechsel der Ansicht geht der Bildschirm ohnehin
durchs Dunkle. Und „Cover Flow“ bleibt der Name der Ansicht: So heißt sie im Wunsch, und so
kennt man sie.
