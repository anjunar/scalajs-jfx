package jfx.router

import jfx.i18n.I18nLocale
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RouterUrlResolverSpec extends AnyFlatSpec with Matchers {

  private val config =
    RouterConfig(
      basePath = "/scalajs-jfx",
      supportedLocales = Seq(I18nLocale("de"), I18nLocale.En)
    )

  "RouterUrlResolver" should "strip base path and locale before route matching" in {
    val resolved =
      RouterUrlResolver.resolve("/scalajs-jfx/de/about?tab=details", config)

    resolved.path shouldBe "/about"
    resolved.browserPath shouldBe "/scalajs-jfx/de/about"
    resolved.search shouldBe "?tab=details"
    resolved.queryParams shouldBe Map("tab" -> "details")
    resolved.locale shouldBe Some(I18nLocale("de"))
  }

  it should "preserve the current locale for locale-neutral navigations" in {
    val resolved =
      RouterUrlResolver.resolve("/about", config, preferredLocale = Some(I18nLocale("de")))

    resolved.path shouldBe "/about"
    resolved.browserPath shouldBe "/scalajs-jfx/de/about"
    resolved.locale shouldBe Some(I18nLocale("de"))
  }

  it should "leave non-localized routes untouched when no locale is active" in {
    val resolved =
      RouterUrlResolver.resolve("/scalajs-jfx/about", config)

    resolved.path shouldBe "/about"
    resolved.browserPath shouldBe "/scalajs-jfx/about"
    resolved.locale shouldBe None
  }
}
