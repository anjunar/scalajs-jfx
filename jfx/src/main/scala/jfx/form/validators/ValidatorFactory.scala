package jfx.form.validators

import reflect.Annotation

import scala.util.matching.Regex

object ValidatorFactory {

  def createValidators(annotations: Array[Annotation]): Vector[jfx.form.validators.Validator[Any]] = {
    annotations.flatMap(createValidator).toVector
  }

  def createValidator(annotation: Annotation): Option[jfx.form.validators.Validator[Any]] = {
    annotation.annotationClassName match {
      case "jfx.form.validators.NotNull" =>
        val message = annotation.parameters.getOrElse("message", "Darf nicht null sein").asInstanceOf[String]
        Some(NotNullValidator[Any](message))

      case "jfx.form.validators.NotEmpty" =>
        val message = annotation.parameters.getOrElse("message", "Darf nicht leer sein").asInstanceOf[String]
        Some(NotEmptyValidator[Any](message))

      case "jfx.form.validators.NotBlank" =>
        val message = annotation.parameters.getOrElse("message", "Darf nicht leer oder nur Leerzeichen sein").asInstanceOf[String]
        Some(NotBlankValidator(message).asInstanceOf[jfx.form.validators.Validator[Any]])

      case "jfx.form.validators.Size" =>
        val min = annotation.parameters.getOrElse("min", 0).asInstanceOf[Int]
        val max = annotation.parameters.getOrElse("max", Int.MaxValue).asInstanceOf[Int]
        val messageParam = annotation.parameters.getOrElse("message", "").asInstanceOf[String]
        val message = if (messageParam.nonEmpty) messageParam else null
        Some(SizeValidator[Any](min, max, message))

      case "jfx.form.validators.Min" =>
        val value = annotation.parameters.getOrElse("value", 0L).asInstanceOf[Long]
        val messageParam = annotation.parameters.getOrElse("message", "").asInstanceOf[String]
        val message = if (messageParam.nonEmpty) messageParam else null
        Some(MinValidator[Any](value, message))

      case "jfx.form.validators.Max" =>
        val value = annotation.parameters.getOrElse("value", 0L).asInstanceOf[Long]
        val messageParam = annotation.parameters.getOrElse("message", "").asInstanceOf[String]
        val message = if (messageParam.nonEmpty) messageParam else null
        Some(MaxValidator[Any](value, message))

      case "jfx.form.validators.Digits" =>
        val integer = annotation.parameters.getOrElse("integer", 0).asInstanceOf[Int]
        val fraction = annotation.parameters.getOrElse("fraction", 0).asInstanceOf[Int]
        val messageParam = annotation.parameters.getOrElse("message", "").asInstanceOf[String]
        val message = if (messageParam.nonEmpty) messageParam else null
        Some(DigitsValidator[Any](integer, fraction, message))

      case "jfx.form.validators.Pattern" =>
        val regex = annotation.parameters.getOrElse("regex", "").asInstanceOf[String]
        val message = annotation.parameters.getOrElse("message", "Hat ein ungueltiges Format").asInstanceOf[String]
        Some(PatternValidator(new Regex(regex), message).asInstanceOf[jfx.form.validators.Validator[Any]])

      case "jfx.form.validators.EmailConstraint" =>
        val message = annotation.parameters.getOrElse("message", "Muss eine gueltige E-Mail-Adresse sein").asInstanceOf[String]
        Some(EmailValidator(message).asInstanceOf[jfx.form.validators.Validator[Any]])

      case _ => None
    }
  }

}
