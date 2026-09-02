package jfx.webauthn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class WebAuthnFacadesSpec extends AnyFlatSpec with Matchers {

  "WebAuthn facades" should "omit optional dictionary members instead of writing null" in {
    val creation = PublicKeyCredentialCreationOptions(
      rp = PublicKeyCredentialRpEntity("Example"),
      user = PublicKeyCredentialUserEntity(Base64Url.decode("dXNlcg"), "user", "User"),
      challenge = Base64Url.decode("Y2hhbGxlbmdl"),
      pubKeyCredParams = Seq(PublicKeyCredentialParameters(CoseAlgorithmIdentifier.ES256))
    )
    val request = CredentialCreationOptions(creation)

    js.isUndefined(creation.rp.id) shouldBe true
    js.isUndefined(creation.timeout) shouldBe true
    js.isUndefined(creation.excludeCredentials) shouldBe true
    js.isUndefined(creation.extensions) shouldBe true
    js.isUndefined(request.signal) shouldBe true
    js.isUndefined(request.mediation) shouldBe true
  }

  it should "carry mediation independently from public-key ceremony options" in {
    val options = PublicKeyCredentialRequestOptions(Base64Url.decode("Y2hhbGxlbmdl"))
    val request = CredentialRequestOptions.withSettings(
      options,
      CredentialRequestSettings(mediation = Some(CredentialMediationRequirement.Conditional))
    )

    request.mediation.get shouldBe "conditional"
    request.publicKey shouldBe options
  }

  "WebAuthn" should "be safe to inspect outside a browser" in {
    WebAuthn.isSupported shouldBe false
    WebAuthn.isAvailable shouldBe false
  }
}
