package jfx.form.validators

import scala.annotation.StaticAnnotation

final case class NotNull(message: String = "Darf nicht null sein") extends StaticAnnotation

final case class NotEmpty(message: String = "Darf nicht leer sein") extends StaticAnnotation

final case class NotBlank(message: String = "Darf nicht leer oder nur Leerzeichen sein") extends StaticAnnotation

final case class Size(
  min: Int = 0,
  max: Int = Int.MaxValue,
  message: String = ""
) extends StaticAnnotation

final case class Min(value: Long, message: String = "") extends StaticAnnotation

final case class Max(value: Long, message: String = "") extends StaticAnnotation

final case class DecimalMin(
  value: String,
  inclusive: Boolean = true,
  message: String = ""
) extends StaticAnnotation

final case class DecimalMax(
  value: String,
  inclusive: Boolean = true,
  message: String = ""
) extends StaticAnnotation

final case class Digits(
  integer: Int = 0,
  fraction: Int = 0,
  message: String = ""
) extends StaticAnnotation

final case class Pattern(
  regex: String,
  message: String = "Hat ein ungültiges Format"
) extends StaticAnnotation

final case class EmailConstraint(message: String = "Muss eine gültige E-Mail-Adresse sein") extends StaticAnnotation
