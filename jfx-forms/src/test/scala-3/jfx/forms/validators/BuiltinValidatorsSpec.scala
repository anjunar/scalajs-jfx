package jfx.forms.validators

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BuiltinValidatorsSpec extends AnyFlatSpec with Matchers {

  "NotBlankValidator" should "reject null, empty, and whitespace-only values" in {
    val validator = NotBlankValidator()

    validator.validate(null) shouldBe Some("Must not be blank")
    validator.validate("") shouldBe Some("Must not be blank")
    validator.validate("  ") shouldBe Some("Must not be blank")
    validator.validate("Mira") shouldBe None
  }

  "SizeValidator" should "keep null values optional and enforce its configured range" in {
    val validator = SizeValidator[String](min = 2, max = 4)

    validator.validate(null) shouldBe None
    validator.validate("A") shouldBe Some("Must contain between 2 and 4 characters/items")
    validator.validate("Mira") shouldBe None
    validator.validate("Patrick") shouldBe Some("Must contain between 2 and 4 characters/items")
  }

  "EmailValidator" should "allow optional empty values and reject malformed addresses" in {
    val validator = EmailValidator()

    validator.validate("") shouldBe None
    validator.validate("invalid") shouldBe Some("Must be a valid email address")
    validator.validate("mira@example.org") shouldBe None
  }
}
