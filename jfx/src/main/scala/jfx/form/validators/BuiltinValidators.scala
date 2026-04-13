package jfx.form.validators

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date
import scala.util.matching.Regex

final case class NotNullValidator[V](message: String = "Darf nicht null sein") extends Validator[V] {
  override def validate(value: V): Option[String] =
    if (value == null) Some(message)
    else None
}

final case class NullValidator[V](message: String = "Muss null sein") extends Validator[V] {
  override def validate(value: V): Option[String] =
    if (value == null) None
    else Some(message)
}

final case class AssertTrueValidator(message: String = "Muss wahr sein") extends Validator[Boolean] {
  override def validate(value: Boolean): Option[String] =
    if (value) None
    else Some(message)
}

final case class AssertFalseValidator(message: String = "Muss falsch sein") extends Validator[Boolean] {
  override def validate(value: Boolean): Option[String] =
    if (!value) None
    else Some(message)
}

final case class NotEmptyValidator[V](message: String = "Darf nicht leer sein") extends Validator[V] {
  override def validate(value: V): Option[String] =
    if (value == null) Some(message)
    else ValidatorSupport.sizeOf(value) match {
      case Some(size) if size > 0 => None
      case Some(_) => Some(message)
      case None => None
    }
}

final case class NotBlankValidator(message: String = "Darf nicht leer oder nur Leerzeichen sein") extends Validator[String] {
  override def validate(value: String): Option[String] =
    if (value == null || value.trim.isEmpty) Some(message)
    else None
}

final case class SizeValidator[V](
  min: Int = 0,
  max: Int = Int.MaxValue,
  message: String | Null = null
) extends Validator[V] {

  require(min >= 0, s"min must be >= 0 but was $min")
  require(max >= min, s"max must be >= min but was $max < $min")

  override def validate(value: V): Option[String] = {
    if (value == null) {
      None
    } else {
      ValidatorSupport.sizeOf(value) match {
        case Some(size) if size < min || size > max =>
          Some(resolveMessage())
        case _ =>
          None
      }
    }
  }

  private def resolveMessage(): String =
    if (message != null && message.trim.nonEmpty) message
    else if (min == max) s"Muss genau $min Zeichen/Eintraege haben"
    else if (max == Int.MaxValue) s"Muss mindestens $min Zeichen/Eintraege haben"
    else if (min == 0) s"Darf hoechstens $max Zeichen/Eintraege haben"
    else s"Muss zwischen $min und $max Zeichen/Eintraege haben"
}

final case class MinValidator[V](value: Long, message: String | Null = null) extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateLongConstraint(candidate, _ >= value, resolveMessage())

  private def resolveMessage(): String =
    if (ValidatorSupport.hasText(message)) message
    else s"Muss größer oder gleich $value sein"
}

final case class MaxValidator[V](value: Long, message: String | Null = null) extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateLongConstraint(candidate, _ <= value, resolveMessage())

  private def resolveMessage(): String =
    if (ValidatorSupport.hasText(message)) message
    else s"Muss kleiner oder gleich $value sein"
}

final case class DecimalMinValidator[V](
  value: BigDecimal,
  inclusive: Boolean = true,
  message: String | Null = null
) extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, compare(_, value), resolveMessage())

  private def compare(candidate: BigDecimal, limit: BigDecimal): Boolean =
    if (inclusive) candidate >= limit
    else candidate > limit

  private def resolveMessage(): String =
    if (ValidatorSupport.hasText(message)) message
    else if (inclusive) s"Muss größer oder gleich $value sein"
    else s"Muss größer als $value sein"
}

final case class DecimalMaxValidator[V](
  value: BigDecimal,
  inclusive: Boolean = true,
  message: String | Null = null
) extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, compare(_, value), resolveMessage())

  private def compare(candidate: BigDecimal, limit: BigDecimal): Boolean =
    if (inclusive) candidate <= limit
    else candidate < limit

  private def resolveMessage(): String =
    if (ValidatorSupport.hasText(message)) message
    else if (inclusive) s"Muss kleiner oder gleich $value sein"
    else s"Muss kleiner als $value sein"
}

