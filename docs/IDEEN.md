# Klangbild – was es noch besser macht

Eine Bestandsaufnahme nach dem ersten Test auf dem Gerät: woher bei Displays dieser Art das
„Wow“ kommt, wie Klangbild noch selbstverständlicher in den Raum passt, und welche
Anpassungen sinnvoll sind – und welche nicht. Am Ende eine priorisierte Liste.

## Kurzfassung

1. **Das Wichtigste ist unsichtbar:** ruckelfreie Bewegung und saubere Dunkelstufen auf
   dem OLED. Ein einziges Stocken oder ein sichtbarer Farbstreifen im Lichtkegel zerstört
   die Illusion schneller, als jede neue Funktion sie aufbauen kann. Erst messen
   (`--ez debug true`), dann Neues.
2. **Der größte Sprung zum „Es gehört zum Raum“** ist, dass Klangbild auf das echte Licht im
   Zimmer reagiert: Farbtemperatur und Helligkeit der Wohnzimmerlampe oder ein
   Helligkeitssensor aus Home Assistant statt nur der Sonnenstand.
3. **Anwesenheit statt Zeitschaltuhr:** Ist niemand im Raum, wird es dunkel; kommt jemand
   herein, geht zuerst das Licht an, dann erscheint der Text.
4. **Wenige, gute Voreinstellungen schlagen viele Schalter.** Kaum jemand ändert
   Einstellungen. Szenen wie „Nur Bild“ oder „Abend“, umschaltbar auch aus Home Assistant,
   sind wertvoller als weitere Einzeloptionen.
5. **Bewusst nicht:** Lichtorgel, animierte Cover, Benachrichtigungen, Schrift- und
   Farbwahl. Klangbild ist ein Objekt mit einer Haltung, kein Baukasten.

## 1. Woher das „Wow“ kommt

Die Produkte, die in diesem Bereich als hochwertig gelten (Samsung The Frame, Apple TV mit
seinen Luftaufnahmen, Meural, Roon, Plexamp), verbindet weniger ihre Funktionsliste als
einige Haltungen:

- **Sie glauben dem Raum.** The Frame passt Helligkeit und Farbton an das Umgebungslicht
  an, Apples True Tone ebenso, Googles Ambient-Modus dimmt in dunklen Räumen. Ein Bild, das
  heller strahlt als das Zimmer, wirkt wie ein Fernseher; eines, das so hell ist wie ein
  beleuchtetes Bild an der Wand, wirkt wie ein Bild. Der Google TV Streamer hat keinen
  Lichtsensor – die Sensoren im Haus kennt Home Assistant.
- **Sie sind da, wenn jemand da ist.** The Frame schaltet sich mit Bewegungsmelder ab und
  an, Fire TV Omni erwacht, wenn jemand den Raum betritt. Das spart dem OLED Stunden und
  macht das Wiedersehen zum Moment.
- **Sie tun wenig, das aber genau.** Spotify verlangt für seine Canvas-Videos ruhige,
  schnittarme Schleifen ohne Blitze; Apple lässt animierte Cover nur laufen, wenn der
  Bildschirm ganz hell ist. „Calm Technology“ nennt das: in der Peripherie bleiben, auch im
  Fehlerfall funktionieren.
- **Die Farbe kommt aus dem Bild.** Plexamp färbt seinen Hintergrund aus dem Cover;
  Klangbild geht weiter und macht daraus Licht, das sich wie Licht verhält.
- **Lesbar vom Sofa, das Bild groß, kein Diashow-Gewitter.** Die häufigste Bitte in Roons
  Forum: aus der Entfernung lesbar, lieber das Cover groß als ständig wechselnde
  Bandfotos.
- **Verlässlichkeit ist Luxus.** Spotify hat sein Car Thing per Update stillgelegt.
  Klangbild redet nur mit Home Assistant im eigenen Netz, ohne Cloud – das ist ein Wert,
  kein Mangel.

Klangbild erfüllt die meisten davon schon: das Licht aus dem Bild, Typografie mit
Satzregeln, die Choreografie beim Titelwechsel, der OLED-Schutz, die ehrliche Auflösung.
Was fehlt, ist vor allem der Bezug zum echten Raum.

