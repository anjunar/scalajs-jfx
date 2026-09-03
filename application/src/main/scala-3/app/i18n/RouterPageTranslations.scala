package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object RouterPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Router & route model", "Router & Routenmodell"),
    de(
      i18n"Base path, locale prefix and explicit route context now live in one coherent flow.",
      "Basispfad, Locale-Präfix und expliziter Routenkontext bilden jetzt einen zusammenhängenden Ablauf."
    ),
    de(i18n"Contract", "Vertrag"),
    de(i18n"Only async routes remain", "Es bleiben nur asynchrone Routen"),
    de(
      i18n"The route loader always receives a RouteContext and always returns a Future[AbstractComponent]. There is no second synchronous API surface to drift away anymore.",
      "Der Routen-Loader erhält immer einen RouteContext und liefert immer ein Future[AbstractComponent]. Es gibt keine zweite synchrone API-Oberfläche mehr, die davon abweichen könnte."
    ),
    de(i18n"Nested route demo", "Nested-Route-Demo"),
    de(
      i18n"The button activates a child route. Its component appears below through the parent's routerOutlet().",
      "Der Button aktiviert eine Child-Route. Ihre Komponente erscheint darunter über routerOutlet() des Parents."
    ),
    de(i18n"Open /router/user/42", "/router/user/42 öffnen"),
    de(i18n"Current route shape", "Aktuelle Routenstruktur"),
    de(
      i18n"The demo uses the same API as downstream applications would.",
      "Die Demo verwendet dieselbe API wie nachgelagerte Anwendungen."
    )
  )
}
