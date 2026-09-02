package jfx.webauthn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

class WebAuthnCodecsSpec extends AnyFlatSpec with Matchers {

  "WebAuthnCodecs" should "decode complete registration JSON options" in {
    val json = js.Dynamic.literal(
      rp = js.Dynamic.literal(id = "example.com", name = "Example"),
      user = js.Dynamic.literal(
        id = "dXNlcjEyMw",
        name = "ada@example.com",
        displayName = "Ada"
      ),
      challenge = "Y2hhbGxlbmdlLTEyMw",
      pubKeyCredParams = js.Array(
        js.Dynamic.literal(`type` = "public-key", alg = -7),
        js.Dynamic.literal(`type` = "public-key", alg = -257)
      ),
      timeout = 60000,
      excludeCredentials = js.Array(
        js.Dynamic.literal(
          id = "Y3JlZC0x",
          `type` = "public-key",
          transports = js.Array("internal", "hybrid")
        )
      ),
      authenticatorSelection = js.Dynamic.literal(
        authenticatorAttachment = "platform",
        residentKey = "preferred",
        requireResidentKey = false,
        userVerification = "required"
      ),
      hints = js.Array("client-device"),
      attestation = "none",
      attestationFormats = js.Array("packed"),
      extensions = js.Dynamic.literal(credProps = true)
    )

    val options = WebAuthnCodecs.creationOptionsFromJson(json)

    Base64Url.encode(options.challenge) shouldBe "Y2hhbGxlbmdlLTEyMw"
    Base64Url.encode(options.user.id) shouldBe "dXNlcjEyMw"
    options.rp.id.get shouldBe "example.com"
    options.rp.name shouldBe "Example"
    options.pubKeyCredParams.map(_.alg).toSeq shouldBe Seq(-7, -257)
    options.timeout.get shouldBe 60000d
    Base64Url.encode(options.excludeCredentials.get.head.id) shouldBe "Y3JlZC0x"
    options.excludeCredentials.get.head.transports.get.toSeq shouldBe Seq("internal", "hybrid")
    options.authenticatorSelection.get.authenticatorAttachment.get shouldBe "platform"
    options.authenticatorSelection.get.residentKey.get shouldBe "preferred"
    options.authenticatorSelection.get.requireResidentKey.get shouldBe false
    options.authenticatorSelection.get.userVerification.get shouldBe "required"
    options.hints.get.toSeq shouldBe Seq("client-device")
    options.attestationFormats.get.toSeq shouldBe Seq("packed")
    options.extensions.get.asInstanceOf[js.Dynamic].credProps.asInstanceOf[Boolean] shouldBe true
  }

  it should "decode authentication options from a JSON string" in {
    val options = WebAuthnCodecs.requestOptionsFromJson(
      """{
        |  "challenge": "YXV0aC1jaGFsbGVuZ2U",
        |  "rpId": "example.com",
        |  "userVerification": "preferred",
        |  "hints": ["hybrid"],
        |  "allowCredentials": [{"id": "Y3JlZC0y", "type": "public-key"}]
        |}""".stripMargin
    )

    Base64Url.encode(options.challenge) shouldBe "YXV0aC1jaGFsbGVuZ2U"
    options.rpId.get shouldBe "example.com"
    options.userVerification.get shouldBe "preferred"
    options.hints.get.toSeq shouldBe Seq("hybrid")
    Base64Url.encode(options.allowCredentials.get.head.id) shouldBe "Y3JlZC0y"
  }

  it should "reject missing fields, wrong types and invalid base64url before a ceremony" in {
    val missing = intercept[IllegalArgumentException] {
      WebAuthnCodecs.requestOptionsFromJson(js.Dynamic.literal(rpId = "example.com"))
    }
    missing.getMessage should include("challenge")

    val wrongType = intercept[IllegalArgumentException] {
      WebAuthnCodecs.requestOptionsFromJson(
        js.Dynamic.literal(challenge = "YQ", allowCredentials = "not-an-array")
      )
    }
    wrongType.getMessage should include("allowCredentials")

    val invalidBase64 = intercept[IllegalArgumentException] {
      WebAuthnCodecs.requestOptionsFromJson(js.Dynamic.literal(challenge = "not valid"))
    }
    invalidBase64.getMessage should include("base64url")
  }