## 2. Ehrliche Bestandsaufnahme

| Bereich | Stand | Was noch fehlt |
|---|---|---|
| Licht und Bild | Licht in den Farben der Coverränder, Tag und Nacht nach dem Sonnenstand | Bezug zum echten Raumlicht; am Gerät geprüfte Dunkelstufen |
| Bewegung | Titelwechsel in gut einer Sekunde, Pause lässt das Bild zurücktreten, gleitender Fortschrittsbalken | Bildrate auf dem Streamer gemessen (bisher nur im Emulator) |
| Text | Eine Schrift mit optischen Größen, Satzregeln, Gruppe und Lautstärke in der Statuszeile | – |
| Zustände | Jeder Zustand gestaltet, auch stumm | Ankunft (jemand betritt den Raum) |
| Bedienung | Einstellungen per Fernbedienung, Home Assistant, ADB, Link | Voreinstellungen (Szenen), Vorschau beim Einstellen |
| Raum | Nachtmodus, Ruhe nach Zeit | Anwesenheit, Licht im Zimmer |

## 3. Wie Klangbild sich noch besser in den Raum fügt

**Raumlicht übernehmen.** Eine Lampe oder ein Helligkeitssensor aus Home Assistant ersetzt
den Sonnenstand als Quelle für Wärme und Helligkeit: Leuchtet die Stehlampe mit 2700 K
gedimmt, wird Klangbild wärmer und dunkler; brennt mittags kein Licht, aber die Sonne
scheint herein, darf das Bild kräftiger sein. Technisch liefern Lampen `color_temp_kelvin`
und `brightness` (nur im Farbtemperatur-Modus; bunte Szenen werden ignoriert),
Helligkeitssensoren Lux. Klangbild kann solche Entitäten seit dieser Version zusätzlich
abonnieren (derselbe Weg wie für die Namen der Gruppen-Lautsprecher). Die Gestaltungsregeln
bleiben dabei Grenzen: Nachtfarben, maximale Helligkeit, schwarzer Rand.

**Anwesenheit.** Ein Präsenzmelder (`binary_sensor`, Klasse `occupancy`) im Wohnzimmer:
Ist der Raum ein paar Minuten leer, zieht sich Klangbild in die Ruhe zurück; betritt jemand
den Raum, geht zuerst das Licht an (wie bei Pause → Spielen), der Text folgt einen
Moment später. Dazu Ruhezeiten, in denen Klangbild nur im Nachtmodus erscheint.

**Das Licht über den Fernseher hinaus.** Optional übernimmt eine echte Lampe im Raum – eine
Hue hinter dem Fernseher, ein LED-Streifen – bei jedem Titelwechsel langsam die Farbe des
Lichts, das das Cover auf die Wand wirft (über Home Assistant, mit Übergangszeit). Kein Ambilight im Takt, sondern
dieselbe ruhige Farbe wie an der virtuellen Wand. Das ist der stärkste „Wow“-Moment, den
Klangbild bieten kann, muss aber ausdrücklich eingeschaltet werden.

**Einstellungen am Fernseher selbst** (nicht am Gerät geprüft, je nach Modell): ein
Bildmodus wie „Kino“ oder „Filmmaker“, dynamischer Kontrast und Rauschfilter aus. Sie
zeigen die feinen Verläufe knapp über Schwarz am ehesten so, wie sie gerechnet sind.

## 4. Anpassbarkeit: was sinnvoll ist

Untersuchungen zu Software-Einstellungen zeigen seit Jahren dasselbe: Nur ein kleiner Teil
der Menschen ändert überhaupt etwas, alle anderen leben mit den Voreinstellungen. Apples
Gestaltungsrichtlinien folgern daraus: gute Voreinstellungen, so wenige Einstellungen wie
möglich, und nichts fragen, was sich erkennen lässt. Googles TV-Richtlinien ergänzen: so
wenige Ebenen wie möglich, nie eine Bestätigung vor *Zurück*. Danach richtet sich das neue
Menü – eine Ebene, Änderungen gelten sofort.

Der nächste sinnvolle Schritt sind **Szenen** über den Einzelschaltern:

