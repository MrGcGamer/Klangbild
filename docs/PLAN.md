# Klangbild – Plan

> **Klangbild** (das): in der Hi-Fi-Sprache der Charakter eines Klangs („ein warmes Klangbild“),
> wörtlich: das Bild des Klangs. Genau das soll die App sein – ein ruhiges Bild dessen,
> was gerade im Wohnzimmer erklingt.

Dieses Dokument ist der Arbeitsplan. Entscheidungen mit Begründung stehen in
[`DECISIONS.md`](DECISIONS.md), die Bedienung in der [`README`](../README.md).

## Leitlinien

1. **Gestaltung ist die Hauptaufgabe.** Erst die Augen bauen (Screenshot-Pipeline), dann
   gestalten, dann polieren. Jede Phase endet mit gerenderten Bildern, die ich mir ansehe.
2. **Datenquelle → Zustandsmodell → UI**, streng getrennt, Coroutines und Flow.
   Alles, was Logik ist, ist auf der JVM testbar (kein Android nötig).
3. **Billig pro Frame.** Teures (Weichzeichner, Farbanalyse, Hochskalierung, Dithering)
   einmal pro Titel im Hintergrund; pro Frame nur Transparenz und Transformationen.
4. **Nichts behaupten, was nicht geprüft ist.** Was nur auf dem echten Gerät prüfbar ist,
   wird ausdrücklich so markiert.

## Phasen

### Phase 0 – Fundament
- Android SDK (Kommandozeilen-Tools) lokal, aktuelle stabile Versionen von AGP, Kotlin,
  Compose, Roborazzi/Robolectric prüfen.
- Gradle-Projekt (Kotlin DSL, Version Catalog), ein Modul `app`.
- `CLAUDE.md`, `docs/DECISIONS.md`, `.gitignore`.

### Phase 1 – Augen bauen (visueller Feedback-Loop)
- Roborazzi + Robolectric (Native Graphics) für JVM-Screenshots in 1920 × 1080 und
  stichprobenartig 3840 × 2160.
- Synthetische Test-Cover für die Grenzfälle: fast schwarz, fast weiß, monochrom, neon,
  niedrig aufgelöst (300 px), hoch aufgelöst, unruhig/fotografisch, kein Cover.
- Test-Metadaten: lange Klassik-Titel, viele Künstler, fehlendes Album, Japanisch,
  Arabisch, Emoji.
- Übergänge als Bildfolge (Kontaktabzug) rendern, um Bewegung zu beurteilen.

### Phase 2 – Gestaltungsrichtungen
- Zwei bis drei klar unterschiedliche Richtungen rendern (Arbeitstitel: *Galerie*,
  *Halo*, *Feuilleton*).
- Strenge Bewertung wie ein Art Director, Wahl begründen (DECISIONS.md).
- Schriften (OFL) auswählen und mit Lizenzen einbinden.

### Phase 3 – Daten
- Home-Assistant-WebSocket: Authentifizierung, `subscribe_entities` für den Player und
  `sun.sun`, `call_service` mit `return_response` für `music_assistant.get_queue`
  (nächster Titel, Cover-URLs), Ping/Pong.
- Wiederverbindung mit exponentiellem Backoff + Jitter, Netzwerk-Callback,
  Sonderfälle: HA-Neustart, WLAN weg, `unavailable`, ungültiger Token, unbekannte Entität.
- URL-Auflösung (`entity_picture` relativ/absolut), Token nur an den HA-Host,
  Klartext nur im lokalen Netz.
- Cover laden: höchste verfügbare Auflösung (bekannte CDNs), Vorab-Laden des nächsten Titels.
- Demo-Quelle mit erzeugten Covern und wechselnden Titeln (in Version 2 entfernt; die Cover leben in den Tests weiter).

### Phase 4 – Zustandsmodell und Lebenszyklus
- Ein reiner Reducer: Quelle + Zeit → Bildschirmzustand (spielt, pausiert, puffert,
  Titelwechsel, Ruhe, Verbindung weg, Token ungültig, Einrichtung).
- Fortschritt lokal interpolieren, Uhrenversatz zu HA schätzen, sanft korrigieren.
- Bildschirm an nur bei Musik; nach Pause sanft in den Ruhezustand; nach einstellbarer
  Zeit Bildschirm freigeben oder App schließen. Zurück beendet.
- Start per `am start` mit Extras; laufende App übernimmt neue Extras (`onNewIntent`).
- Kaltstart ohne weißen Blitz: schwarzes Fenster, erstes Bild blendet aus Schwarz auf,
  letztes Cover aus dem Cache.

### Phase 5 – Einrichtung
- QR-Code auf dem TV → kleine Webseite, die die App selbst im LAN bereitstellt
  (HA-URL, Token, Entität mit Auswahlliste, Einstellungen).
- Absicherung mit Einmal-Code (im QR enthalten, sonst eintippen), Server läuft nur
  während der Einrichtung.
- Alternativ alles per ADB-Extras.

### Phase 6 – Polieren
- Mehrere Runden: alle Zustände, Übergänge, Grenzfälle, Tageszeit (`sun.sun`),
  OLED-Schutz (unmerkliches Wandern), 4K-Stichproben.
- Kritik durch einen frischen Subagenten, der den Code nicht kennt.
- Performance-Durchsicht: Vorberechnung, wenige Vollbild-Ebenen, gedrosselte
  Ambient-Animationen, Release-Build mit R8.

### Phase 7 – Build, Signierung, CI
- GitHub Actions: Unit- und Screenshot-Tests, signierte Release-APK als Artifact,
  fortlaufendes Release „latest“ für `main`, Smoke-Test auf einem Android-TV-Emulator
  im Demo-Modus mit Screenshot (seit Version 2 gegen einen Ersatz-Home-Assistant).
- Eigener Keystore im (privaten) Repo, `versionCode` automatisch aus der Commit-Zeit.

### Phase 8 – Abschluss
- README (deutsch): Installation per `adb`, Einrichtung, HA-Automationen (YAML),
  Bildschirmschoner (`DreamService`), Fehlerbehebung, finale Renderings.
- Launcher-Banner und Icon im Stil der App.
- Pull Request nach `main` mit Zusammenfassung und Screenshots.

## Definition of Done (aus dem Brief)
- [ ] CI grün, signierte Release-APK als Artifact und Release
- [ ] Screenshot-Tests decken alle Zustände ab, finale Renderings in der README
- [x] ~~Demo-Modus funktioniert ohne Home Assistant~~ (in Version 2 entfernt)
- [ ] README: Installation, Einrichtung, HA-Automationen, Fehlerbehebung
- [ ] Keine Geheimnisse im Repo (außer dem bewusst eingecheckten Keystore)
- [ ] Pull Request nach `main`
