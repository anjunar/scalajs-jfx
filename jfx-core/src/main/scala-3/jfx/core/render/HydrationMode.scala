package jfx.core.render

enum HydrationMode {

  /** Jeder server-gerenderte Knoten muss vom Client-Baum beansprucht werden. */
  case Strict

  /** `<head>`: Reihenfolge und Vollstaendigkeit sind dort nicht garantiert. */
  case Head

  /** Der Bereich wird uebernommen, ohne seinen Inhalt zu pruefen.
    *
    * Gedacht fuer den Fall, dass der Client die Knoten noch nicht nachbauen kann -- etwa weil ein
    * Route-Loader noch laeuft. Der SSR-Inhalt bleibt sichtbar und wird ersetzt, sobald der Client
    * seinen eigenen Baum hat. Siehe CHANGE.md P4-1.
    */
  case Adopt
}
