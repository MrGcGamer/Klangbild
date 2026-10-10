package io.github.hazymorning.klangbild.config

import io.github.hazymorning.klangbild.color.Appearance
import io.github.hazymorning.klangbild.data.LinkState
import io.github.hazymorning.klangbild.data.ma.MaLink
import io.github.hazymorning.klangbild.domain.MediaKind
import io.github.hazymorning.klangbild.domain.SleepChoice
import io.github.hazymorning.klangbild.domain.StageView
import io.github.hazymorning.klangbild.domain.TimeLine

/** Sections of the settings menu, in order. */
enum class MenuSection(val title: String) {
    PICTURE("Bild"),
    INFO("Informationen"),
    EFFECTS("Effekte"),
    VISTA("Fensterblick"),
    REMOTE("Fernbedienung"),
    TIMER("Timer"),
    CONNECTION("Verbindung"),
}

/** One line of the settings menu, in the menu's order. */
enum class MenuEntry(val section: MenuSection, val label: String) {
    VIEW(MenuSection.PICTURE, "Ansicht"),
    APPEARANCE(MenuSection.PICTURE, "Erscheinungsbild"),
    CLOCK(MenuSection.INFO, "Uhrzeit"),
    TIME_LINE(MenuSection.INFO, "Fortschrittsbalken"),
    NEXT(MenuSection.INFO, "Nächster Titel"),
    ALBUM(MenuSection.INFO, "Album und Jahr"),
    GROUP(MenuSection.INFO, "Lautsprechergruppe"),
    VOLUME(MenuSection.INFO, "Lautstärke"),
    BACKGROUND(MenuSection.EFFECTS, "Hintergrund"),
    LIGHT(MenuSection.EFFECTS, "Intensität"),
    MOTION(MenuSection.EFFECTS, "Bewegung"),
    BARS(MenuSection.EFFECTS, "Lichtleisten"),
    VISTA_PAUSE(MenuSection.VISTA, "Bei Pause"),
    VISTA_PLAYING(MenuSection.VISTA, "Während der Musik"),
    MINI_PLAYER(MenuSection.VISTA, "Mini-Player"),
    REMOTE(MenuSection.REMOTE, "Musik steuern"),
    UP_DOWN(MenuSection.REMOTE, "Hoch und runter"),
    SPOKEN(MenuSection.REMOTE, "Hörbücher, Podcasts"),
    SLEEP_TIMER(MenuSection.TIMER, "Sleep-Timer"),
    SLEEP_AFTER(MenuSection.TIMER, "Ausschalten nach"),
    SLEEP_ACTION(MenuSection.TIMER, "Beim Ausschalten"),
    CONNECTION(MenuSection.CONNECTION, "Home Assistant"),
    MUSIC_ASSISTANT(MenuSection.CONNECTION, "Music Assistant"),
}

/**
 * What the menu needs to know besides the settings: the connections and the wall as they are
 * now, the sleep timer ([sleep], pausing at [sleepAt]) and what kind of thing plays.
 */
data class MenuContext(
    val link: LinkState? = null,
    val appearance: Appearance = Appearance.DARK,
    val ma: MaLink? = null,
    val sleep: SleepChoice = SleepChoice.OFF,
    val sleepAt: Long? = null,
    val kind: MediaKind? = null,
)

/** A line as the menu shows it: the value in words and what the option does; [level] (0..1) for a value shown on a slider. */
data class MenuRow(val entry: MenuEntry, val value: String, val description: String, val level: Float? = null)

/**
 * The settings menu, operated with the remote: up and down choose a line, left and right
 * (or OK) change its value at once – there is nothing to confirm. Every text is German UI
 * prose: no-break spaces keep articles and adjectives with their noun and a short word that
 * opens a sentence off the end of a line.
 */
object SettingsMenu {
    val entries: List<MenuEntry> = MenuEntry.entries

    /** Minutes offered for the sleep timeout. */
    val sleepChoices = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    fun rows(s: Settings, ctx: MenuContext): List<MenuRow> = entries.map { MenuRow(it, value(it, s, ctx), description(it, s, ctx), level(it, s)) }

    /** Where a slider stands: the light bars' brightness. */
    fun level(e: MenuEntry, s: Settings): Float? = when (e) {
        MenuEntry.BARS -> s.barLevel.brightness.takeIf { s.bars.isSet }
        else -> null
    }

