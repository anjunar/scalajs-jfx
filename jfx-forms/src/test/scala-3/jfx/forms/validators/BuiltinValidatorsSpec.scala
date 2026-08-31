package jfx.forms.validators

import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.Annotation

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

  "Decimal and digit validators" should "enforce inclusive bounds and precision" in {
    DecimalMinValidator[String](BigDecimal("1.5")).validate("1.49") should not be empty
    DecimalMinValidator[String](BigDecimal("1.5")).validate("1.5") shouldBe None
    DecimalMaxValidator[Double](BigDecimal("2.5"), inclusive = false).validate(2.5) should not be empty
    DigitsValidator[String](integer = 3, fraction = 2).validate("123.45") shouldBe None
    DigitsValidator[String](integer = 3, fraction = 2).validate("1234.5") should not be empty
  }

  "Temporal validators" should "compare instants against the current time" in {
    PastValidator[Instant]().validate(Instant.now().minusSeconds(10)) shouldBe None
    FutureValidator[Instant]().validate(Instant.now().plusSeconds(10)) shouldBe None
    PastValidator[Instant]().validate(Instant.now().plusSeconds(10)) should not be empty
  }

  "ValidatorFactory" should "create configured validators from reflection annotations" in {
    val validator = ValidatorFactory.createValidator(
      Annotation(
        "jfx.forms.validators.Size",
        Map("min" -> 2, "max" -> 3, "message" -> "Wrong size")
      )
    )

    validator.get.validate("A") shouldBe Some("Wrong size")
    validator.get.validate("Ada") shouldBe None
    ValidatorFactory.createValidator(Annotation("unknown.Annotation")) shouldBe None
  }
}