final case class PositiveValidator[V](message: String = "Muss positiv sein") extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, _ > 0, message)
}

final case class PositiveOrZeroValidator[V](message: String = "Muss positiv oder 0 sein") extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, _ >= 0, message)
}

final case class NegativeValidator[V](message: String = "Muss negativ sein") extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, _ < 0, message)
}

final case class NegativeOrZeroValidator[V](message: String = "Muss negativ oder 0 sein") extends Validator[V] {
  override def validate(candidate: V): Option[String] =
    ValidatorSupport.validateDecimalConstraint(candidate, _ <= 0, message)
}

final case class DigitsValidator[V](
  integer: Int,
  fraction: Int,
  message: String | Null = null
) extends Validator[V] {

  require(integer >= 0, s"integer must be >= 0 but was $integer")
  require(fraction >= 0, s"fraction must be >= 0 but was $fraction")

  override def validate(candidate: V): Option[String] =
    if (candidate == null) None
    else ValidatorSupport.toBigDecimal(candidate) match {
      case Some(decimal) if hasValidDigits(decimal) => None
      case Some(_) => Some(resolveMessage())
      case None => None
    }

  private def hasValidDigits(decimal: BigDecimal): Boolean = {
    val normalized = decimal.bigDecimal.stripTrailingZeros()
    val scale = math.max(0, normalized.scale())
    val precision = normalized.precision()
    val integerDigits = math.max(0, precision - scale)
    integerDigits <= integer && scale <= fraction
  }

  private def resolveMessage(): String =
    if (ValidatorSupport.hasText(message)) message
    else s"Zulaessig sind hoechstens $integer Vorkommastellen und $fraction Nachkommastellen"
}

final case class PatternValidator(
  regex: Regex,
  message: String = "Hat ein ungültiges Format"
) extends Validator[String] {
  override def validate(value: String): Option[String] =
    if (value == null) None
    else if (regex.matches(value)) None
    else Some(message)
}

final case class EmailValidator(
  message: String = "Muss eine gültige E-Mail-Adresse sein",
  regex: Regex = ValidatorSupport.defaultEmailRegex
) extends Validator[String] {
  override def validate(value: String): Option[String] =
    if (value == null || value.trim.isEmpty) None
    else if (regex.matches(value.trim)) None
    else Some(message)
}

final case class PastValidator[V](message: String = "Muss in der Vergangenheit liegen") extends Validator[V] {
  override def validate(value: V): Option[String] =
    ValidatorSupport.validateTemporalConstraint(value, isPast = true, inclusive = false, message)
}

final case class PastOrPresentValidator[V](message: String = "Muss in der Vergangenheit oder Gegenwart liegen") extends Validator[V] {
  override def validate(value: V): Option[String] =
    ValidatorSupport.validateTemporalConstraint(value, isPast = true, inclusive = true, message)
}

final case class FutureValidator[V](message: String = "Muss in der Zukunft liegen") extends Validator[V] {
  override def validate(value: V): Option[String] =
    ValidatorSupport.validateTemporalConstraint(value, isPast = false, inclusive = false, message)
}

final case class FutureOrPresentValidator[V](message: String = "Muss in der Zukunft oder Gegenwart liegen") extends Validator[V] {
  override def validate(value: V): Option[String] =
    ValidatorSupport.validateTemporalConstraint(value, isPast = false, inclusive = true, message)
}

private object ValidatorSupport {

  val defaultEmailRegex: Regex =
    "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$".r

  def hasText(value: String | Null): Boolean =
    value != null && value.trim.nonEmpty

  def sizeOf(value: Any): Option[Int] =
    value match {
      case text: String => Some(text.length)
      case array: scala.scalajs.js.Array[?] => Some(array.length)
      case array: Array[?] => Some(array.length)
      case iterable: Iterable[?] => Some(iterable.size)
      case collection: IterableOnce[?] => Some(collection.iterator.length)
      case _ => None
    }

