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

## 7. Ehrliche Auflösung

Ein Cover wird höchstens mit 1,1 Quellpixeln pro 1080p-Pixel gezeigt (bei 4K also maximal
2,2-fach, per Lanczos-3 einmal pro Titel auf die exakte Pixelgröße gebracht). Aus drei
Metern Abstand liegt das unter dem Auflösungsvermögen des Auges. Ein 300-px-Senderlogo
bleibt dadurch klein und scharf statt groß und matschig. Bekannte CDNs (Spotify, Deezer,
Apple, Tidal, Qobuz, Google, Music Assistants Imageproxy) werden nach der größten Variante
gefragt; die Original-URL bleibt Rückfall.