    fun value(e: MenuEntry, s: Settings, ctx: MenuContext): String = when (e) {
        MenuEntry.VIEW -> if (s.view == StageView.FLOW) "Cover Flow" else "Einzelnes Cover"
        MenuEntry.CLOCK -> onOff(s.showClock)
        MenuEntry.TIME_LINE -> onOff(s.showTimeLine)
        MenuEntry.NEXT -> onOff(s.showNext)
        MenuEntry.ALBUM -> onOff(s.showAlbum)
        MenuEntry.GROUP -> onOff(s.showGroup)
        MenuEntry.VOLUME -> when (s.volumeDisplay) {
            VolumeDisplay.ON_CHANGE -> "Bei Änderung"
            VolumeDisplay.ALWAYS -> "Immer"
            VolumeDisplay.OFF -> "Aus"
        }
        MenuEntry.BACKGROUND -> if (s.background == BackgroundMode.COVER) "Coverfarben" else "Einfarbig"
        MenuEntry.MOTION -> when (s.motion) {
            MotionLevel.OFF -> "Aus"
            MotionLevel.GENTLE -> "Langsam"
            MotionLevel.MEDIUM -> "Mittel"
            MotionLevel.LIVELY -> "Schnell"
        }
        MenuEntry.BARS -> if (!s.bars.isSet) "Einrichten" else if (s.barLevel == BarLevel.OFF) "Aus" else "${s.barLevel.percent}\u00A0%"
        MenuEntry.APPEARANCE -> when (s.appearance) {
            AppearanceMode.AUTO -> "Automatisch"
            AppearanceMode.LIGHT -> "Hell"
            AppearanceMode.DARK -> "Dunkel"
        }
        MenuEntry.LIGHT -> when (s.light) {
            LightLevel.STRONG -> "Stark"
            LightLevel.NORMAL -> "Normal"
            LightLevel.SUBTLE -> "Schwach"
            LightLevel.OFF -> "Aus"
        }
        MenuEntry.VISTA_PAUSE -> delay(s.vistaPauseSeconds)
        MenuEntry.VISTA_PLAYING -> delay(s.vistaPlayingSeconds)
        MenuEntry.MINI_PLAYER -> when (s.miniPlayer) {
            MiniPlayer.LEFT -> "Unten links"
            MiniPlayer.RIGHT -> "Unten rechts"
            MiniPlayer.OFF -> "Aus"
        }
        MenuEntry.REMOTE -> onOff(s.remoteControl)
        MenuEntry.UP_DOWN -> if (s.volumeKeys) "Lautstärke" else "Fensterblick, Menü"
        MenuEntry.SPOKEN -> if (s.spokenSkip) "Spulen" else "Titel wechseln"
        MenuEntry.SLEEP_TIMER -> sleepValue(ctx.sleep, ctx.kind)
        MenuEntry.SLEEP_AFTER -> minutes(s.sleepAfterMinutes)
        MenuEntry.SLEEP_ACTION -> if (s.sleepAction == SleepAction.CLOSE) "App schließen" else "Bildschirmschoner"
        MenuEntry.CONNECTION -> when {
            !s.isConfigured -> "Einrichten"
            ctx.link == LinkState.CONNECTED -> "Verbunden"
            ctx.link == LinkState.AUTH_FAILED -> "Token abgelehnt"
            else -> "Verbindet …"
        }
        MenuEntry.MUSIC_ASSISTANT -> when {
            !s.hasMusicAssistant -> "Nicht eingerichtet"
            ctx.ma == MaLink.CONNECTED -> "Verbunden"
            ctx.ma == MaLink.AUTH_FAILED -> "Token abgelehnt"
            ctx.ma == MaLink.UNREACHABLE -> "Nicht erreichbar"
            else -> "Verbindet …"
        }
    }

