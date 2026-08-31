package jfx.forms

final case class ErrorResponse(message: String = "", path: Seq[String] = Seq.empty) {
  def withoutHead: ErrorResponse = copy(path = path.drop(1))
}

final class ErrorResponseException(val errors: Seq[ErrorResponse])
    extends RuntimeException(errors.map(_.message).filter(_.nonEmpty).mkString(", "))
