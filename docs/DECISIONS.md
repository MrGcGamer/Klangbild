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
  die exakte Pixelgröße, Farbanalyse, eine **Graustufen-Lichtkarte** (analytisch aus dem
  Abstand zum Cover, in linearem Licht, mit Dithering gegen Banding) in 960 × 540, gestreckt
  gezeichnet.
- **Licht pro Frame:** ein einziges Rechteck mit `ComposeShader(Lichtkarte × SweepGradient)`
  und einem Farbfilter. Der Sweep-Gradient ist die Sonnenuhr-Maske (wird nur gedreht), der
  Farbfilter die Lichtfarbe (kann beim Titelwechsel durch OKLCH wandern). Das Dithering der
  Karte ist so stark gewählt, dass nach dem Einfärben noch etwa ein Ausgabe-Level Rauschen
  übrig bleibt.
- **Pro Frame** nur Transparenz, Transformationen und Farbmatrizen; animierte Werte werden in
  Draw-/Layer-Lambdas gelesen, sodass Animationen nie neu komponieren.
- **Nichts animiert dauerhaft.** Wenn nichts passiert, wird nichts gezeichnet. Sonnenuhr und
  OLED-Drift ticken alle fünf Sekunden (wenige Pixel), die Zeitzeile ändert sich höchstens
  alle 15 Sekunden. Nur Übergänge (2,6 s) laufen mit voller Bildrate.
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