    fun description(e: MenuEntry, s: Settings, ctx: MenuContext): String = when (e) {
        MenuEntry.VIEW -> if (s.view == StageView.FLOW) {
            "Das Cover in der Mitte, links die Cover davor, rechts die nächsten. Sie blättern weiter, wenn ein neues Album beginnt."
        } else {
            "Ein Cover, groß, neben dem Titel."
        }
        MenuEntry.APPEARANCE -> when (s.appearance) {
            AppearanceMode.AUTO ->
                "Tagsüber hell, von Sonnenuntergang bis Sonnenaufgang dunkel – nach dem Stand der Sonne in Home\u00A0Assistant."
            AppearanceMode.LIGHT -> "Immer hell: heller Hintergrund, dunkle Schrift."
            AppearanceMode.DARK -> "Immer dunkel: dunkler Hintergrund, helle Schrift."
        }
        MenuEntry.CLOCK -> "Die Uhrzeit oben in der ersten Zeile."
        MenuEntry.TIME_LINE ->
            "Der Balken mit gespielter Zeit und Länge. Ausgeschaltet zeigt ihn ein Tastendruck trotzdem kurz."
        MenuEntry.NEXT -> "Der nächste Titel aus der Warteschlange, in der letzten Zeile."
        MenuEntry.ALBUM -> "Name und Jahr des Albums unter den Künstlern."
        MenuEntry.GROUP ->
            "Spielen weitere Lautsprecher mit, steht neben der Uhrzeit etwa „Wohnzimmer\u00A0+\u00A05“ oder „Überall“."
        MenuEntry.VOLUME -> when (s.volumeDisplay) {
            VolumeDisplay.ON_CHANGE -> "Ändert sich die Lautstärke, steht sie ein paar Sekunden lang neben der Uhrzeit."
            VolumeDisplay.ALWAYS -> "Die Lautstärke steht immer neben der Uhrzeit."
            VolumeDisplay.OFF -> "Die Lautstärke wird nie angezeigt."
        }
        MenuEntry.BACKGROUND -> if (s.background == BackgroundMode.COVER) {
            "Die Farben des Covers füllen den ganzen Bildschirm, weich verlaufend. Der Fortschrittsbalken nimmt ihren Ton an."
        } else {
            "Ein ruhiger Hintergrund: dunkel mit dem Licht des Covers, hell mit seinem Schatten."
        }
        MenuEntry.LIGHT -> if (s.background == BackgroundMode.COVER) {
            // With the cover's colours the setting is how strong they are.
            "Wie\u00A0kräftig die\u00A0Farben des\u00A0Covers den\u00A0Hintergrund färben. " + when (s.light) {
                LightLevel.STRONG -> "Stark:\u00A0satte\u00A0Farben."
                LightLevel.NORMAL -> "Normal\u00A0ist so, wie Klangbild gedacht\u00A0ist."
                LightLevel.SUBTLE -> "Schwach:\u00A0gedeckte\u00A0Farben."
                LightLevel.OFF -> if (ctx.appearance == Appearance.LIGHT) "Aus:\u00A0fast ohne\u00A0Farbe." else "Aus:\u00A0nur das\u00A0Cover auf\u00A0Schwarz."
            }
        } else if (ctx.appearance == Appearance.LIGHT) {
            "Wie stark der helle Hintergrund die Farbe des Covers annimmt. " + when (s.light) {
                LightLevel.STRONG -> "Stark: ein deutlicher Hauch."
                LightLevel.NORMAL -> "Normal ist so, wie Klangbild gedacht ist."
                LightLevel.SUBTLE -> "Schwach: nur eine Ahnung."
                LightLevel.OFF -> "Aus: ohne Farbe."
            }
        } else {
            "Wie hell das Cover den Hintergrund um sich herum anstrahlt. " + when (s.light) {
                LightLevel.STRONG -> "Stark passt zu hellen Räumen."
                LightLevel.NORMAL -> "Normal ist so, wie Klangbild gedacht ist."
                LightLevel.SUBTLE -> "Schwach passt zu dunklen Abenden."
                LightLevel.OFF -> "Aus: nur das Cover, ohne Licht."
            }
        }
        MenuEntry.MOTION -> {
            val what = when {
                s.background == BackgroundMode.COVER && s.bars.isSet -> "Wie schnell die Farben im Hintergrund und in den Lichtleisten fließen. "
                s.bars.isSet -> "Wie schnell die Farben der Lichtleisten fließen – auf dem Bildschirm nur mit Coverfarben. "
                else -> "Wie schnell die Farben im Hintergrund fließen – mit Coverfarben. "
            }
            what + when (s.motion) {
                MotionLevel.OFF -> "Aus: Sie stehen still."
                MotionLevel.GENTLE -> "Langsam: kaum zu bemerken."
                MotionLevel.MEDIUM -> "Mittel: deutlich, aber ruhig."
                MotionLevel.LIVELY -> "Schnell: sichtbar in Bewegung."
            } + " Nur während Musik läuft."
        }
        MenuEntry.BARS -> if (!s.bars.isSet) {
            "Lichtleisten hinter dem Fernseher, etwa Philips Hue Play, leuchten in den Farben des Covers. " +
                "OK zeigt den QR-Code; am Handy wählst du sie aus Home\u00A0Assistant aus."
        } else {
            if (s.barLevel == BarLevel.OFF) {
                "Die Lichtleisten hinter dem Fernseher behalten ihr eigenes Licht. Rechts schaltet sie zu."
            } else {
                "Jede Lichtleiste hinter dem Fernseher leuchtet in einer Farbe des Bildes. So hell bei Tag; abends und nachts " +
                    "werden sie von selbst dunkler. Nach der Musik sind sie wieder wie vorher."
            }
        }
        MenuEntry.VISTA_PAUSE -> if (s.vistaPauseSeconds == 0) {
            "Ist die Musik angehalten, bleibt das Cover stehen; nach drei\u00A0Minuten erscheint die Uhr."
        } else {
            "Ist die Musik so lange angehalten, erscheint eine Landschaft aus Papier, deren Licht der\u00A0echten Tageszeit folgt. " +
                "Jede Taste holt das Cover zurück."
        }
        MenuEntry.VISTA_PLAYING -> if (s.vistaPlayingSeconds == 0) {
            if (s.volumeKeys) "Während Musik läuft, bleibt das Cover."
            else "Während Musik läuft, bleibt das Cover. Den\u00A0Fensterblick gibt es jederzeit: Pfeil hoch."
        } else {
            "Läuft die Musik so lange ohne Tastendruck, wird sie zum\u00A0Fensterblick\u00A0– mit dem\u00A0Mini-Player in der\u00A0Ecke."
        }
        MenuEntry.MINI_PLAYER -> when (s.miniPlayer) {
            MiniPlayer.LEFT -> "Cover, Titel und Fortschritt unten links in\u00A0der Landschaft."
            MiniPlayer.RIGHT -> "Cover, Titel und Fortschritt unten rechts in\u00A0der Landschaft."
            MiniPlayer.OFF -> "Der Fensterblick zeigt nur die\u00A0Landschaft."
        }
        MenuEntry.REMOTE ->
            if (s.remoteControl) "OK hält die Musik an und spielt weiter, links und rechts wechseln den Titel."
            else "Die Fernbedienung steuert die Musik nicht; OK lange drücken öffnet weiter dieses Menü."
        MenuEntry.UP_DOWN -> if (s.volumeKeys) {
            "Pfeil hoch und runter ändern die Lautstärke des Lautsprechers; die\u00A0Schnelleinstellungen gibt es dann nicht."
        } else {
            "Pfeil hoch zeigt den Fensterblick; noch einmal hoch oder Zurück holt das Cover zurück. " +
                "Pfeil runter öffnet unten die\u00A0Schnelleinstellungen, noch einmal runter schließt\u00A0sie."
        }
        MenuEntry.SPOKEN -> if (s.spokenSkip) {
            "Bei Hörbüchern und Podcasts spult links 15\u00A0Sekunden zurück, rechts 30\u00A0Sekunden vor. " +
                "Gedrückt gehalten geht es zum vorigen oder nächsten Kapitel."
        } else {
            "Links und rechts wechseln auch bei Hörbüchern und Podcasts den Titel."
        }
        MenuEntry.SLEEP_TIMER -> when {
            ctx.sleep == SleepChoice.OFF ->
                "Hält die Musik nach einer Weile an, auf Wunsch am Ende eines Kapitels. Dafür muss Klangbild laufen."
            ctx.sleepAt != null -> "Die Musik hält um ${TimeLine.clock(ctx.sleepAt)} an."
            else -> "Die Musik hält an, sobald ${endPhrase(ctx.sleep, ctx.kind)} erreicht ist."
        }
        MenuEntry.SLEEP_AFTER -> "Läuft keine Musik mehr, schaltet Klangbild nach dieser Zeit aus."
        MenuEntry.SLEEP_ACTION -> if (s.sleepAction == SleepAction.CLOSE) {
            "Klangbild schließt sich, zurück zum Startbildschirm."
        } else {
            "Der Fernseher übernimmt: sein Bildschirmschoner oder Standby."
        }
        MenuEntry.CONNECTION -> if (!s.isConfigured) {
            "OK\u00A0zeigt den\u00A0QR-Code. Am\u00A0Handy verbindest du Home\u00A0Assistant und\u00A0wählst den\u00A0Lautsprecher."
        } else {
            "OK\u00A0zeigt den\u00A0QR-Code. Am\u00A0Handy änderst du Adresse, Zugangstoken und\u00A0Lautsprecher."
        }
        MenuEntry.MUSIC_ASSISTANT -> if (!s.hasMusicAssistant) {
            "Freiwillig. Mit\u00A0Music\u00A0Assistant zeigt Cover\u00A0Flow die\u00A0ganze Warteschlange, und\u00A0bei Hörbüchern stehen die\u00A0Kapitel da. " +
                "OK\u00A0zeigt den\u00A0QR-Code zum\u00A0Einrichten."
        } else {
            "Cover\u00A0Flow zeigt die\u00A0ganze Warteschlange. OK\u00A0zeigt den\u00A0QR-Code. Am\u00A0Handy änderst du Adresse und\u00A0Token."
        }
    }

