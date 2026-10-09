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

Übernommen aus der Kritik:
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
