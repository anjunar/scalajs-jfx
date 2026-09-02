package jfx.webauthn

import scala.scalajs.js

sealed trait WebAuthnCredentialPayload {
  def id: String
  def rawId: String
  def authenticatorAttachment: Option[String]
  def clientExtensionResults: js.Object
  def credentialType: String
  def toJsObject: js.Object

  final def toJson: String = js.JSON.stringify(toJsObject)
}

final case class RegistrationCredential(
    id: String,
    rawId: String,
    response: RegistrationResponse,
    authenticatorAttachment: Option[String] = None,
    clientExtensionResults: js.Object = js.Dynamic.literal(),
    credentialType: String = CredentialType.PublicKey
) extends WebAuthnCredentialPayload {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      id = id,
      rawId = rawId,
      response = response.toJsObject,
      clientExtensionResults = clientExtensionResults
    )
    result.updateDynamic("type")(credentialType)
    authenticatorAttachment.foreach(result.updateDynamic("authenticatorAttachment")(_))
    result
  }
}

final case class RegistrationResponse(
    clientDataJSON: String,
    attestationObject: String,
    transports: Seq[String] = Seq.empty,
    authenticatorData: Option[String] = None,
    publicKey: Option[String] = None,
    publicKeyAlgorithm: Option[Int] = None
) {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      clientDataJSON = clientDataJSON,
      attestationObject = attestationObject,
      transports = js.Array(transports*)
    )
    authenticatorData.foreach(result.updateDynamic("authenticatorData")(_))
    publicKey.foreach(result.updateDynamic("publicKey")(_))
    publicKeyAlgorithm.foreach(result.updateDynamic("publicKeyAlgorithm")(_))
    result
  }
}

final case class AuthenticationCredential(
    id: String,
    rawId: String,
    response: AuthenticationResponse,
    authenticatorAttachment: Option[String] = None,
    clientExtensionResults: js.Object = js.Dynamic.literal(),
    credentialType: String = CredentialType.PublicKey
) extends WebAuthnCredentialPayload {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      id = id,
      rawId = rawId,
      response = response.toJsObject,
      clientExtensionResults = clientExtensionResults
    )
    result.updateDynamic("type")(credentialType)
    authenticatorAttachment.foreach(result.updateDynamic("authenticatorAttachment")(_))
    result
  }
}

final case class AuthenticationResponse(
    clientDataJSON: String,
    authenticatorData: String,
    signature: String,
    userHandle: Option[String] = None
) {
  def toJsObject: js.Object = {
    val result = js.Dynamic.literal(
      clientDataJSON = clientDataJSON,
      authenticatorData = authenticatorData,
      signature = signature
    )
    userHandle.foreach(result.updateDynamic("userHandle")(_))
    result
  }
}
