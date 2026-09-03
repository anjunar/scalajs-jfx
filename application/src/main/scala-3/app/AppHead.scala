package app

import jfx.core.component.AbstractComponent
import jfx.core.document.{DocumentHead, HeadEntry}
import jfx.core.i18n.{I18nLocale, I18nRuntime}
import jfx.router.Router

/** Everything the demo puts into the document head.
  *
  * Until the head was rendered from Scala this lived in `index.html` and was filled at build time,
  * which meant every route shipped the same title, description and canonical URL -- also under
  * `/de/…`. Here the site-wide entries are registered once and the page-dependent ones are replaced
  * on every navigation and locale switch. See REVIEW.md B-1.
  */
final class AppHead(
    documentHead: DocumentHead,
    router: Router,
    i18n: I18nRuntime,
    theme: AppTheme,
    navigation: Seq[NavEntry]
) {

  def install(owner: AbstractComponent): Unit = {
    documentHead.batch {
      siteEntries.foreach(entry => owner.addDisposable(documentHead.push(entry)))
    }

    val page = documentHead.handle(owner)

    owner.addDisposable(router.state.observe(_ => updatePage(page)))
    owner.addDisposable(i18n.locale.observeWithoutInitial(_ => updatePage(page)))

    val themeColor = documentHead.handle(owner)

    owner.addDisposable(
      theme.modeProperty.observe { mode =>
        themeColor.set(
          HeadEntry.meta(
            "theme-color",
            if (mode == AppTheme.Mode.Dark) "#171918" else "#eee9e1"
          )
        )
      }
    )
  }

  /** Registered once. `charset` comes first because it has to. */
  private def siteEntries: Seq[HeadEntry] =
    Seq(
      HeadEntry.charset(),
      HeadEntry.base(baseHref),
      HeadEntry.meta("viewport", "width=device-width, initial-scale=1.0"),
      HeadEntry.meta(
        "keywords",
        "Scala.js, Scala UI framework, reactive UI, JavaFX DSL, typed forms, " +
          "client-side routing, lifecycle management"
      ),
      HeadEntry.meta("author", SiteConfig.author),
      HeadEntry.link(
        "stylesheet",
        "https://fonts.googleapis.com/icon?family=Material+Icons"
      ),
      HeadEntry.link("icon", "favicon.svg", "type" -> "image/svg+xml"),
      HeadEntry.property("og:type", "website"),
      HeadEntry.property("og:site_name", SiteConfig.name),
      HeadEntry.property("og:image", s"${SiteConfig.siteUrl}/og-image.svg"),
      HeadEntry.meta("twitter:card", "summary_large_image"),
      HeadEntry.meta("twitter:image", s"${SiteConfig.siteUrl}/og-image.svg"),
      HeadEntry.jsonLd("ld:software", softwareSourceCode),
      HeadEntry.inlineScript("theme-init", themeInitScript)
    )

  /** Replaced on every navigation and on every locale switch. */
  private def updatePage(page: DocumentHead.Handle): Unit = {
    val state  = router.state.get
    val locale = i18n.locale.get
    val entry  = navigation.find(_.matches(state.path))
    val found  = state.currentMatchOption.isDefined

    val title =
      if (!found) s"${notFoundTitle(locale)} | ${SiteConfig.name}"
      else
        entry.filter(_.path != "/") match {
          case Some(matched) => s"${matched.title(locale)} | ${SiteConfig.name}"
          case None          => SiteConfig.title
        }

    val description =
      entry.filter(_.path != "/").map(_.description(locale)).getOrElse(SiteConfig.description)

    val canonical = canonicalUrl(state.path, locale)

    documentHead.htmlAttribute("lang", locale.code)

    page.set(
      Seq(
        HeadEntry.title(title),
        HeadEntry.meta("description", description),
        HeadEntry.meta("robots", if (found) "index, follow" else "noindex, nofollow"),
        HeadEntry.link("canonical", canonical),
        HeadEntry.property("og:title", title),
        HeadEntry.property("og:description", description),
        HeadEntry.property("og:url", canonical),
        HeadEntry.meta("twitter:title", title),
        HeadEntry.meta("twitter:description", description)
      ) ++ alternates(state.path)*
    )
  }

  /** One `hreflang` link per supported locale, plus `x-default` on the default one. Without them a
    * crawler reads `/de/router` and `/en/router` as two competing pages.
    */
  private def alternates(path: String): Seq[HeadEntry] =
    i18n.supportedLocales.map { locale =>
      HeadEntry.alternate(locale.code, canonicalUrl(path, locale))
    } :+ HeadEntry.alternate("x-default", canonicalUrl(path, i18n.defaultLocale))

  private def canonicalUrl(path: String, locale: I18nLocale): String = {
    val browserPath =
      router.localizedPath(path, locale).stripPrefix(SiteConfig.basePath)

    if (browserPath.isEmpty || browserPath == "/") s"${SiteConfig.siteUrl}/"
    else s"${SiteConfig.siteUrl}$browserPath/"
  }

  private def notFoundTitle(locale: I18nLocale): String =
    if (locale == AppI18n.German) "Seite nicht gefunden" else "Page not found"

  private def baseHref: String =
    if (SiteConfig.basePath.isEmpty) "/" else s"${SiteConfig.basePath}/"

  private def softwareSourceCode: String =
    s"""{
       |  "@context": "https://schema.org",
       |  "@type": "SoftwareSourceCode",
       |  "name": "${SiteConfig.name}",
       |  "description": "${SiteConfig.shortDescription}",
       |  "programmingLanguage": "Scala",
       |  "runtimePlatform": "Scala.js",
       |  "license": "https://opensource.org/licenses/MIT",
       |  "codeRepository": "${SiteConfig.codeRepository}",
       |  "url": "${SiteConfig.siteUrl}/",
       |  "author": {
       |    "@type": "Person",
       |    "name": "${SiteConfig.author}",
       |    "url": "${SiteConfig.authorUrl}"
       |  }
       |}""".stripMargin

  /** Runs before the first paint and sets `data-theme`, so the page does not start in the wrong
    * theme and correct itself once the bundle is loaded. It is the one piece of the head that has to
    * be a script: the server cannot know what the visitor stored.
    *
    * [[AppTheme.BrowserEffects]] takes over from there and writes the same attribute.
    */
  private def themeInitScript: String =
    s"""(() => {
       |  try {
       |    const key = '${SiteConfig.themeStorageKey}'
       |    const stored = localStorage.getItem(key)
       |    const preferred = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
       |    document.documentElement.dataset.theme = stored === 'dark' || stored === 'light' ? stored : preferred
       |  } catch (error) {
       |    document.documentElement.dataset.theme = 'light'
       |  }
       |})()""".stripMargin
}
