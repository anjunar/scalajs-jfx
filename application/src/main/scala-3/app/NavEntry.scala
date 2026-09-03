package app

import jfx.core.i18n.{I18nLocale, RuntimeMessage}

final case class NavEntry(
    zoneMessage: RuntimeMessage,
    titleMessage: RuntimeMessage,
    copyMessage: RuntimeMessage,
    path: String
) {

  def matches(currentPath: String): Boolean =
    if (path == "/") currentPath == "/"
    else currentPath == path || currentPath.startsWith(s"$path/")

  def title(locale: I18nLocale): String =
    AppI18n.resolve(titleMessage, locale)

  /** The sidebar's subtitle, which doubles as the page's meta description. Not named `copy`: that
    * is the case class's own method.
    */
  def description(locale: I18nLocale): String =
    AppI18n.resolve(copyMessage, locale)
}