| Szene | Was sie tut |
|---|---|
| **Bild und Text** | wie jetzt (Voreinstellung) |
| **Nur Bild** | das Cover in der Mitte, etwas größer; Titel und Künstler erscheinen nur beim Titelwechsel für ein paar Sekunden und auf Tastendruck |
| **Abend** | Wandlicht dezent, ohne Fortschritt und Album |
| **Gäste** | Wandlicht kräftig, „Als Nächstes“ an, auf Wunsch eine Liedtext-Zeile |

Jede Szene ist auch aus Home Assistant schaltbar (Link oder ADB-Extra), damit Automationen
sie mit der Tageszeit oder der Lichtszene wechseln. Die Einzelschalter bleiben zum
Feineinstellen.

**Bewusst nicht anpassbar:** Schriften, Farbschemata, Layout-Varianten, Größe des Covers
(sie folgt der ehrlichen Auflösung). Diese Entscheidungen machen Klangbild aus; jede
Wahlmöglichkeit dort verwässert es.

## 5. Priorisierte Liste

| # | Idee | Wirkung | Aufwand |
|---|---|---|---|
| 1 | Am Gerät messen und feinjustieren: Bildrate, Dunkelstufen, Helligkeit des Lichts | Grundlage für alles „Premium“ | klein |
| 2 | Raumlicht oder Lux-Sensor statt nur Sonnenstand | gehört sichtbar zum Raum | mittel |
| 3 | Anwesenheit und Ruhezeiten, mit Ankunfts-Choreografie (erst Licht, dann Text) | OLED-Schutz, Ankommen als Moment | klein bis mittel |
| 4 | Szenen im Menü und per Link aus Home Assistant | wenige Entscheidungen statt vieler | klein |
| 5 | „Nur Bild“ als eigene Komposition (Cover mittig, Text nur beim Wechsel) | reines Kunstobjekt | mittel |
| 6 | Farbe des Wandlichts auf eine echte Lampe übertragen (optional) | der stärkste Wow-Moment | klein bis mittel |
| 7 | Stummschaltung dimmt wie eine Pause | ehrlicher Zustand | klein |
| 8 | „Überall“, wenn alle Lautsprecher spielen (wie bei Sonos) | sofort verständlich | klein |
| 9 | Schlaftimer in Worten („Die Musik endet in zwanzig Minuten“), das Licht sinkt langsam | sanftes Ende des Abends | mittel – braucht Zugang zu Music Assistant selbst |
| 10 | Eine synchrone Liedtext-Zeile, kursiv, langsam überblendet, nur auf Wunsch | Abende mit Gästen | mittel bis groß – Music Assistant liefert Liedtexte, Home Assistant reicht sie nicht weiter |
| 11 | Vorschau im Menü: die Bühne bleibt bei Licht-Einstellungen sichtbar | Einstellen ohne Raten | mittel |
| 12 | Klangbild als eigener Anzeige-Teilnehmer in Music Assistant (Sendspin) | millisekundengenauer Fortschritt, Gruppen direkt | groß, Protokoll noch in Vorschau |

**Nicht empfohlen:** Visualisierungen im Takt der Musik (Music Assistant hat dafür eigene
Wege, keines der Kunstobjekt-Produkte reagiert auf Ton), animierte Cover (liefert Music
Assistant nicht), helle Passepartouts (auf dem OLED die größte statische helle Fläche),
Benachrichtigungen, wechselnde Künstlerfotos.

## 6. Was Version 1.0.0 davon umgesetzt hat

- Lautsprechergruppe neben der Uhrzeit: „Wohnzimmer + 5“, bei zwei Lautsprechern beide
  Namen, ein Gruppen-Player von Music Assistant mit seinem eigenen Namen.
- Lautstärke bei Änderung (egal wo), auf Wunsch ständig; Pfeil hoch/runter als
  Lautstärketasten (optional).
- Einstellungen per Fernbedienung, jede Zeile einzeln, das Licht in vier Stufen.
- Öffnen aus Home Assistant per `select_source`, per Link (`klangbild://open?…`) und per ADB,
  alle Anzeige-Optionen auch von außen setzbar – die Grundlage für Szenen aus Automationen.
- Zusätzliche Abos auf weitere Entitäten – die Grundlage für Raumlicht und Anwesenheit.

## 7. Neu bewertet für Version 2

