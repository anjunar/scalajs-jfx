package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.i18n.{I18nRuntime, i18n}

object I18nPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val locale =
      I18nRuntime.require.locale

    Showcase.showcasePage(
      i18n"i18n & locale routing",
      i18n"The toolbar locale switch now aligns with locale-prefixed routes instead of living beside them."
    ) {
      Showcase.sectionIntro(
        i18n"Direction",
        i18n"URL locale first, message locale second",
        i18n"The route decides the current locale. Text helpers then resolve visible copy from that one property."
      )

      Showcase.metricStrip(
        "current" -> locale.map(_.code).get,
        "prefixes" -> "/de, /en",
        "fallback" -> "en"
      )

      Showcase.insightGrid(
        ("Route", "Locale is part of the path", "Direct URLs, SSR and client navigation now agree on the same prefix semantics."),
        ("Toolbar", "Switch keeps the current page", "Changing locale rewrites the URL but preserves the matched application path."),
        ("Catalog", "Ready for message-based i18n", "The repository already contains a richer i18n model that can replace the lightweight demo copy step by step.")
      )

      Showcase.apiSection(
        i18n"Lightweight demo copy",
        i18n"The visual design is ported first; the full message catalog can grow from here."
      ) {
        Showcase.codeBlock(
          "scala",
          """DemoI18n.text(i18n"Router", localeProperty)""".stripMargin
        )
      }
    }
  }
}