  it should "serialize registration responses and recursively encode extension buffers" in {
    val credential = js.Dynamic
      .literal(
        id = "credential-id",
        rawId = bytes(1, 2, 3).buffer,
        `type` = "public-key",
        authenticatorAttachment = null,
        response = js.Dynamic.literal(
          clientDataJSON = bytes(4, 5).buffer,
          attestationObject = bytes(6, 7).buffer,
          getTransports = (() => js.Array("internal")): js.Function0[js.Array[String]],
          getAuthenticatorData =
            (() => bytes(8, 9).buffer): js.Function0[scala.scalajs.js.typedarray.ArrayBuffer],
          getPublicKey =
            (() => bytes(10, 11).buffer): js.Function0[scala.scalajs.js.typedarray.ArrayBuffer],
          getPublicKeyAlgorithm = (() => -7): js.Function0[Int]
        ),
        getClientExtensionResults = (
            () =>
              js.Dynamic.literal(
                prf = js.Dynamic.literal(results = js.Dynamic.literal(first = bytes(12, 13).buffer))
              )
        ): js.Function0[js.Object]
      )
      .asInstanceOf[PublicKeyCredential]

    val payload    = WebAuthnCodecs.registrationCredential(credential)
    val extensions = payload.clientExtensionResults
      .asInstanceOf[js.Dynamic]
      .prf
      .results
      .first
      .asInstanceOf[String]

    payload.id shouldBe "credential-id"
    payload.rawId shouldBe "AQID"
    payload.response.clientDataJSON shouldBe "BAU"
    payload.response.attestationObject shouldBe "Bgc"
    payload.response.authenticatorData shouldBe Some("CAk")
    payload.response.publicKey shouldBe Some("Cgs")
    payload.response.publicKeyAlgorithm shouldBe Some(-7)
    payload.response.transports shouldBe Seq("internal")
    payload.authenticatorAttachment shouldBe None
    extensions shouldBe "DA0"
    js.JSON.parse(payload.toJson).selectDynamic("type").asInstanceOf[String] shouldBe "public-key"
  }

  it should "serialize authentication responses including an optional user handle" in {
    val credential = js.Dynamic
      .literal(
        id = "credential-id",
        rawId = bytes(1).buffer,
        `type` = "public-key",
        authenticatorAttachment = "cross-platform",
        response = js.Dynamic.literal(
          clientDataJSON = bytes(2).buffer,
          authenticatorData = bytes(3).buffer,
          signature = bytes(4).buffer,
          userHandle = bytes(5).buffer
        ),
        getClientExtensionResults = (() => js.Dynamic.literal()): js.Function0[js.Object]
      )
      .asInstanceOf[PublicKeyCredential]

    val payload = WebAuthnCodecs.authenticationCredential(credential)

    payload.rawId shouldBe "AQ"
    payload.response.clientDataJSON shouldBe "Ag"
    payload.response.authenticatorData shouldBe "Aw"
    payload.response.signature shouldBe "BA"
    payload.response.userHandle shouldBe Some("BQ")
    payload.authenticatorAttachment shouldBe Some("cross-platform")
  }

  it should "prefer native credential JSON serialization and preserve its receiver" in {
    val credential                                      = js.Dynamic.literal(id = "native-id")
    val toJson: js.ThisFunction0[js.Dynamic, js.Object] = self => {
      if (self.id.asInstanceOf[String] != "native-id")
        throw new IllegalStateException("toJSON receiver was lost")
      js.Dynamic.literal(source = "native")
    }
    credential.updateDynamic("toJSON")(toJson)

    val payload = WebAuthnCodecs
      .credentialToJson(credential.asInstanceOf[PublicKeyCredential])
      .asInstanceOf[js.Dynamic]

    payload.source.asInstanceOf[String] shouldBe "native"
  }

  private def bytes(values: Int*): Uint8Array =
    new Uint8Array(js.Array(values.map(_.toShort)*))
}
