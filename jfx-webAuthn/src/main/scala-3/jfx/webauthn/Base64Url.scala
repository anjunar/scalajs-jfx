package jfx.webauthn

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

/** RFC 4648 base64url conversion for WebAuthn buffer values. */
object Base64Url {

  def encode(buffer: ArrayBuffer): String =
    encode(new Uint8Array(buffer))

  def encode(bytes: Uint8Array): String = {
    val binary = new StringBuilder(bytes.length)
    var index  = 0
    while (index < bytes.length) {
      binary.append(bytes(index).toChar)
      index += 1
    }

    globalBase64Encode(binary.result())
      .replace('+', '-')
      .replace('/', '_')
      .stripSuffix("==")
      .stripSuffix("=")
  }

  def decode(value: String): ArrayBuffer = {
    requireValid(value)
    val unpadded = value.takeWhile(_ != '=')
    val standard = unpadded.replace('-', '+').replace('_', '/')
    val padding  = (4 - standard.length % 4) % 4
    val binary   = globalBase64Decode(standard + ("=" * padding))
    val bytes    = new Uint8Array(binary.length)

    var index = 0
    while (index < binary.length) {
      bytes(index) = binary.charAt(index).toInt.toShort
      index += 1
    }
    bytes.buffer
  }

  def decodeToBytes(value: String): Uint8Array =
    new Uint8Array(decode(value))

  private def requireValid(value: String): Unit = {
    if (value == null) {
      throw new IllegalArgumentException("Base64url value must not be null")
    }

    val firstPadding    = value.indexOf('=')
    val dataLength      = if (firstPadding < 0) value.length else firstPadding
    val padding         = value.length - dataLength
    val alphabetIsValid = {
      var valid = true
      var index = 0
      while (valid && index < dataLength) {
        val character = value.charAt(index)
        valid = (character >= 'A' && character <= 'Z') ||
          (character >= 'a' && character <= 'z') ||
          (character >= '0' && character <= '9') ||
          character == '-' || character == '_'
        index += 1
      }
      valid
    }
    val paddingIsValid =
      padding <= 2 &&
        (firstPadding < 0 || value.drop(firstPadding).forall(_ == '=')) &&
        (padding == 0 || value.length % 4 == 0)

    if (!alphabetIsValid || !paddingIsValid || dataLength % 4 == 1) {
      throw new IllegalArgumentException("Invalid base64url value")
    }
  }

  private def globalBase64Encode(value: String): String =
    js.Dynamic.global.btoa(value).asInstanceOf[String]

  private def globalBase64Decode(value: String): String =
    try js.Dynamic.global.atob(value).asInstanceOf[String]
    catch {
      case error: Throwable =>
        throw new IllegalArgumentException("Invalid base64url value", error)
    }
}
