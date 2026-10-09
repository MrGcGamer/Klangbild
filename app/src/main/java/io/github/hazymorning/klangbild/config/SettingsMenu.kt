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
    DISPLAY("Anzeige"),
    EFFECTS("Effekte"),
    LIGHT("Licht"),
    REMOTE("Fernbedienung"),
    REST("Ruhe"),
    CONNECTION("Verbindung"),
}

/** One line of the settings menu. */
enum class MenuEntry(val section: MenuSection, val label: String) {
    VIEW(MenuSection.DISPLAY, "Ansicht"),
    CLOCK(MenuSection.DISPLAY, "Uhrzeit"),
    TIME_LINE(MenuSection.DISPLAY, "Fortschritt"),
    NEXT(MenuSection.DISPLAY, "Als Nächstes"),
    ALBUM(MenuSection.DISPLAY, "Album"),
    GROUP(MenuSection.DISPLAY, "Lautsprechergruppe"),
    VOLUME(MenuSection.DISPLAY, "Lautstärke"),
    BACKGROUND(MenuSection.EFFECTS, "Hintergrund"),
    MOTION(MenuSection.EFFECTS, "Bewegung"),
    BARS(MenuSection.EFFECTS, "Lichtleisten"),
    APPEARANCE(MenuSection.LIGHT, "Erscheinungsbild"),
    LIGHT(MenuSection.LIGHT, "Wandlicht"),
    DAY_NIGHT(MenuSection.LIGHT, "Tag und Nacht"),
    REMOTE(MenuSection.REMOTE, "Musik steuern"),
    UP_DOWN(MenuSection.REMOTE, "Hoch und runter"),
    SPOKEN(MenuSection.REMOTE, "Hörbücher und Podcasts"),
    SLEEP_TIMER(MenuSection.REST, "Einschlafen"),
    SLEEP_AFTER(MenuSection.REST, "Ruhe nach"),
    SLEEP_ACTION(MenuSection.REST, "Danach"),
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

/** A line as the menu shows it: the value in words and what the option does. */
data class MenuRow(val entry: MenuEntry, val value: String, val description: String)

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

    fun rows(s: Settings, ctx: MenuContext): List<MenuRow> = entries.map { MenuRow(it, value(it, s, ctx), description(it, s, ctx)) }