    /**
     * The settings after one step of [e]: [direction] −1 (left) or +1 (right, OK). Left and
     * right stop at the ends of a scale, OK ([wrap]) goes round. Switches simply flip. Null
     * for the action lines (and the light bars while none are set up: OK sets them up).
     */
    fun step(e: MenuEntry, s: Settings, direction: Int, wrap: Boolean = false): Settings? = when (e) {
        MenuEntry.VIEW -> s.copy(view = if (s.view == StageView.FLOW) StageView.SINGLE else StageView.FLOW)
        MenuEntry.CLOCK -> s.copy(showClock = !s.showClock)
        MenuEntry.TIME_LINE -> s.copy(showTimeLine = !s.showTimeLine)
        MenuEntry.NEXT -> s.copy(showNext = !s.showNext)
        MenuEntry.ALBUM -> s.copy(showAlbum = !s.showAlbum)
        MenuEntry.GROUP -> s.copy(showGroup = !s.showGroup)
        MenuEntry.VOLUME -> s.copy(volumeDisplay = move(VOLUME_ORDER, s.volumeDisplay, direction, wrap))
        MenuEntry.BACKGROUND -> s.copy(background = if (s.background == BackgroundMode.COVER) BackgroundMode.WALL else BackgroundMode.COVER)
        MenuEntry.MOTION -> s.copy(motion = move(MOTION_ORDER, s.motion, direction, wrap))
        MenuEntry.BARS -> if (s.bars.isSet) s.copy(barLevel = move(BAR_ORDER, s.barLevel, direction, wrap)) else null
        MenuEntry.APPEARANCE -> s.copy(appearance = move(APPEARANCE_ORDER, s.appearance, direction, wrap))
        MenuEntry.LIGHT -> s.copy(light = move(LIGHT_ORDER, s.light, direction, wrap))
        MenuEntry.VISTA_PAUSE -> s.copy(vistaPauseSeconds = move(PAUSE_DELAYS, nearest(PAUSE_DELAYS, s.vistaPauseSeconds), direction, wrap))
        MenuEntry.VISTA_PLAYING -> s.copy(vistaPlayingSeconds = move(PLAYING_DELAYS, nearest(PLAYING_DELAYS, s.vistaPlayingSeconds), direction, wrap))
        MenuEntry.MINI_PLAYER -> s.copy(miniPlayer = move(MINI_ORDER, s.miniPlayer, direction, wrap))
        MenuEntry.REMOTE -> s.copy(remoteControl = !s.remoteControl)
        MenuEntry.UP_DOWN -> s.copy(volumeKeys = !s.volumeKeys)
        MenuEntry.SPOKEN -> s.copy(spokenSkip = !s.spokenSkip)
        MenuEntry.SLEEP_AFTER -> s.copy(sleepAfterMinutes = nextSleep(s.sleepAfterMinutes, direction, wrap))
        MenuEntry.SLEEP_ACTION -> s.copy(
            sleepAction = if (s.sleepAction == SleepAction.CLOSE) SleepAction.RELEASE else SleepAction.CLOSE,
        )
        MenuEntry.CONNECTION, MenuEntry.MUSIC_ASSISTANT, MenuEntry.SLEEP_TIMER -> null
    }

