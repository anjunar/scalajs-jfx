package app

import jfx.core.state.ReadOnlyProperty
import jfx.i18n.*

object DemoI18n {
  val German: I18nLocale  = I18nLocale("de")
  val English: I18nLocale = I18nLocale.En

  def localeLabel(locale: ReadOnlyProperty[I18nLocale]): ReadOnlyProperty[String] =
    locale.map {
      case German => "DE"
      case _      => "EN"
    }

  def runtime(locale: ReadOnlyProperty[I18nLocale]): I18nRuntime =
    I18nRuntime(locale, resolver)

  def text(message: RuntimeMessage, locale: ReadOnlyProperty[I18nLocale]): ReadOnlyProperty[String] =
    resolver.resolve(message, locale)

  def resolve(message: RuntimeMessage, locale: I18nLocale): String =
    resolver.resolve(message, locale)

  val catalog: MessageCatalog =
    MessageCatalog(
      de(i18n"JFX API", "JFX API"),
      de(i18n"Foundation", "Foundation"),
      de(i18n"Discover", "Entdecken"),
      de(i18n"Start", "Start"),
      de(i18n"Router", "Router"),
      de(i18n"Paths, locale and loaders", "Pfade, Locale und Loader"),
      de(i18n"i18n", "i18n"),
      de(i18n"Toolbar locale meets URL locale", "Toolbar-Locale trifft URL-Locale"),
      de(i18n"Runtime", "Runtime"),
      de(i18n"Rendering", "Rendering"),
      de(i18n"SSR, hydration and shell stability", "SSR, Hydration und Shell-Stabilität"),
      de(i18n"State", "State"),
      de(i18n"Reactive properties in plain sight", "Reaktive Properties sichtbar gemacht"),
      de(i18n"Composition", "Composition"),
      de(i18n"Forms", "Forms"),
      de(i18n"Control registration and context", "Control-Registrierung und Kontext"),
      de(i18n"Viewport", "Viewport"),
      de(i18n"Notifications and windows", "Benachrichtigungen und Fenster"),
      de(i18n"Design inherited from JFX2, content rebuilt for scalajs-jfx.", "Design aus JFX2 übernommen, Inhalt für scalajs-jfx neu aufgebaut."),
      de(i18n"Light", "Hell"),
      de(i18n"Dark", "Dunkel"),
      de(i18n"Pure Scala.js architecture, rebuilt around the modules that actually exist here.", "Pure Scala.js-Architektur, neu aufgebaut um die Module, die hier tatsächlich existieren."),
      de(i18n"Scala.js UI architecture", "Scala.js UI-Architektur"),
      de(i18n"A fresh demo, rebuilt around the actual scalajs-jfx modules.", "Eine frische Demo, neu aufgebaut um die echten scalajs-jfx-Module."),
      de(i18n"The visual language mirrors the JFX2 showcase, but the pages here are written specifically for this repository: router, i18n, viewport, forms and rendering infrastructure.", "Die visuelle Sprache spiegelt die JFX2-Showcase, aber die Seiten hier sind speziell für dieses Repository geschrieben: Router, i18n, Viewport, Forms und Rendering-Infrastruktur."),
      de(i18n"The shell is familiar. The story is new.", "Die Hülle ist vertraut. Die Erzählung ist neu."),
      de(i18n"This demo is intentionally narrower than JFX2: it shows the real building blocks that exist in this repository and avoids pretending that missing modules are already here.", "Diese Demo ist bewusst schmaler als JFX2: Sie zeigt die echten Bausteine dieses Repositories und tut nicht so, als wären fehlende Module schon vorhanden."),
      de(i18n"Open router docs", "Router-Doku öffnen"),
      de(i18n"Open", "Öffnen"),
      de(i18n"Router & route model", "Router & Routenmodell"),
      de(i18n"Base path, locale prefix and explicit route context now live in one coherent flow.", "Base Path, Locale-Präfix und expliziter RouteContext leben jetzt in einem kohärenten Fluss."),
      de(i18n"Contract", "Vertrag"),
      de(i18n"Only async routes remain", "Es gibt nur noch asynchrone Routen"),
      de(i18n"The route loader always receives a RouteContext and always returns a Future[AbstractComponent]. There is no second synchronous API surface to drift away anymore.", "Der Route-Loader bekommt immer einen RouteContext und liefert immer ein Future[AbstractComponent]. Es gibt keine zweite synchrone API-Fläche mehr, die auseinanderlaufen kann."),
      de(i18n"Route context demo", "Route-Context-Demo"),
      de(i18n"This button leads to a route with an explicit path parameter.", "Dieser Button führt zu einer Route mit explizitem Path-Parameter."),
      de(i18n"Open /router/user/42", "Öffne /router/user/42"),
      de(i18n"Current route shape", "Aktuelle Routenform"),
      de(i18n"The demo uses the same API as downstream applications would.", "Die Demo verwendet dieselbe API wie nachgelagerte Anwendungen."),
      de(i18n"Explicit route context", "Expliziter RouteContext"),
      de(i18n"This page exists to prove that path params no longer arrive through Route.requireContext.", "Diese Seite beweist, dass Path-Parameter nicht mehr über Route.requireContext hereinkommen."),
      de(i18n"Loader input", "Loader-Eingabe"),
      de(i18n"The route parameter is read directly from the loader argument.", "Der Routenparameter wird direkt aus dem Loader-Argument gelesen."),
      de(i18n"i18n & locale routing", "i18n & Locale-Routing"),
      de(i18n"The toolbar locale switch now aligns with locale-prefixed routes instead of living beside them.", "Der Locale-Schalter in der Toolbar richtet sich jetzt an locale-präfigierten Routen aus, statt neben ihnen herzulaufen."),
      de(i18n"Direction", "Richtung"),
      de(i18n"URL locale first, message locale second", "Zuerst URL-Locale, dann Message-Locale"),
      de(i18n"The route decides the current locale. Text helpers then resolve visible copy from that one property.", "Die Route entscheidet die aktuelle Locale. Text-Helfer lösen sichtbare Texte dann aus genau dieser einen Property auf."),
      de(i18n"Lightweight demo copy", "Leichte Demo-Texte"),
      de(i18n"The visual design is ported first; the full message catalog can grow from here.", "Das visuelle Design wurde zuerst portiert; der vollständige Message-Katalog kann von hier aus wachsen."),
      de(i18n"Rendering, SSR & hydration", "Rendering, SSR & Hydration"),
      de(i18n"The app shell is server-rendered, hydrated on the client and still keeps route loading honest.", "Die App-Hülle wird serverseitig gerendert, im Client hydriert und hält das Route-Loading trotzdem ehrlich."),
      de(i18n"Boot flow", "Boot-Ablauf"),
      de(i18n"Client and SSR both hand the initial URL to App explicitly.", "Client und SSR geben die Initial-URL beide explizit an App weiter."),
      de(i18n"Reactive state", "Reaktiver State"),
      de(i18n"Properties are still the smallest honest abstraction in the system.", "Properties sind weiterhin die kleinste ehrliche Abstraktion im System."),
      de(i18n"Counter", "Zähler"),
      de(i18n"A tiny interaction is enough to make the data flow visible.", "Eine kleine Interaktion reicht, um den Datenfluss sichtbar zu machen."),
      de(i18n"The visible text is derived directly from a Property[Int].", "Der sichtbare Text wird direkt aus einer Property[Int] abgeleitet."),
      de(i18n"Increment", "Erhöhen"),
      de(i18n"Reset", "Zurücksetzen"),
      de(i18n"Forms architecture", "Form-Architektur"),
      de(i18n"The demo documents the form model without pretending that JFX2 controls already exist here.", "Die Demo dokumentiert das Form-Modell, ohne so zu tun, als gäbe es hier schon die komplette JFX2-Control-Suite."),
      de(i18n"Focus", "Fokus"),
      de(i18n"Registration, control contract and shared context", "Registrierung, Control-Vertrag und geteilter Kontext"),
      de(i18n"jfx-forms is present in this repository, but the visual showcase is rewritten around the architecture instead of copying a feature matrix from another project.", "jfx-forms ist in diesem Repository vorhanden, aber die visuelle Showcase wird um die Architektur herum neu geschrieben statt eine Feature-Matrix aus einem anderen Projekt zu kopieren."),
      de(i18n"Lifecycle states", "Lifecycle-Zustände"),
      de(i18n"These buttons describe how the form stack is wired.", "Diese Buttons beschreiben, wie der Form-Stack verdrahtet ist."),
      de(i18n"Registration", "Registrierung"),
      de(i18n"Binding", "Binding"),
      de(i18n"Validation", "Validierung"),
      de(i18n"Inputs register themselves through FormContext so the form owns a concrete field map.", "Inputs registrieren sich über FormContext, sodass das Formular eine konkrete Field-Map besitzt."),
      de(i18n"Controls then expose their own contract for reading and writing values.", "Controls exponieren anschließend ihren eigenen Vertrag für Lesen und Schreiben von Werten."),
      de(i18n"Validation stays near the control layer instead of hiding in a remote action handler.", "Validierung bleibt nah an der Control-Schicht statt sich in einem entfernten Action-Handler zu verstecken."),
      de(i18n"Current primitives", "Aktuelle Primitive"),
      de(i18n"What exists right now in this repository.", "Was in diesem Repository aktuell wirklich existiert."),
      de(i18n"Viewport surfaces", "Viewport-Flächen"),
      de(i18n"Notifications and windows are still one of the strongest interactive stories in this repository.", "Benachrichtigungen und Fenster sind weiterhin eine der stärksten interaktiven Geschichten in diesem Repository."),
      de(i18n"Interactive stage", "Interaktive Bühne"),
      de(i18n"Open a notification or a window from the routed page.", "Öffne eine Benachrichtigung oder ein Fenster direkt aus der gerouteten Seite."),
      de(i18n"Notify", "Benachrichtigen"),
      de(i18n"Viewport notification from the rebuilt demo.", "Viewport-Benachrichtigung aus der neu aufgebauten Demo."),
      de(i18n"Open window", "Fenster öffnen"),
      de(i18n"Viewport window", "Viewport-Fenster"),
      de(i18n"Global viewport window", "Globales Viewport-Fenster"),
      de(i18n"This content is mounted into the shared viewport layer, not into the route subtree.", "Dieser Inhalt wird in die geteilte Viewport-Schicht gemountet, nicht in den Routen-Subtree.")
    )

  private val resolver =
    I18nResolver(catalog)

  private def de(message: RuntimeMessage, translation: String): CatalogEntry =
    I18n.entry(message.key).translations(
      German -> translation
    )
}
