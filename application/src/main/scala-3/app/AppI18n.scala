package app

import app.i18n.*
import jfx.core.state.ReadOnlyProperty
import jfx.i18n.*

object AppI18n {
  val German: I18nLocale  = I18nLocale("de")
  val English: I18nLocale = I18nLocale.En

  def localeLabel(locale: ReadOnlyProperty[I18nLocale]): ReadOnlyProperty[String] =
    locale.map {
      case German => "DE"
      case _      => "EN"
    }

  def resolve(message: RuntimeMessage, locale: I18nLocale): String =
    resolver.resolve(message, locale)

  val catalog: MessageCatalog =
    TranslationSupport.catalog(
      AppTranslations.entries,
      OverviewPageTranslations.entries,
      ButtonPageTranslations.entries,
      ImagePageTranslations.entries,
      LayoutPageTranslations.entries,
      WindowPageTranslations.entries,
      RouterPageTranslations.entries,
      RouterUserPageTranslations.entries,
      I18nPageTranslations.entries,
      RenderingPageTranslations.entries,
      StatePageTranslations.entries,
      FormsPageTranslations.entries,
      ImageCropperPageTranslations.entries,
      EditorPageTranslations.entries,
      TabsPageTranslations.entries,
      CarouselPageTranslations.entries,
      ComboBoxPageTranslations.entries,
      TableViewPageTranslations.entries,
      DataGridPageTranslations.entries,
      VirtualListViewPageTranslations.entries,
      ViewportPageTranslations.entries
    )

  private val resolver =
    I18nResolver(catalog)

  val config: I18nConfig =
    I18nConfig(
      resolver = resolver,
      supportedLocales = Seq(German, English),
      defaultLocale = English
    )
}