    /** The delays offered for the Fensterblick (s): when paused, and while music plays; 0 is "Aus". */
    val PAUSE_DELAYS = listOf(0, 5, 15, 30, 60, 180, 300, 600)
    val PLAYING_DELAYS = listOf(0, 60, 120, 300, 600, 900, 1800)

    /** "Aus", "15 Sekunden", "Drei Minuten" … */
    fun delay(seconds: Int): String = when {
        seconds <= 0 -> "Aus"
        seconds <= 12 -> numberWords[seconds].replaceFirstChar { it.uppercase() } + " Sekunden"
        seconds < 60 -> "$seconds Sekunden"
        else -> minutes(seconds / 60)
    }

    /** The offered value nearest to [v] (which may have been set by ADB). */
    private fun nearest(choices: List<Int>, v: Int): Int = choices.minByOrNull { kotlin.math.abs(it - v) } ?: choices.first()

    /** "Aus", "30 Minuten", "Eine Stunde", "Ende des Kapitels", "Ende der Folge" … */
    fun sleepValue(choice: SleepChoice, kind: MediaKind?): String = when (choice) {
        SleepChoice.OFF -> "Aus"
        SleepChoice.CHAPTER_END -> "Ende des Kapitels"
        SleepChoice.ITEM_END -> when (kind) {
            MediaKind.PODCAST -> "Ende der Folge"
            MediaKind.AUDIOBOOK -> "Ende des Hörbuchs"
            else -> "Ende des Titels"
        }
        else -> minutes(choice.minutes)
    }

