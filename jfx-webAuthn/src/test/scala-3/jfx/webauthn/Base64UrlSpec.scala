package jfx.webauthn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

class Base64UrlSpec extends AnyFlatSpec with Matchers {

  "Base64Url" should "round-trip arbitrary bytes without padding" in {
    val bytes   = new Uint8Array(js.Array[Short](0, 1, 2, 127, 128, 250, 251, 252, 255))
    val encoded = Base64Url.encode(bytes)
    val decoded = Base64Url.decodeToBytes(encoded)

    encoded shouldBe "AAECf4D6-_z_"
    decoded.toArray.toSeq shouldBe Seq(0, 1, 2, 127, 128, 250, 251, 252, 255)
  }

  it should "accept canonical padded input" in {
    Base64Url.decodeToBytes("Zg==").toArray.toSeq shouldBe Seq('f'.toInt)
  }

  it should "reject non-url-safe, malformed and misplaced padding" in {
    Seq("a", "a+b/", "Zg=", "Z===", "Zm 8", "💥").foreach { value =>
      withClue(value) {
        intercept[IllegalArgumentException](Base64Url.decode(value))
      }
    }
  }
}
