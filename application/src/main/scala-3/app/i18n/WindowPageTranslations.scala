package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object WindowPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Window & viewport", "Fenster & Viewport"),
    de(i18n"The architecture of space.", "Die Architektur des Raums."),
    de(i18n"Space management", "Raumverwaltung"),
    de(
      i18n"The viewport is the stage for things that live above the page.",
      "Der Viewport ist die Bühne für Dinge, die über der Seite liegen."
    ),
    de(
      i18n"Notifications, windows, and overlays need a central order. JFX2 gathers those concerns in the viewport so pages do not have to manage global UI layers on their own.",
      "Benachrichtigungen, Fenster und Overlays brauchen eine zentrale Ordnung. JFX2 bündelt diese Belange im Viewport, sodass Seiten globale UI-Ebenen nicht selbst verwalten müssen."
    ),
    de(i18n"Notify", "Benachrichtigen"),
    de(
      i18n"Short feedback without losing context.",
      "Kurze Rückmeldung, ohne den Kontext zu verlieren."
    ),
    de(i18n"Window", "Fenster"),
    de(i18n"Focused work surfaces above the page.", "Fokussierte Arbeitsflächen über der Seite."),
    de(i18n"Overlay", "Overlay"),
    de(
      i18n"Anchor-bound surfaces for selection and details.",
      "An Anker gebundene Flächen für Auswahl und Details."
    ),
    de(i18n"Viewport", "Viewport"),
    de(
      i18n"Four notification types show how global feedback is triggered from a page.",
      "Vier Benachrichtigungstypen zeigen, wie eine Seite globale Rückmeldungen auslöst."
    ),
    de(
      i18n"The viewport is the quiet center that carries windows, notifications, and overlays. It brings order to the chaos and gives it a stage.",
      "Der Viewport ist das ruhige Zentrum, das Fenster, Benachrichtigungen und Overlays trägt. Er bringt Ordnung ins Chaos und gibt ihm eine Bühne."
    ),
    de(i18n"Info notification", "Info-Benachrichtigung"),
    de(i18n"Silence is the origin of every form.", "Stille ist der Ursprung jeder Form."),
    de(i18n"Success notification", "Erfolgsbenachrichtigung"),
    de(i18n"The structure is now sound.", "Die Struktur ist jetzt stabil."),
    de(i18n"Warning notification", "Warnungsbenachrichtigung"),
    de(i18n"Warning: the form may be hardening.", "Warnung: Die Form könnte sich verhärten."),
    de(i18n"Error notification", "Fehlerbenachrichtigung"),
    de(i18n"A crack in the foundation was discovered.", "Ein Riss im Fundament wurde entdeckt."),
    de(
      i18n"A window remains in the global viewport while the page underneath keeps its state.",
      "Ein Fenster bleibt im globalen Viewport, während die darunterliegende Seite ihren Zustand behält."
    ),
    de(
      i18n"Windows are movable islands in the viewport. They allow focus without losing context.",
      "Fenster sind bewegliche Inseln im Viewport. Sie ermöglichen Fokus, ohne den Kontext zu verlieren."
    ),
    de(i18n"Open window", "Fenster öffnen"),
    de(i18n"A room for thoughts", "Ein Raum für Gedanken"),
    de(i18n"There is room for your ideas here.", "Hier ist Raum für deine Ideen."),
    de(i18n"Confirm note", "Notiz bestätigen"),
    de(i18n"The note in the window was confirmed.", "Die Notiz im Fenster wurde bestätigt."),
    de(i18n"Center", "Zentrum"),
    de(i18n"Global UI belongs in one place", "Globale UI gehört an einen Ort"),
    de(
      i18n"Viewport.windows and Viewport.notifications form the state of the top UI layer.",
      "Viewport.windows und Viewport.notifications bilden den Zustand der obersten UI-Ebene."
    ),
    de(i18n"Focus", "Fokus"),
    de(i18n"Windows keep Z-index and activity", "Fenster verwalten Z-Index und Aktivität"),
    de(
      i18n"The viewport can touch, arrange, and close windows without duplicating page logic.",
      "Der Viewport kann Fenster aktivieren, anordnen und schließen, ohne Seitenlogik zu duplizieren."
    ),
    de(i18n"Readability", "Lesbarkeit"),
    de(i18n"Pages only trigger intents", "Seiten lösen nur Absichten aus"),
    de(
      i18n"The page says notify or addWindow, the viewport handles presentation and lifecycle.",
      "Die Seite sagt notify oder addWindow; der Viewport übernimmt Darstellung und Lebenszyklus."
    ),
    de(i18n"Usage", "Verwendung"),
    de(
      i18n"The page binds the viewport once and then only sends clear intents.",
      "Die Seite bindet den Viewport einmal und sendet danach nur noch klare Absichten."
    )
  )
}
