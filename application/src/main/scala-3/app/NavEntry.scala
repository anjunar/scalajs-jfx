package app

import jfx.i18n.{I18nLocale, RuntimeMessage}

final case class NavEntry(
    zoneMessage: RuntimeMessage,
    titleMessage: RuntimeMessage,
    copyMessage: RuntimeMessage,
    path: String
) {

  def matches(currentPath: String): Boolean =
    if (path == "/") currentPath == "/"
    else currentPath == path || currentPath.startsWith(s"$path/")

  def zone(locale: I18nLocale): String =
    AppI18n.resolve(zoneMessage, locale)

  def title(locale: I18nLocale): String =
    AppI18n.resolve(titleMessage, locale)
}
