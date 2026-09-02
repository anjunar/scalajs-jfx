package jfx.forms.validators

import scala.annotation.StaticAnnotation

final case class NotNull(message: String = "Must not be null")   extends StaticAnnotation
final case class Null(message: String = "Must be null")          extends StaticAnnotation
final case class AssertTrue(message: String = "Must be true")    extends StaticAnnotation
final case class AssertFalse(message: String = "Must be false")  extends StaticAnnotation
final case class NotEmpty(message: String = "Must not be empty") extends StaticAnnotation
final case class NotBlank(message: String = "Must not be blank") extends StaticAnnotation
final case class Size(min: Int = 0, max: Int = Int.MaxValue, message: String = "")
    extends StaticAnnotation
final case class Min(value: Long, message: String = "") extends StaticAnnotation
final case class Max(value: Long, message: String = "") extends StaticAnnotation
final case class DecimalMin(value: String, inclusive: Boolean = true, message: String = "")
    extends StaticAnnotation
final case class DecimalMax(value: String, inclusive: Boolean = true, message: String = "")
    extends StaticAnnotation
final case class Positive(message: String = "Must be positive") extends StaticAnnotation
final case class PositiveOrZero(message: String = "Must be positive or zero")
    extends StaticAnnotation
final case class Negative(message: String = "Must be negative") extends StaticAnnotation
final case class NegativeOrZero(message: String = "Must be negative or zero")
    extends StaticAnnotation
final case class Digits(integer: Int = 0, fraction: Int = 0, message: String = "")
    extends StaticAnnotation
final case class Pattern(regex: String, message: String = "Has an invalid format")
    extends StaticAnnotation
final case class EmailConstraint(message: String = "Must be a valid email address")
    extends StaticAnnotation
final case class Past(message: String = "Must be in the past") extends StaticAnnotation
final case class PastOrPresent(message: String = "Must be in the past or present")
    extends StaticAnnotation
final case class Future(message: String = "Must be in the future") extends StaticAnnotation
final case class FutureOrPresent(message: String = "Must be in the future or present")
    extends StaticAnnotation
