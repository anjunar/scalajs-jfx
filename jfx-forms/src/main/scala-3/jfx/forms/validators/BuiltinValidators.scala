package jfx.forms.validators

import scala.scalajs.js
import scala.util.matching.Regex

final case class NotNullValidator[V](message: String = "Must not be null") extends Validator[V] {
  def validate(value: V): Option[String] = Option.when(value == null)(message)
}

final case class NullValidator[V](message: String = "Must be null") extends Validator[V] {
  def validate(value: V): Option[String] = Option.when(value != null)(message)
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
    message: String | Null = null
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

final case class MinValidator[V](value: Long, message: String | Null = null) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.longConstraint(candidate, _ >= value, Option(message).getOrElse(s"Must be greater than or equal to $value"))
}

final case class MaxValidator[V](value: Long, message: String | Null = null) extends Validator[V] {
  def validate(candidate: V): Option[String] =
    ValidatorSupport.longConstraint(candidate, _ <= value, Option(message).getOrElse(s"Must be less than or equal to $value"))
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

  private def toBigDecimal(value: Any): Option[BigDecimal] = value match {
    case big: BigDecimal => Some(big)
    case number: Byte    => Some(BigDecimal(number))
    case number: Short   => Some(BigDecimal(number))
    case number: Int     => Some(BigDecimal(number))
    case number: Long    => Some(BigDecimal(number))
    case number: Float if !number.isNaN && !number.isInfinite => Some(BigDecimal.decimal(number.toDouble))
    case number: Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number))
    case text: String if text.trim.nonEmpty => text.trim.toDoubleOption.map(BigDecimal(_))
    case _ => None
  }
}