    fun value(e: MenuEntry, s: Settings, ctx: MenuContext): String = when (e) {
        MenuEntry.VIEW -> if (s.view == StageView.FLOW) "Cover Flow" else "Einzelbild"
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
        MenuEntry.BACKGROUND -> if (s.background == BackgroundMode.COVER) "Coverfarben" else "Wand"
        MenuEntry.MOTION -> when (s.motion) {
            MotionLevel.OFF -> "Aus"
            MotionLevel.GENTLE -> "Sanft"
            MotionLevel.MEDIUM -> "Mittel"
            MotionLevel.LIVELY -> "Lebhaft"
        }
        MenuEntry.BARS -> if (!s.bars.isSet) "Einrichten" else when (s.barLevel) {
            BarLevel.OFF -> "Aus"
            BarLevel.SUBTLE -> "Dezent"
            BarLevel.NORMAL -> "Normal"
            BarLevel.STRONG -> "Kräftig"
        }
        MenuEntry.APPEARANCE -> when (s.appearance) {
            AppearanceMode.AUTO -> "Automatisch"
            AppearanceMode.LIGHT -> "Hell"
            AppearanceMode.DARK -> "Dunkel"
        }
        MenuEntry.LIGHT -> when (s.light) {
            LightLevel.STRONG -> "Kräftig"
            LightLevel.NORMAL -> "Normal"
            LightLevel.SUBTLE -> "Dezent"
            LightLevel.OFF -> "Aus"
        }
        MenuEntry.DAY_NIGHT -> onOff(s.dayNight)
        MenuEntry.REMOTE -> onOff(s.remoteControl)
        MenuEntry.UP_DOWN -> if (s.volumeKeys) "Lautstärke" else "Einblenden"
        MenuEntry.SPOKEN -> if (s.spokenSkip) "Springen" else "Wechseln"
        MenuEntry.SLEEP_TIMER -> sleepValue(ctx.sleep, ctx.kind)
        MenuEntry.SLEEP_AFTER -> minutes(s.sleepAfterMinutes)
        MenuEntry.SLEEP_ACTION -> if (s.sleepAction == SleepAction.CLOSE) "App schließen" else "Bildschirm freigeben"
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
            "Das Cover in der Mitte, links die Cover davor, rechts die nächsten – sie blättern weiter, wenn ein neues Album beginnt."
        } else {
            "Ein Cover an der Wand, in seinem eigenen Licht."
        }
        MenuEntry.CLOCK -> "Die Uhrzeit oben neben dem Bild."
        MenuEntry.TIME_LINE ->
            "Der Balken mit gespielter und verbleibender Zeit. Ausgeschaltet zeigt ihn ein Tastendruck trotzdem kurz."
        MenuEntry.NEXT -> "Der nächste Titel aus der Warteschlange, unten neben dem Bild."
        MenuEntry.ALBUM -> "Name und Jahr des Albums unter den Künstlern."
        MenuEntry.GROUP ->
            "Spielen weitere Lautsprecher mit, steht neben der Uhrzeit etwa „Wohnzimmer + 5“ oder „Überall“."
        MenuEntry.VOLUME -> when (s.volumeDisplay) {
            VolumeDisplay.ON_CHANGE -> "Ändert sich die Lautstärke, steht sie ein paar Sekunden lang neben der Uhrzeit."
            VolumeDisplay.ALWAYS -> "Die Lautstärke steht ständig neben der Uhrzeit."
            VolumeDisplay.OFF -> "Die Lautstärke erscheint nie."
        }
        MenuEntry.BACKGROUND -> if (s.background == BackgroundMode.COVER) {
            "Die Farben des Covers füllen den Hintergrund, weich verlaufend. Der Fortschritt nimmt ihren Ton an, die Schrift einen Hauch davon."
        } else {
            "Das Bild hängt an der Wand: im Dunkeln in seinem eigenen Licht, im Hellen auf Papier."
        }
        MenuEntry.MOTION -> {
            val what = when {
                s.background == BackgroundMode.COVER && s.bars.isSet -> "Wie die Farben im Hintergrund und in den Lichtleisten fließen. "
                s.bars.isSet -> "Wie die Farben der Lichtleisten fließen – auf dem Bildschirm nur mit Coverfarben. "
                else -> "Wie die Farben im Hintergrund fließen – mit Coverfarben. "
            }
            what + when (s.motion) {
                MotionLevel.OFF -> "Aus: Sie stehen still."
                MotionLevel.GENTLE -> "Sanft: langsam ineinander, kaum zu bemerken."
                MotionLevel.MEDIUM -> "Mittel: deutlich, aber ruhig."
                MotionLevel.LIVELY -> "Lebhaft: sichtbar in Bewegung."
            }
        }
        MenuEntry.BARS -> if (!s.bars.isSet) {
            "Lichtleisten hinter dem Fernseher, etwa Philips Hue Play, leuchten in den Farben des Covers. " +
                "OK zeigt den QR-Code; am Handy wählst du sie aus Home Assistant aus."
        } else {
            "Die Lichtleisten hinter dem Fernseher leuchten in den Farben des Covers; nach der Musik sind sie wieder wie vorher. " + when (s.barLevel) {
                BarLevel.OFF -> "Aus: Sie behalten ihr eigenes Licht."
                BarLevel.SUBTLE -> "Dezent: ein Schimmer an der Wand."
                BarLevel.NORMAL -> "Normal ist so, wie Klangbild gedacht ist."
                BarLevel.STRONG -> "Kräftig: für helle Räume."
            }
        }
        MenuEntry.APPEARANCE -> when (s.appearance) {
            AppearanceMode.AUTO ->
                "Hell, solange die Sonne scheint, dunkel von Sonnenuntergang bis Sonnenaufgang – nach dem Stand der Sonne in Home Assistant."
            AppearanceMode.LIGHT -> "Immer hell: eine Wand wie Papier, dunkle Schrift."
            AppearanceMode.DARK -> "Immer dunkel: das Bild leuchtet auf schwarzer Wand."
        }
        MenuEntry.LIGHT -> if (s.background == BackgroundMode.COVER) {
            // With the cover's colours the light setting is how strong they are.
            (if (ctx.appearance == Appearance.LIGHT) "Wie\u00A0kräftig die\u00A0Farben des\u00A0Covers das\u00A0Papier tönen. "
            else "Wie\u00A0kräftig die\u00A0Farben des\u00A0Covers den\u00A0Hintergrund füllen. ") + when (s.light) {
                LightLevel.STRONG -> "Kräftig:\u00A0satte\u00A0Farben."
                LightLevel.NORMAL -> "Normal\u00A0ist so, wie Klangbild gedacht\u00A0ist."
                LightLevel.SUBTLE -> "Dezent:\u00A0gedeckte\u00A0Farben."
                LightLevel.OFF -> if (ctx.appearance == Appearance.LIGHT) "Aus:\u00A0fast\u00A0reines\u00A0Papier." else "Aus:\u00A0nur das\u00A0Bild auf\u00A0Schwarz."
            }
        } else if (ctx.appearance == Appearance.LIGHT) {
            "Wie stark die helle Wand die Farbe des Bildes annimmt. " + when (s.light) {
                LightLevel.STRONG -> "Kräftig: ein deutlicher Hauch."
                LightLevel.NORMAL -> "Normal ist so, wie Klangbild gedacht ist."
                LightLevel.SUBTLE -> "Dezent: nur eine Ahnung."
                LightLevel.OFF -> "Aus: reines Papier."
            }
        } else {
            "Wie hell das Bild die Wand um sich herum anstrahlt. " + when (s.light) {
                LightLevel.STRONG -> "Kräftig passt zu hellen Räumen."
                LightLevel.NORMAL -> "Normal ist so, wie Klangbild gedacht ist."
                LightLevel.SUBTLE -> "Dezent passt zu dunklen Abenden."
                LightLevel.OFF -> "Aus: nur das Bild, ohne Licht."
            }
        }
        MenuEntry.DAY_NIGHT ->
            "Abends wärmer, nachts gedämpfter – nach dem Stand der Sonne. Alles bleibt zu sehen."
        MenuEntry.REMOTE ->
            "OK hält die Musik an, links und rechts wechseln den Titel – am Lautsprecher in Home Assistant."
        MenuEntry.UP_DOWN -> if (s.volumeKeys) {
            "Pfeil hoch und runter ändern die Lautstärke des Lautsprechers."
        } else {
            "Pfeil hoch und runter zeigen ausgeblendete Angaben wie Uhrzeit und Fortschritt kurz an."
        }
        MenuEntry.SPOKEN -> if (s.spokenSkip) {
            "Bei Hörbüchern und Podcasts springt links 15 Sekunden zurück, rechts 30 Sekunden vor. " +
                "Gedrückt gehalten geht es zum vorigen oder nächsten Kapitel."
        } else {
            "Links und rechts wechseln auch bei Hörbüchern und Podcasts zum vorigen oder nächsten Eintrag."
        }
        MenuEntry.SLEEP_TIMER -> when {
            ctx.sleep == SleepChoice.OFF ->
                "Hält die Musik nach einer Weile an, auch am Ende eines Kapitels. Dafür muss Klangbild laufen."
            ctx.sleepAt != null -> "Die Musik hält um ${TimeLine.clock(ctx.sleepAt)} an."
            else -> "Die Musik hält an, sobald ${endPhrase(ctx.sleep, ctx.kind)} erreicht ist."
        }
        MenuEntry.SLEEP_AFTER -> "So lange bleibt der Bildschirm ohne Musik an."
        MenuEntry.SLEEP_ACTION -> if (s.sleepAction == SleepAction.CLOSE) {
            "Danach schließt sich Klangbild, zurück zum Startbildschirm."
        } else {
            "Danach übernimmt der Fernseher: Bildschirmschoner oder Standby."
        }
        MenuEntry.CONNECTION -> if (!s.isConfigured) {
            "OK\u00A0zeigt den\u00A0QR-Code. Am\u00A0Handy verbindest du Home\u00A0Assistant und\u00A0wählst den\u00A0Lautsprecher."
        } else {
            "OK\u00A0zeigt den\u00A0QR-Code. Am\u00A0Handy änderst du Adresse, Zugangstoken und\u00A0Lautsprecher."
        }
        MenuEntry.MUSIC_ASSISTANT -> if (!s.hasMusicAssistant) {
            "Freiwillig. Mit\u00A0Music\u00A0Assistant zeigt Cover\u00A0Flow die\u00A0ganze Warteschlange – was\u00A0lief und\u00A0was\u00A0kommt. " +
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
        MenuEntry.DAY_NIGHT -> s.copy(dayNight = !s.dayNight)
        MenuEntry.REMOTE -> s.copy(remoteControl = !s.remoteControl)
        MenuEntry.UP_DOWN -> s.copy(volumeKeys = !s.volumeKeys)
        MenuEntry.SPOKEN -> s.copy(spokenSkip = !s.spokenSkip)
        MenuEntry.SLEEP_AFTER -> s.copy(sleepAfterMinutes = nextSleep(s.sleepAfterMinutes, direction, wrap))
        MenuEntry.SLEEP_ACTION -> s.copy(
            sleepAction = if (s.sleepAction == SleepAction.CLOSE) SleepAction.RELEASE else SleepAction.CLOSE,
        )
        MenuEntry.CONNECTION, MenuEntry.MUSIC_ASSISTANT, MenuEntry.SLEEP_TIMER -> null
    }

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
    private val BAR_ORDER = listOf(BarLevel.OFF, BarLevel.SUBTLE, BarLevel.NORMAL, BarLevel.STRONG)

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
