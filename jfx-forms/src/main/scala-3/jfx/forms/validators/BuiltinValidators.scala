package jfx.forms.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import scala.scalajs.js
import scala.util.matching.Regex

final case class NotNullValidator[V](message: String = "Must not be null") extends Validator[V] {
  def validate(value: V): Option[String] = Option.when(value == null)(message)
}

final case class NullValidator[V](message: String = "Must be null") extends Validator[V] {
  def validate(value: V): Option[String] = Option.when(value != null)(message)
}

final case class AssertTrueValidator(message: String = "Must be true") extends Validator[Boolean] {
  def validate(value: Boolean): Option[String] = Option.when(!value)(message)
}

final case class AssertFalseValidator(message: String = "Must be false") extends Validator[Boolean] {
  def validate(value: Boolean): Option[String] = Option.when(value)(message)
}

final case class NotEmptyValidator[V](message: String = "Must not be empty") extends Validator[V] {
  def validate(value: V): Option[String] =
    if (value == null) Some(message)
    else ValidatorSupport.sizeOf(value).filter(_ == 0).map(_ => message)
}

final case class NotBlankValidator(message: String = "Must not be blank") extends Validator[String] {
  def validate(value: String): Option[String] =
    Option.when(value == null || value.trim.isEmpty)(message)
}

final case class SizeValidator[V](
    min: Int = 0,
    max: Int = Int.MaxValue,
    message: String | scala.Null = null
) extends Validator[V] {
  require(min >= 0, s"min must be >= 0 but was $min")
  require(max >= min, s"max must be >= min but was $max < $min")

  def validate(value: V): Option[String] =
    if (value == null) None
    else ValidatorSupport.sizeOf(value).filter(size => size < min || size > max).map(_ => resolvedMessage)

  private def resolvedMessage: String =
    Option(message).filter(_.trim.nonEmpty).getOrElse {
      if (min == max) s"Must contain exactly $min characters/items"
      else if (max == Int.MaxValue) s"Must contain at least $min characters/items"
      else if (min == 0) s"Must contain at most $max characters/items"
      else s"Must contain between $min and $max characters/items"
    }
}

final case class MinValidator[V](value: Long, message: String | scala.Null = null) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.longConstraint(candidate, _ >= value, Option(message).getOrElse(s"Must be greater than or equal to $value"))
}

final case class MaxValidator[V](value: Long, message: String | scala.Null = null) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.longConstraint(candidate, _ <= value, Option(message).getOrElse(s"Must be less than or equal to $value"))
}

final case class DecimalMinValidator[V](
    value: BigDecimal,
    inclusive: Boolean = true,
    message: String | scala.Null = null
) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(
      candidate,
      decimal => if (inclusive) decimal >= value else decimal > value,
      Option(message).getOrElse(
        if (inclusive) s"Must be greater than or equal to $value" else s"Must be greater than $value"
      )
    )
}

final case class DecimalMaxValidator[V](
    value: BigDecimal,
    inclusive: Boolean = true,
    message: String | scala.Null = null
) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(
      candidate,
      decimal => if (inclusive) decimal <= value else decimal < value,
      Option(message).getOrElse(
        if (inclusive) s"Must be less than or equal to $value" else s"Must be less than $value"
      )
    )
}

final case class PositiveValidator[V](message: String = "Must be positive") extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(candidate, _ > 0, message)
}

final case class PositiveOrZeroValidator[V](message: String = "Must be positive or zero")
    extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(candidate, _ >= 0, message)
}

final case class NegativeValidator[V](message: String = "Must be negative") extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(candidate, _ < 0, message)
}

final case class NegativeOrZeroValidator[V](message: String = "Must be negative or zero")
    extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.decimalConstraint(candidate, _ <= 0, message)
}

final case class DigitsValidator[V](
    integer: Int,
    fraction: Int,
    message: String | scala.Null = null
) extends Validator[V] {
  require(integer >= 0, s"integer must be >= 0 but was $integer")
  require(fraction >= 0, s"fraction must be >= 0 but was $fraction")

  def validate(candidate: V): Option[String] =
    if (candidate == null) None
    else ValidatorSupport.toBigDecimal(candidate) match {
      case Some(decimal) if validDigits(decimal) => None
      case Some(_) => Some(Option(message).getOrElse(
        s"At most $integer integer digits and $fraction fractional digits are allowed"
      ))
      case None => None
    }

  private def validDigits(decimal: BigDecimal): Boolean = {
    val normalized = decimal.bigDecimal.stripTrailingZeros()
    val scale = math.max(0, normalized.scale())
    val integerDigits = math.max(0, normalized.precision() - scale)
    integerDigits <= integer && scale <= fraction
  }
}

