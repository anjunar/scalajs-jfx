package jfx.webauthn

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class WebAuthnAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "WebAuthn capability checks" should "degrade safely outside a browser" in {
    for {
      capabilities <- WebAuthn.clientCapabilities()
      conditional  <- WebAuthn.isConditionalMediationAvailable()
      platform     <- WebAuthn.isUserVerifyingPlatformAuthenticatorAvailable()
    } yield {
      capabilities shouldBe empty
      conditional shouldBe false
      platform shouldBe false
    }
  }

  "WebAuthn ceremonies" should "fail asynchronously when the browser API is unavailable" in {
    val options = PublicKeyCredentialRequestOptions(Base64Url.decode("Y2hhbGxlbmdl"))

    recoverToSucceededIf[UnsupportedOperationException] {
      WebAuthn.authenticate(options)
    }
  }
}
