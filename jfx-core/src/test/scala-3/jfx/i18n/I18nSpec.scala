package jfx.i18n

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.state.Property
import jfx.core.text.TextValue
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class I18nSpec extends AnyFlatSpec with Matchers {

  "i18n interpolator" should "use the full English source as visible identity" in {
    val user = "Mira"
    val group = "Design"

    val message = i18n"User $user invited you to $group"

    message.key.source shouldBe "User {user} invited you to {group}"
    message.key.placeholders shouldBe Vector("user", "group")
    message.args.map(arg => arg.name -> arg.value).toMap shouldBe Map("user" -> "Mira", "group" -> "Design")
  }

  it should "support explicit placeholder names for expressions" in {
    val user = User("Mira")

    val message = i18n"User ${I18n.named("user", user.name)} signed in"

    message.key.source shouldBe "User {user} signed in"
    message.args.head.value shouldBe "Mira"
  }

  it should "resolve message-centered multilingual catalog entries with locale fallback" in {
    val count = 3
    val message = i18n"$count documents selected"
    val catalog = MessageCatalog(
      I18n.entry(message.key).translations(
        I18nLocale("de") -> "{count} Dokumente ausgewahlt",
        I18nLocale("fr") -> "{count} documents selectionnes"
      )
    )
    val resolver = I18nResolver(catalog)

    resolver.resolve(message, I18nLocale("de-AT")) shouldBe "3 Dokumente ausgewahlt"
    resolver.resolve(message, I18nLocale("es")) shouldBe "3 documents selected"
  }

  it should "produce reactive text from a reactive locale" in {
    val locale = Property(I18nLocale("en"))
    val message = i18n"Delete document"
    val catalog = MessageCatalog(
      I18n.entry(message.key).translations(
        I18nLocale("de") -> "Dokument loschen"
      )
    )

    val text = I18nResolver(catalog).resolve(message, locale)

    text.get shouldBe "Delete document"
    locale.set(I18nLocale("de"))
    text.get shouldBe "Dokument loschen"
  }

  it should "resolve runtime messages through the component i18n context" in {
    val locale = Property(I18nLocale("en"))
    val message = i18n"Delete document"
    val catalog = MessageCatalog(
      I18n.entry(message.key).translations(
        I18nLocale("de") -> "Dokument loschen"
      )
    )

    val root = new AbstractCustomComponent {}
    given AbstractComponent = root

    I18nRuntime.provide(I18nRuntime(locale, I18nResolver(catalog)))
    val text = summon[TextValue[RuntimeMessage]].asReadOnlyProperty(message)

    text.get shouldBe "Delete document"
    locale.set(I18nLocale("de"))
    text.get shouldBe "Dokument loschen"
  }

  private final case class User(name: String)
}