final case class PatternValidator(regex: Regex, message: String = "Has an invalid format")
    extends Validator[String] {
  def validate(value: String): Option[String] =
    Option.when(value != null && !regex.matches(value))(message)
}

final case class EmailValidator(
    message: String = "Must be a valid email address",
    regex: Regex = ValidatorSupport.defaultEmailRegex
) extends Validator[String] {
  def validate(value: String): Option[String] =
    Option.when(value != null && value.trim.nonEmpty && !regex.matches(value.trim))(message)
}

final case class PastValidator[V](message: String = "Must be in the past") extends Validator[V] {
  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = true, inclusive = false, message)
}

final case class PastOrPresentValidator[V](message: String = "Must be in the past or present")
    extends Validator[V] {
  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = true, inclusive = true, message)
}

final case class FutureValidator[V](message: String = "Must be in the future") extends Validator[V] {
  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = false, inclusive = false, message)
}

final case class FutureOrPresentValidator[V](message: String = "Must be in the future or present")
    extends Validator[V] {
  def validate(value: V): Option[String] =
    ValidatorSupport.temporalConstraint(value, isPast = false, inclusive = true, message)
}

private object ValidatorSupport {
  val defaultEmailRegex: Regex =
    "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$".r

  def sizeOf(value: Any): Option[Int] = value match {
    case text: String            => Some(text.length)
    case array: js.Array[?]      => Some(array.length)
    case array: Array[?]         => Some(array.length)
    case iterable: Iterable[?]   => Some(iterable.size)
    case values: IterableOnce[?] => Some(values.iterator.length)
    case _                       => None
  }

  def longConstraint[V](candidate: V, predicate: Long => Boolean, message: String): Option[String] =
    if (candidate == null) None
    else toBigDecimal(candidate) match {
      case Some(decimal) if decimal.isValidLong && predicate(decimal.toLongExact) => None
      case Some(_) => Some(message)
      case None    => None
    }

  def decimalConstraint[V](
      candidate: V,
      predicate: BigDecimal => Boolean,
      message: String
  ): Option[String] =
    if (candidate == null) None
    else toBigDecimal(candidate) match {
      case Some(decimal) if predicate(decimal) => None
      case Some(_)                             => Some(message)
      case None                                => None
    }

  def toBigDecimal(value: Any): Option[BigDecimal] = value match {
    case big: BigDecimal           => Some(big)
    case big: java.math.BigDecimal => Some(BigDecimal(big))
    case number: Byte    => Some(BigDecimal(number))
    case number: Short   => Some(BigDecimal(number))
    case number: Int     => Some(BigDecimal(number))
    case number: Long    => Some(BigDecimal(number))
    case number: Float if !number.isNaN && !number.isInfinite => Some(BigDecimal.decimal(number.toDouble))
    case number: Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number))
    case text: String if text.trim.nonEmpty => text.trim.toDoubleOption.map(BigDecimal(_))
    case _ => None
  }

  def temporalConstraint[V](
      candidate: V,
      isPast: Boolean,
      inclusive: Boolean,
      message: String
  ): Option[String] =
    if (candidate == null) None
    else temporalComparison(candidate) match {
      case Some(comparison) if accepted(comparison, isPast, inclusive) => None
      case Some(_)                                                     => Some(message)
      case None                                                        => None
    }

  private def temporalComparison(candidate: Any): Option[Int] = candidate match {
    case value: Instant        => Some(value.compareTo(Instant.now()))
    case value: LocalDate      => Some(value.compareTo(LocalDate.now()))
    case value: LocalDateTime  => Some(value.compareTo(LocalDateTime.now()))
    case value: OffsetDateTime => Some(value.compareTo(OffsetDateTime.now()))
    case value: ZonedDateTime  => Some(value.compareTo(ZonedDateTime.now()))
    case value: Date           => Some(value.compareTo(new Date()))
    case _                     => None
  }

  private def accepted(comparison: Int, isPast: Boolean, inclusive: Boolean): Boolean =
    if (isPast) {
      if (inclusive) comparison <= 0 else comparison < 0
    } else if (inclusive) comparison >= 0
    else comparison > 0
}