  def toBigDecimal(value: Any): Option[BigDecimal] =
    value match {
      case big: BigDecimal => Some(big)
      case big: java.math.BigDecimal => Some(BigDecimal(big))
      case number: Byte => Some(BigDecimal(number))
      case number: Short => Some(BigDecimal(number))
      case number: Int => Some(BigDecimal(number))
      case number: Long => Some(BigDecimal(number))
      case number: Float if !number.isNaN && !number.isInfinite => Some(BigDecimal.decimal(number.toDouble))
      case number: Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number))
      case number: java.lang.Byte => Some(BigDecimal(number.byteValue()))
      case number: java.lang.Short => Some(BigDecimal(number.shortValue()))
      case number: java.lang.Integer => Some(BigDecimal(number.intValue()))
      case number: java.lang.Long => Some(BigDecimal(number.longValue()))
      case number: java.lang.Float if !number.isNaN && !number.isInfinite => Some(BigDecimal.decimal(number.doubleValue()))
      case number: java.lang.Double if !number.isNaN && !number.isInfinite => Some(BigDecimal(number.doubleValue()))
      case text: String if text.trim.nonEmpty => text.trim.toDoubleOption.map(BigDecimal(_))
      case _ => None
    }

  def validateLongConstraint[V](candidate: V, predicate: Long => Boolean, message: String): Option[String] =
    if (candidate == null) None
    else toBigDecimal(candidate) match {
      case Some(decimal) if decimal.isValidLong && predicate(decimal.toLongExact) => None
      case Some(_) => Some(message)
      case None => None
    }

  def validateDecimalConstraint[V](candidate: V, predicate: BigDecimal => Boolean, message: String): Option[String] =
    if (candidate == null) None
    else toBigDecimal(candidate) match {
      case Some(decimal) if predicate(decimal) => None
      case Some(_) => Some(message)
      case None => None
    }

  def validateTemporalConstraint[V](candidate: V, isPast: Boolean, inclusive: Boolean, message: String): Option[String] =
    if (candidate == null) None
    else temporalComparison(candidate) match {
      case Some(comparison) if comparison(isPast, inclusive) => None
      case Some(_) => Some(message)
      case None => None
    }

  private def temporalComparison(candidate: Any): Option[(Boolean, Boolean) => Boolean] =
    candidate match {
      case instant: Instant =>
        val now = Instant.now()
        Some((isPast: Boolean, inclusive: Boolean) => compare(instant.compareTo(now), isPast, inclusive))
      case localDate: LocalDate =>
        val now = LocalDate.now()
        Some((isPast: Boolean, inclusive: Boolean) => compare(localDate.compareTo(now), isPast, inclusive))
      case localDateTime: LocalDateTime =>
        val now = LocalDateTime.now()
        Some((isPast: Boolean, inclusive: Boolean) => compare(localDateTime.compareTo(now), isPast, inclusive))
      case offsetDateTime: OffsetDateTime =>
        val now = OffsetDateTime.now()
        Some((isPast: Boolean, inclusive: Boolean) => compare(offsetDateTime.compareTo(now), isPast, inclusive))
      case zonedDateTime: ZonedDateTime =>
        val now = ZonedDateTime.now()
        Some((isPast: Boolean, inclusive: Boolean) => compare(zonedDateTime.compareTo(now), isPast, inclusive))
      case date: Date =>
        val now = new Date()
        Some((isPast: Boolean, inclusive: Boolean) => compare(date.compareTo(now), isPast, inclusive))
      case _ =>
        None
    }

  private def compare(result: Int, isPast: Boolean, inclusive: Boolean): Boolean =
    if (isPast) {
      if (inclusive) result <= 0 else result < 0
    } else {
      if (inclusive) result >= 0 else result > 0
    }
}
