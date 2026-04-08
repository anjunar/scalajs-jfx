package jfx.form

class ErrorResponseException(val errors: Seq[ErrorResponse]) extends RuntimeException