Maßstab: Macht es Klangbild im Alltag spürbar besser, ohne etwas zu verwässern – und lässt
es sich ohne Raten bauen, also ohne Entitäten aus deinem Home Assistant zu kennen und ohne
Messungen am Gerät?

| # | Idee | Entscheidung | Warum |
|---|---|---|---|
| – | Fortschrittsbalken statt Sonnenuhr | **umgesetzt** | Die Sonnenuhr war weder ruhig noch verständlich; einen Balken liest jeder ohne Nachdenken. |
| 7 | Stummschaltung dimmt wie eine Pause | **umgesetzt** | Ein ehrlicher Zustand, oben steht „Stumm“ (mit der Gruppe). |
| 8 | „Überall“ | **umgesetzt** | Spielen alle Lautsprecher von Music Assistant zusammen, steht „Überall“ statt „Wohnzimmer + 7“ – sofort verständlich, wie bei Sonos. |
| – | Ein Tastendruck blendet Ausgeblendetes ein | **umgesetzt** | Wer Uhrzeit, Gruppe oder Fortschritt ausblendet, sieht sie mit ▲/▼ trotzdem kurz. |
| 1 | Am Gerät messen | **bleibt Nummer 1** | Geht nur am Streamer (`--ez debug true`). Die neue Bühne ist leichter als die alte: ein ruhendes Lichtbild statt einer wandernden Lampe, dazu ein kleiner Balken. |
| 2 | Raumlicht | **später, mit deiner Lampe** | Braucht die Lampe oder den Lux-Sensor aus deinem Home Assistant und Feinabstimmung am Gerät; bis dahin bleibt der Sonnenstand. |
| 3 | Anwesenheit | **später, wenn ein Präsenzmelder da ist** | Spart OLED-Stunden, braucht aber einen `binary_sensor`; den häufigsten Fall deckt die Ruhezeit nach dem Musikende schon ab. |
| 6 | Echte Lampe übernimmt das Licht | **später, optional** | Der stärkste „Wow“-Moment, aber nur mit einer Lampe deiner Wahl und am Gerät abgestimmt. |
| 4 | Szenen | **vorerst nicht** | Das Menü hat wenige, klare Schalter; jede Kombination lässt sich per Link oder ADB schon aus Automationen setzen. Szenen lohnen sich, wenn Automationen danach verlangen. |
| 5 | „Nur Bild“ | **vorerst nicht** | Ohne Uhrzeit, Album und Fortschritt bleiben schon heute nur Bild, Titel und Künstler; eine eigene Komposition ohne Text ist eine Gestaltungsfrage für später. |
| 11 | Vorschau im Menü | **vorerst nicht** | Das Menü verdeckt die Bühne; eine echte Vorschau bräuchte ein anderes Menülayout. Die Änderung gilt sofort und ist nach *Zurück* zu sehen. |
| 9, 10 | Schlaftimer, Liedtext | **nicht möglich** | Home Assistant reicht weder den Schlaftimer noch Liedtexte von Music Assistant weiter. |
| 12 | Sendspin | **später** | Das Protokoll ist noch in Vorschau. |

## Quellen (Auswahl)

- Sonos: Räume gruppieren, „Überall“ – <https://support.sonos.com/de-de/article/group-and-ungroup-rooms>
- Music Assistant, Gruppen im Web-Frontend – <https://github.com/music-assistant/frontend/blob/main/src/helpers/players.ts>
- Music Assistant, Liedtexte – <https://www.music-assistant.io/metadata/lyrics/>
- Home Assistant, Media-Player- und Licht-Entitäten – <https://developers.home-assistant.io/docs/core/entity/media-player>, <https://developers.home-assistant.io/docs/core/entity/light>
- Home Assistant, Music-Assistant-Integration (Quelltext) – <https://github.com/home-assistant/core/blob/dev/homeassistant/components/music_assistant/media_player.py>
- Spotify, Canvas-Richtlinien – <https://support.spotify.com/sg-en/artists/article/canvas-guidelines/>
- Apple, Human Interface Guidelines: Settings – <https://developer.apple.com/design/human-interface-guidelines/settings>
- Android TV, Navigation – <https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv>
- Calm Technology – <https://calmtech.com/>