    /** "das Ende des Kapitels" – for the sentence that says when the music pauses. */
    private fun endPhrase(choice: SleepChoice, kind: MediaKind?): String = "das\u00A0" + sleepValue(choice, kind).replace(" ", "\u00A0")

    /** From quiet to present: left means less, right means more. */
    private val VOLUME_ORDER = listOf(VolumeDisplay.OFF, VolumeDisplay.ON_CHANGE, VolumeDisplay.ALWAYS)
    private val LIGHT_ORDER = listOf(LightLevel.OFF, LightLevel.SUBTLE, LightLevel.NORMAL, LightLevel.STRONG)
    private val APPEARANCE_ORDER = listOf(AppearanceMode.DARK, AppearanceMode.AUTO, AppearanceMode.LIGHT)
    private val MOTION_ORDER = listOf(MotionLevel.OFF, MotionLevel.GENTLE, MotionLevel.MEDIUM, MotionLevel.LIVELY)
    private val BAR_ORDER = BarLevel.entries
    private val MINI_ORDER = listOf(MiniPlayer.OFF, MiniPlayer.LEFT, MiniPlayer.RIGHT)

    private fun <T> move(order: List<T>, current: T, direction: Int, wrap: Boolean): T {
        val i = order.indexOf(current).coerceAtLeast(0) + direction
        return if (wrap) order[Math.floorMod(i, order.size)] else order[i.coerceIn(0, order.size - 1)]
    }

    /** The next offered value from [minutes] (which may be any value set by ADB). */
    fun nextSleep(minutes: Int, direction: Int, wrap: Boolean = false): Int {
        val choices = sleepChoices
        return if (direction > 0) {
            choices.firstOrNull { it > minutes } ?: if (wrap) choices.first() else choices.last()
        } else {
            choices.lastOrNull { it < minutes } ?: if (wrap) choices.last() else choices.first()
        }
    }

    private fun onOff(on: Boolean) = if (on) "An" else "Aus"

    private val numberWords = listOf(
        "", "eine", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
    )

    /** "Zehn Minuten", "Eine Stunde", "90 Minuten" – numbers up to twelve in words. */
    fun minutes(m: Int): String = when {
        m == 60 -> "Eine Stunde"
        m % 60 == 0 && m / 60 <= 12 -> numberWords[m / 60].replaceFirstChar { it.uppercase() } + " Stunden"
        m == 1 -> "Eine Minute"
        m <= 12 -> numberWords[m].replaceFirstChar { it.uppercase() } + " Minuten"
        else -> "$m Minuten"
    }
}
