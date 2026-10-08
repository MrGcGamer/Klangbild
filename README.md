# Klangbild

**Was gerade im Wohnzimmer läuft – als Bild an der Wand.**

Klangbild ist eine Android-TV-App für den Google TV Streamer. Sobald Music Assistant im
Wohnzimmer Musik abspielt, weckt Home Assistant den Fernseher und Klangbild zeigt, was
läuft: das Cover wie ein hinterleuchtetes Kunstwerk, sein Licht fällt in seiner eigenen
Farbe auf eine dunkle Wand, daneben Titel und Künstler in ruhiger Typografie. Keine Knöpfe,
keine Leisten, kein Fortschrittsbalken – die Zeit erzählt eine wandernde Lichtquelle hinter
dem Cover und eine Zeile in Worten: *„In drei Minuten · Spiegel im Spiegel · Arvo Pärt“*.

> *Klangbild* (das): in der Hi-Fi-Sprache der Charakter eines Klangs – und wörtlich das Bild
> des Klangs.

![Klangbild spielt](docs/screens/01-spielt.jpg)

## Inhalt

- [Bilder](#bilder)
- [Was Klangbild kann](#was-klangbild-kann)
- [Installation](#installation)
- [Einrichtung](#einrichtung)
- [Home-Assistant-Automationen](#home-assistant-automationen)
- [Bedienung mit der Fernbedienung](#bedienung-mit-der-fernbedienung)
- [Bildschirmschoner (optional)](#bildschirmschoner-optional)
- [Fehlerbehebung](#fehlerbehebung)
- [Datenschutz und Sicherheit](#datenschutz-und-sicherheit)
- [Entwicklung](#entwicklung)
- [Lizenzen](#lizenzen)

## Bilder

Alle Bilder stammen aus den Screenshot-Tests der App (gerendert auf der JVM in
1920 × 1080). Die Cover sind prozedural erzeugt – echte Albumcover liegen nicht im Repo.

| | |
|---|---|
| ![Klassik mit Werk und Satz](docs/screens/02-klassik.jpg) | ![Japanisch, fast schwarzes Cover](docs/screens/03-japanisch.jpg) |
| *Klassik: Werk über dem Satz, viele Mitwirkende auf einer Zeile mit „u. a.“* | *Japanisch, fast schwarzes Cover im warmweißen Lampenlicht* |
| ![Radio ohne Cover: Chladni-Figur](docs/screens/04-radio.jpg) | ![Kleines Cover, ehrlich klein](docs/screens/05-niedrig-aufgeloest.jpg) |
| *Radio ohne Cover: Sand auf einer schwingenden Platte (Chladni-Figur) – buchstäblich ein Bild des Klangs* | *Ein 300-px-Cover bleibt klein und scharf, statt groß und matschig* |
| ![Pausiert](docs/screens/06-pausiert.jpg) | ![Abends](docs/screens/07-abends.jpg) |
| *Pausiert: das Licht wird gedimmt wie eine Lampe* | *Abends wärmer und dunkler* |
| ![Nachts](docs/screens/08-nachts.jpg) | ![Nichts läuft](docs/screens/09-ruhe.jpg) |
| *Nachts sehr zurückhaltend: nur Cover, Titel, Künstler* | *Nichts läuft: eine stille Uhr an einer Wand im Lampenlicht* |
| ![Verbindung weg](docs/screens/10-verbindung.jpg) | ![Einrichtung per QR-Code](docs/screens/11-einrichtung.jpg) |
| *Verbindung weg: ruhig erklärt, nie ein Fehlercode* | *Einrichtung: der QR-Code hängt wie ein Bild an der Wand* |

**Titelwechsel** – das alte Bild wird dunkel, die Lampe dimmt und wechselt in der leeren
Wand ihre Farbe, dann kommt das neue Bild; der Text folgt, die Uhr bleibt stehen. Beim
nächsten Titel desselben Albums wechselt nur der Text:

![Titelwechsel als Bildfolge](docs/screens/titelwechsel.gif)

Die drei verworfenen bzw. weiterentwickelten Gestaltungsrichtungen und die Begründung der
Wahl stehen in [`docs/DECISIONS.md`](docs/DECISIONS.md) und
[`docs/design/richtungen.jpg`](docs/design/richtungen.jpg).

## Was Klangbild kann

- **Licht aus dem Bild.** Eine Lampe hinter dem Cover beleuchtet die Wand in der
  Leitfarbe des Covers (Farbanalyse im Farbraum Oklab): der violette Himmel eines
  Synthwave-Covers, nicht seine kleine orange Sonne. Orange wird zu Bernsteinrot statt zu
  Braun, graue und weiße Cover bekommen warmweißes Lampenlicht (≈ 2700 K), weiße Cover
  blenden nie. Direkt am Bild ist das Licht etwas schwächer, ein Stück weiter am hellsten –
  wie bei hinterleuchteten Buchstaben. Weit weg vom Bild ist die Wand schwarz.
- **Das Licht als Sonnenuhr.** Eine Seite des Lichtsaums ist deutlich heller, und diese
  Seite wandert im Lauf des Titels von links unten über oben nach rechts unten –
  unmerklich langsam, nach ein paar Minuten deutlich. Bei Radio pendelt sie langsam mit
  der Uhrzeit. Ein feines Streiflicht liegt über dem Cover, die beleuchtete Kante glänzt.
- **Zeit in Worten.** „In drei Minuten · nächster Titel“, „Gleich“, „Letzte Takte“,
  „Pausiert“. Genaue Zeiten erscheinen nur kurz nach einem Tastendruck.
- **Typografie mit Haltung.** Newsreader für Titel, Jost für Kapitälchen und Ziffern.
  Klassische Titel werden in Werk und Satz geteilt und brechen bevorzugt nach den
  Gedankenstrichen um (höchstens drei Zeilen), „(feat. …)“ wandert zu den Künstlern,
  „– 2011 Remaster“ wird zur kleinen Notiz hinter dem Titel, „Nr. 9“ und „op. 125“ trennen
  nie. Künstler stehen auf einer Zeile („… u. a.“). Japanisch, Arabisch (rechts nach links)
  und Emoji werden korrekt gesetzt – ohne Versalien, Sperrung oder falsche Kursive; Emoji
  am Rand eines Titels fallen weg, Emoji im Titel werden gedämpft.
- **Ehrlich mit der Auflösung.** Klangbild holt sich die größte verfügbare Coverversion
  (Music Assistant, Spotify, Deezer, Apple Music, Tidal, Qobuz, YouTube Music) und zeigt sie
  nie größer, als sie es verträgt.
- **Tageszeit.** Mit `sun.sun` aus Home Assistant: abends dunkler und wärmer, nachts nur
  noch Cover, Titel und Künstler. Ohne Home Assistant (Demo) nach der Uhrzeit.
- **OLED-Schutz.** Die ganze Komposition wandert langsam um ein paar Pixel, das Layout
  spiegelt sich je Album, das Licht bewegt sich, die Uhr im Ruhebildschirm wird jede
  Viertelstunde umgehängt, nichts steht stundenlang still. Am Bildrand ist es immer schwarz.
- **Jeder Zustand ist gestaltet:** spielt, pausiert, puffert, Titelwechsel, nichts läuft,
  Verbindung weg, Lautsprecher nicht erreichbar oder unbekannt, Token abgelehnt, Einrichtung.
- **Als Nächstes** kommt aus der Music-Assistant-Warteschlange (`music_assistant.get_queue`);
  das Cover des nächsten Titels wird vorab berechnet, damit der Wechsel nahtlos ist.
- **Ruhe.** Der Bildschirm bleibt nur an, solange Musik läuft. Nach einer einstellbaren
  Zeit ohne Musik gibt Klangbild den Bildschirm frei oder schließt sich.
- **Demo-Modus** ohne Home Assistant: zwölf erfundene Veröffentlichungen mit erzeugten
  Covern, die alle Grenzfälle zeigen.

## Installation

Voraussetzung: ADB-Zugriff auf den Google TV Streamer (den hast du für die
Home-Assistant-Integration ohnehin).

1. **APK holen.** Die signierte Release-APK liegt nach jedem Build auf `main` im
   [neuesten Release](../../releases/latest) (fester Link:
   `https://github.com/hazymorning/Music-Assistant-TV/releases/latest/download/klangbild.apk`;
   das Repository ist privat, also im Browser angemeldet herunterladen). Für jeden Branch
   gibt es die APK außerdem als Artifact `klangbild-apk` im jeweiligen
   [Actions-Lauf](../../actions).
2. **Installieren** (vom Rechner im selben Netz):
   ```bash
   adb connect 192.168.1.50:5555          # IP des Streamers
   adb install -r klangbild.apk           # -r: Update ohne Deinstallation
   ```
   Updates funktionieren immer mit `adb install -r`, weil jede Version mit demselben
   Schlüssel signiert ist und der `versionCode` automatisch steigt.
3. **Starten:** Klangbild erscheint unter *Apps* im Google-TV-Launcher. Oder per ADB:
   ```bash
   adb shell am start -n io.github.hazymorning.klangbild/.MainActivity
   ```

Direkt ausprobieren ohne Home Assistant:

```bash
adb shell am start -n io.github.hazymorning.klangbild/.MainActivity --ez demo true
```

## Einrichtung

Beim ersten Start zeigt Klangbild einen QR-Code und einen sechsstelligen Code.

1. QR-Code mit der Handykamera scannen. Es öffnet sich eine kleine Seite, die der
   Fernseher selbst im Heimnetz bereitstellt (z. B. `http://192.168.1.50:8765`).
2. **Home-Assistant-Adresse** eintragen, z. B. `http://192.168.1.10:8123` oder die
   Nabu-Casa-Adresse `https://….ui.nabu.casa`.
3. **Langlebigen Zugangsschlüssel** einfügen. In Home Assistant: *Profil → Sicherheit →
   Langlebige Zugangstoken → Token erstellen*.
4. *Lautsprecher suchen* – Klangbild fragt Home Assistant nach allen `media_player`-Entitäten,
   Music-Assistant-Player stehen oben. Den Wohnzimmer-Lautsprecher wählen.
5. Ruhezeit und Optionen wählen, *Speichern*. Der Fernseher verbindet sich sofort.

Die Einstellungen lassen sich jederzeit wieder öffnen: **OK lange drücken** (oder die
Menü-Taste). *Zurück* schließt sie wieder.

### Alternativ: alles per ADB

```bash
adb shell am start -n io.github.hazymorning.klangbild/.MainActivity \
    --es ha_url "http://192.168.1.10:8123" \
    --es token "eyJ0eXAiOiJKV1Qi…" \
    --es entity media_player.wohnzimmer
```

| Extra | Typ | Wirkung |
|---|---|---|
| `ha_url` | String | Adresse von Home Assistant (wird gespeichert) |
| `token` | String | Langlebiger Zugangsschlüssel (wird gespeichert) |
| `entity` | String | `media_player`-Entität (wird gespeichert) |
| `sleep_minutes` | Int/String | Minuten ohne Musik bis zur Ruhe (1–720, Standard 10) |
| `sleep_action` | String | `release` (Bildschirm freigeben, Standard) oder `close` (App schließen) |
| `clock` | Boolean | Uhr anzeigen (Standard `true`) |
| `demo` | Boolean | Nur für diesen Start: Demo statt Home Assistant |
| `setup` | Boolean | Nur für diesen Start: Einstellungen öffnen |
| `debug` | Boolean | Nur für diesen Start: Bildrate einblenden (siehe Fehlerbehebung) |

Läuft Klangbild schon, übernimmt es neue Extras ohne Neustart.

## Home-Assistant-Automationen

Platzhalter anpassen:
- `media_player.wohnzimmer` – der Music-Assistant-Player im Wohnzimmer
- `media_player.google_tv_streamer` – die Entität der *Android TV*-Integration (ADB) des Streamers

### Start: Musik läuft → Fernseher wecken, Klangbild öffnen

Startet nur, wenn auf dem Streamer gerade nichts anderes läuft (also z. B. nicht während
Netflix): erlaubt sind ausgeschaltet/Standby, der Google-TV-Startbildschirm, der
Bildschirmschoner und Klangbild selbst.

```yaml
alias: Klangbild – Musik im Wohnzimmer zeigen
description: Weckt den Google TV Streamer und öffnet Klangbild, sobald im Wohnzimmer Musik läuft.
mode: single
triggers:
  - trigger: state
    entity_id: media_player.wohnzimmer
    to: playing
conditions:
  - condition: template
    value_template: >
      {% set tv = 'media_player.google_tv_streamer' %}
      {% set app = state_attr(tv, 'app_id') %}
      {{ states(tv) in ['off', 'standby', 'unavailable']
         or app in [none,
                    'com.google.android.apps.tv.launcherx',
                    'com.google.android.tvlauncher',
                    'com.google.android.apps.tv.dreamx',
                    'io.github.hazymorning.klangbild'] }}
actions:
  - action: androidtv.adb_command
    target:
      entity_id: media_player.google_tv_streamer
    data:
      command: input keyevent KEYCODE_WAKEUP
  - delay: "00:00:02"
  - action: androidtv.adb_command
    target:
      entity_id: media_player.google_tv_streamer
    data:
      command: >-
        am start -n io.github.hazymorning.klangbild/.MainActivity
        --es entity media_player.wohnzimmer
```

Hinweise:
- `KEYCODE_WAKEUP` weckt den Streamer; ob er den Fernseher per HDMI-CEC mitweckt, hängt von
  den CEC-Einstellungen von Streamer und Fernseher ab (am echten Gerät prüfen).
- Den aktuellen Paketnamen des Startbildschirms siehst du in Home Assistant am Attribut
  `app_id` der Streamer-Entität, wenn der Startbildschirm offen ist.

### Optional: nach längerer Pause zum Startbildschirm oder schlafen

Klangbild gibt den Bildschirm nach der eingestellten Ruhezeit ohnehin frei. Wer den
Streamer aktiv zurückschicken oder schlafen legen will:

```yaml
alias: Klangbild – nach Pause Ruhe
mode: restart
triggers:
  - trigger: state
    entity_id: media_player.wohnzimmer
    from: playing
    for: "00:20:00"
conditions:
  - condition: template
    value_template: >
      {{ state_attr('media_player.google_tv_streamer', 'app_id') == 'io.github.hazymorning.klangbild'
         and not is_state('media_player.wohnzimmer', 'playing') }}
actions:
  - action: androidtv.adb_command
    target:
      entity_id: media_player.google_tv_streamer
    data:
      # HOME = zurück zum Startbildschirm; für Schlafen stattdessen:
      # command: input keyevent KEYCODE_SLEEP
      command: HOME
```

## Bedienung mit der Fernbedienung

Klangbild hat keine sichtbaren Bedienelemente. Die Fernbedienung wirkt trotzdem:

| Taste | Wirkung |
|---|---|
| OK | Pause / Weiter (der Lautsprecher in Home Assistant) |
| ◀ / ▶ | Vorheriger / nächster Titel |
| ▲ / ▼ | Zeigt für ein paar Sekunden die genaue Zeit |
| OK lange drücken, Menü | Einstellungen (QR-Code) |
| Zurück | Klangbild schließen (bzw. Einstellungen schließen) |

Die Steuerung lässt sich in den Einstellungen abschalten. Beim ersten Start ohne
Einrichtung startet OK die Demo.

## Bildschirmschoner (optional)

Klangbild kann auch der System-Bildschirmschoner sein: Läuft Musik, zeigt er sie, sonst die
stille Uhr. Auf Google TV lässt sich ein fremder Bildschirmschoner nur per ADB setzen:

```bash
adb shell settings put secure screensaver_components io.github.hazymorning.klangbild/.dream.KlangbildDream
adb shell settings put secure screensaver_enabled 1
```

Zurück zum Google-Bildschirmschoner (Ambient Mode):

```bash
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

Nicht am echten Gerät geprüft: Google TV kann den Wert bei System-Updates oder einem
Zurücksetzen überschreiben; dann die Befehle erneut ausführen.

## Fehlerbehebung

| Was du siehst | Ursache und Abhilfe |
|---|---|
| QR-Code, „Einrichten mit dem Handy“ | Noch nicht eingerichtet. QR-Code scannen oder per ADB-Extras einrichten. |
| „Der Zugangsschlüssel wurde abgelehnt.“ | Token gelöscht oder falsch kopiert. Neuen langlebigen Token erstellen und über den QR-Code eintragen. |
| „Verbindung wird hergestellt …“ bleibt stehen | Adresse prüfen (mit `http://` und Port `:8123`). Hostnamen wie `homeassistant.local` löst nicht jedes Gerät auf – im Zweifel die IP-Adresse nehmen. |
| „Verbindung unterbrochen.“ | WLAN weg oder Home Assistant startet neu. Klangbild versucht es von selbst wieder (mit wachsendem Abstand, höchstens jede Minute) und sofort, sobald das Netz zurück ist. In den ersten zwei Minuten bleibt das Bild stehen und wird nur gedimmt. |
| „„media_player.…“ kennt Home Assistant nicht.“ | Entität falsch geschrieben oder umbenannt. In den Einstellungen neu wählen. |
| „Gerade nicht erreichbar.“ | Der Lautsprecher meldet `unavailable` (z. B. Music Assistant läuft nicht). |
| Kein „In … Minuten · nächster Titel“ | Der nächste Titel kommt aus Music Assistant. Bei anderen Playern oder Radio zeigt Klangbild nur die Restzeit bzw. den Sender. |
| Unverschlüsseltes `http://` wird abgelehnt | Klartext ist nur im Heimnetz erlaubt (private Adressen, `.local`, `.lan`, Namen ohne Punkt). Von außen bitte `https://` (z. B. Nabu Casa). |
| Die Einrichtungsseite lädt nicht | Handy und Fernseher müssen im selben Netz sein; die Seite läuft nur, solange der QR-Code zu sehen ist. |
| Ruckeln? | Mit `--ez debug true` starten: oben links stehen Bilder pro Sekunde, langsamster Frame und verspätete Frames. Außerdem: `adb shell dumpsys gfxinfo io.github.hazymorning.klangbild`. |
| App schließt nach einer Weile | Ruhezeit mit „App schließen“ eingestellt – in den Einstellungen auf „Bildschirm freigeben“ ändern. |

Logs: `adb logcat | grep -i klangbild`

## Datenschutz und Sicherheit

- Keine Telemetrie, keine Werbung, keine Cloud-Dienste. Klangbild spricht nur mit deinem
  Home Assistant, deinem Music-Assistant-Server und den Cover-Servern der Musikdienste.
- Der Token liegt nur im App-eigenen Speicher; Backups und Geräteübertragung sind
  ausgeschlossen. Er wird ausschließlich an den Home-Assistant-Host geschickt, nie an
  Cover-Server.
- Die Einrichtungsseite läuft nur, solange der QR-Code zu sehen ist, verlangt den
  sechsstelligen Code (nach acht Fehlversuchen gibt es einen neuen) und gibt den
  gespeicherten Token nie zurück. Im Heimnetz läuft sie über `http://` – wie meist auch
  Home Assistant selbst.
- Der Signaturschlüssel im Repo (`keystore/`) ist nur für diese App erzeugt und schützt
  nichts außer Updates dieser privaten Sideload-App (siehe `docs/DECISIONS.md`).

## Entwicklung

```bash
./gradlew :app:testDebugUnitTest                                # Unit- und Screenshot-Tests
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true   # Screenshots nach app/build/screenshots/
./gradlew :app:assembleRelease                                  # signierte Release-APK (R8)
```

- Architektur, Design-Prinzipien und Build-Hinweise: [`CLAUDE.md`](CLAUDE.md)
- Plan und Entscheidungen: [`docs/PLAN.md`](docs/PLAN.md), [`docs/DECISIONS.md`](docs/DECISIONS.md)
- CI (GitHub Actions): Tests, Screenshots als Artifact, signierte APK als Artifact,
  Smoke-Test der Release-APK auf einem Android-TV-14-Emulator im Demo-Modus (mit Screenshots
  als Artifact), auf `main` zusätzlich ein Release.

## Lizenzen

- Schriften: [Newsreader](app/src/main/assets/licenses/Newsreader-OFL.txt) (Production Type)
  und [Jost](app/src/main/assets/licenses/Jost-OFL.txt) (indestructible type*), beide unter
  der SIL Open Font License 1.1, in die App eingebunden.
- Bibliotheken: AndroidX/Jetpack Compose, Kotlin Coroutines und kotlinx.serialization,
  OkHttp, ZXing – Apache License 2.0.
