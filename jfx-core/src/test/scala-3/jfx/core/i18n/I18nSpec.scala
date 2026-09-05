package jfx.core.i18n

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.state.Property
import jfx.core.text.TextValue
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class I18nSpec extends AnyFlatSpec with Matchers {

  "i18n interpolator" should "use the full English source as visible identity" in {
    val user  = "Mira"
    val group = "Design"

    val message = i18n"User $user invited you to $group"

    message.key.source shouldBe "User {user} invited you to {group}"
    message.key.placeholders shouldBe Vector("user", "group")
    message.args.map(arg => arg.name -> arg.value).toMap shouldBe Map(
      "user"  -> "Mira",
      "group" -> "Design"
    )
  }

  it should "support explicit placeholder names for expressions" in {
    val user = User("Mira")

    val message = i18n"User ${I18n.named("user", user.name)} signed in"

    message.key.source shouldBe "User {user} signed in"
    message.args.head.value shouldBe "Mira"
  }

  it should "resolve message-centered multilingual catalog entries with locale fallback" in {
    val count   = 3
    val message = i18n"$count documents selected"
    val catalog = MessageCatalog(
      I18n
        .entry(message.key)
        .translations(
          I18nLocale("de") -> "{count} Dokumente ausgewahlt",
          I18nLocale("fr") -> "{count} documents selectionnes"
        )
    )
    val resolver = I18nResolver(catalog)

    resolver.resolve(message, I18nLocale("de-AT")) shouldBe "3 Dokumente ausgewahlt"
    resolver.resolve(message, I18nLocale("es")) shouldBe "3 documents selected"
  }

  it should "produce reactive text from a reactive locale" in {
    val locale  = Property(I18nLocale("en"))
    val message = i18n"Delete document"
    val catalog = MessageCatalog(
      I18n
        .entry(message.key)
        .translations(
          I18nLocale("de") -> "Dokument loschen"
        )
    )

    val text = I18nResolver(catalog).resolve(message, locale)

    text.get shouldBe "Delete document"
    locale.set(I18nLocale("de"))
    text.get shouldBe "Dokument loschen"
  }

  it should "resolve runtime messages through the component i18n context" in {
    val locale  = Property(I18nLocale("en"))
    val message = i18n"Delete document"
    val catalog = MessageCatalog(
      I18n
        .entry(message.key)
        .translations(
          I18nLocale("de") -> "Dokument loschen"
        )
    )

    val root                = new AbstractCustomComponent {}
    given AbstractComponent = root

    I18nRuntime.provide(I18nRuntime(locale, I18nResolver(catalog)))
    val text = TextValue.asReadOnlyProperty(message)

    text.get shouldBe "Delete document"
    locale.set(I18nLocale("de"))
    text.get shouldBe "Dokument loschen"
  }

  it should "change the locale of a directly constructed runtime" in {
    val locale  = Property(I18nLocale.En)
    val runtime = I18nRuntime(locale, I18nResolver(MessageCatalog.empty))

    runtime.setLocale(I18nLocale("de"))

    runtime.locale.get shouldBe I18nLocale("de")
  }

  it should "interpolate every placeholder in one pass" in {
    val first   = "{second}"
    val second  = "$5\\replacement"
    val message = i18n"$first then $second"
    val catalog = MessageCatalog(
      I18n
        .entry(message.key)
        .translations(I18nLocale("de") -> "{first} und {second}")
    )

    I18nResolver(catalog).resolve(message, I18nLocale("de")) shouldBe
      "{second} und $5\\replacement"
  }

  it should "compute the fingerprint the TypeScript facade must reproduce byte-for-byte" in {
    // `npm/jfx-core/src/i18n.ts` has no macro, so it re-derives `MessageKey.fingerprint` at
    // runtime from the same FNV-1a over the same reconstructed source string
    // (`I18nMacros.fingerprintOf`). This fixture is the parity proof between the two: its
    // TypeScript twin (`i18n.test.ts`, "matches the Scala macro's fingerprint byte-for-byte")
    // asserts the identical literal. If either side's algorithm ever drifts, exactly one of
    // these two tests goes red.
    val message = i18n"Hello ${I18n.named("name", "world")}"

    message.key.source shouldBe "Hello {name}"
    message.key.fingerprint.value shouldBe "51e962fae94c4a20"
  }

  it should "fold a context into the fingerprint the same way for both interpolators" in {
    val message = i18nc"Hello ${I18n.named("name", "world")}"("greeting")

    message.key.context shouldBe Some(MessageContext("greeting"))
    message.key.fingerprint.value shouldBe "c13dfc3c6216d6de"
  }

  private final case class User(name: String)
}
