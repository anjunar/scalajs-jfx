package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object ButtonPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Button", "Button"),
    de(i18n"The pulse of your app.", "Der Puls deiner App."),
    de(i18n"Interaction", "Interaktion"),
    de(
      i18n"A button is small, but it carries responsibility.",
      "Ein Button ist klein, trägt aber Verantwortung."
    ),
    de(
      i18n"In JFX2 the action stays visible in the template: label, event, and surrounding context sit next to each other. That keeps simple buttons easy to read and leaves room for more complex workflows later.",
      "In JFX2 bleibt die Aktion im Template sichtbar: Beschriftung, Ereignis und umgebender Kontext stehen direkt beieinander. Dadurch bleiben einfache Buttons leicht lesbar und es bleibt Raum für komplexere Abläufe."
    ),
    de(i18n"Standard button", "Standard-Button"),
    de(
      i18n"A focused click target with direct event binding. Ideal for clear, self-contained actions.",
      "Ein klarer Klickbereich mit direkter Ereignisbindung. Ideal für eindeutige, in sich geschlossene Aktionen."
    ),
    de(
      i18n"Buttons are the heart of interaction. They are not just click targets; they bring your app to life.",
      "Buttons sind das Herz der Interaktion. Sie sind nicht nur Klickziele, sondern erwecken deine App zum Leben."
    ),
    de(i18n"Click me and bring me to life", "Klick mich und erwecke mich zum Leben"),
    de(i18n"I was clicked! The magic begins.", "Ich wurde angeklickt! Die Magie beginnt."),
    de(i18n"Action group", "Aktionsgruppe"),
    de(
      i18n"Several buttons may sit close together as long as their intent remains distinguishable.",
      "Mehrere Buttons können dicht beieinanderstehen, solange ihre Absicht klar unterscheidbar bleibt."
    ),
    de(i18n"Save", "Speichern"),
    de(i18n"Saved.", "Gespeichert."),
    de(i18n"Check", "Prüfen"),
    de(i18n"Checked.", "Geprüft."),
    de(i18n"Reset", "Zurücksetzen"),
    de(i18n"Reset.", "Zurückgesetzt."),
    de(i18n"State", "Zustand"),
    de(i18n"The button says what happens", "Der Button sagt, was passiert"),
    de(
      i18n"A good label describes the next action, not the technical implementation behind it.",
      "Eine gute Beschriftung beschreibt die nächste Aktion, nicht die technische Umsetzung dahinter."
    ),
    de(i18n"Event", "Ereignis"),
    de(i18n"onClick stays local", "onClick bleibt lokal"),
    de(
      i18n"The DSL keeps trigger and reaction visible in the same place.",
      "Die DSL hält Auslöser und Reaktion am selben Ort sichtbar."
    ),
    de(i18n"Feedback", "Rückmeldung"),
    de(i18n"Actions need a response", "Aktionen brauchen eine Reaktion"),
    de(
      i18n"After the click, the interface should show something visible: a message, status, navigation, or data update.",
      "Nach dem Klick sollte die Oberfläche eine sichtbare Reaktion zeigen: eine Nachricht, einen Status, eine Navigation oder eine Datenaktualisierung."
    ),
    de(i18n"The simplicity of the DSL", "Die Einfachheit der DSL"),
    de(
      i18n"The core stays intentionally small: create the button, bind the handler, done.",
      "Der Kern bleibt bewusst klein: Button erstellen, Handler binden, fertig."
    )
  )
}
