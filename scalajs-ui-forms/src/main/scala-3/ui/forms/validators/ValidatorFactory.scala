package ui.forms.validators

import reflect.Annotation

import scala.util.matching.Regex

object ValidatorFactory {

  def createValidators(annotations: Array[Annotation]): Vector[Validator[Any]] =
    annotations.iterator.flatMap(createValidator).toVector

  def createValidator(annotation: Annotation): Option[Validator[Any]] = {
    val parameters                      = annotation.parameters
    val validator: Option[Validator[?]] = annotation.annotationClassName match {
      case "ui.forms.validators.NotNull" =>
        Some(NotNullValidator[Any](string(parameters, "message", "Must not be null")))
      case "ui.forms.validators.Null" =>
        Some(NullValidator[Any](string(parameters, "message", "Must be null")))
      case "ui.forms.validators.AssertTrue" =>
        Some(AssertTrueValidator(string(parameters, "message", "Must be true")))
      case "ui.forms.validators.AssertFalse" =>
        Some(AssertFalseValidator(string(parameters, "message", "Must be false")))
      case "ui.forms.validators.NotEmpty" =>
        Some(NotEmptyValidator[Any](string(parameters, "message", "Must not be empty")))
      case "ui.forms.validators.NotBlank" =>
        Some(NotBlankValidator(string(parameters, "message", "Must not be blank")))
      case "ui.forms.validators.Size" =>
        Some(
          SizeValidator[Any](
            int(parameters, "min", 0),
            int(parameters, "max", Int.MaxValue),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Min" =>
        Some(MinValidator[Any](long(parameters, "value", 0L), optionalMessage(parameters)))
      case "ui.forms.validators.Max" =>
        Some(MaxValidator[Any](long(parameters, "value", 0L), optionalMessage(parameters)))
      case "ui.forms.validators.DecimalMin" =>
        Some(
          DecimalMinValidator[Any](
            BigDecimal(string(parameters, "value", "0")),
            boolean(parameters, "inclusive", true),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.DecimalMax" =>
        Some(
          DecimalMaxValidator[Any](
            BigDecimal(string(parameters, "value", "0")),
            boolean(parameters, "inclusive", true),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Positive" =>
        Some(PositiveValidator[Any](string(parameters, "message", "Must be positive")))
      case "ui.forms.validators.PositiveOrZero" =>
        Some(
          PositiveOrZeroValidator[Any](string(parameters, "message", "Must be positive or zero"))
        )
      case "ui.forms.validators.Negative" =>
        Some(NegativeValidator[Any](string(parameters, "message", "Must be negative")))
      case "ui.forms.validators.NegativeOrZero" =>
        Some(
          NegativeOrZeroValidator[Any](string(parameters, "message", "Must be negative or zero"))
        )
      case "ui.forms.validators.Digits" =>
        Some(
          DigitsValidator[Any](
            int(parameters, "integer", 0),
            int(parameters, "fraction", 0),
            optionalMessage(parameters)
          )
        )
      case "ui.forms.validators.Pattern" =>
        Some(
          PatternValidator(
            new Regex(string(parameters, "regex", "")),
            string(parameters, "message", "Has an invalid format")
          )
        )
      case "ui.forms.validators.EmailConstraint" =>
        Some(EmailValidator(string(parameters, "message", "Must be a valid email address")))
      case "ui.forms.validators.Past" =>
        Some(PastValidator[Any](string(parameters, "message", "Must be in the past")))
      case "ui.forms.validators.PastOrPresent" =>
        Some(
          PastOrPresentValidator[Any](
            string(parameters, "message", "Must be in the past or present")
          )
        )
      case "ui.forms.validators.Future" =>
        Some(FutureValidator[Any](string(parameters, "message", "Must be in the future")))
      case "ui.forms.validators.FutureOrPresent" =>
        Some(
          FutureOrPresentValidator[Any](
            string(parameters, "message", "Must be in the future or present")
          )
        )
      case _ => None
    }

    validator.map(_.asInstanceOf[Validator[Any]])
  }

  private def optionalMessage(parameters: Map[String, Any]): String | scala.Null =
    parameters.get("message").map(_.toString).filter(_.nonEmpty).orNull

  private def string(parameters: Map[String, Any], name: String, default: String): String =
    parameters.get(name).map(_.toString).getOrElse(default)

  private def int(parameters: Map[String, Any], name: String, default: Int): Int =
    parameters.get(name).collect { case number: Number => number.intValue() }.getOrElse(default)

  private def long(parameters: Map[String, Any], name: String, default: Long): Long =
    parameters.get(name).collect { case number: Number => number.longValue() }.getOrElse(default)

  private def boolean(parameters: Map[String, Any], name: String, default: Boolean): Boolean =
    parameters.get(name).collect { case value: Boolean => value }.getOrElse(default)
}
