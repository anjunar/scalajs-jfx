package jfx.webauthn

import org.scalajs.dom.AbortSignal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
trait PublicKeyCredentialRpEntity extends js.Object {
  val id: js.UndefOr[String] = js.native
  val name: String           = js.native
  @deprecated("WebAuthn no longer defines RP icons", "1.0.0")
  val icon: js.UndefOr[String] = js.native
}

object PublicKeyCredentialRpEntity {
  def apply(
      name: String,
      id: Option[String] = None,
      icon: Option[String] = None
  ): PublicKeyCredentialRpEntity = {
    val result = js.Dynamic.literal(name = name)
    id.foreach(result.updateDynamic("id")(_))
    icon.foreach(result.updateDynamic("icon")(_))
    result.asInstanceOf[PublicKeyCredentialRpEntity]
  }
}

@js.native
trait PublicKeyCredentialUserEntity extends js.Object {
  val id: ArrayBuffer     = js.native
  val name: String        = js.native
  val displayName: String = js.native
  @deprecated("WebAuthn no longer defines user icons", "1.0.0")
  val icon: js.UndefOr[String] = js.native
}

object PublicKeyCredentialUserEntity {
  def apply(
      id: ArrayBuffer,
      name: String,
      displayName: String,
      icon: Option[String] = None
  ): PublicKeyCredentialUserEntity = {
    val result = js.Dynamic.literal(id = id, name = name, displayName = displayName)
    icon.foreach(result.updateDynamic("icon")(_))
    result.asInstanceOf[PublicKeyCredentialUserEntity]
  }
}

@js.native
trait PublicKeyCredentialParameters extends js.Object {
  @JSName("type")
  val credentialType: String = js.native
  val alg: Int               = js.native
}

object PublicKeyCredentialParameters {
  def apply(
      alg: Int,
      credentialType: String = CredentialType.PublicKey
  ): PublicKeyCredentialParameters = {
    val result = js.Dynamic.literal(alg = alg)
    result.updateDynamic("type")(credentialType)
    result.asInstanceOf[PublicKeyCredentialParameters]
  }
}

@js.native
trait PublicKeyCredentialDescriptor extends js.Object {
  @JSName("type")
  val credentialType: String                   = js.native
  val id: ArrayBuffer                          = js.native
  val transports: js.UndefOr[js.Array[String]] = js.native
}

object PublicKeyCredentialDescriptor {
  def apply(
      id: ArrayBuffer,
      credentialType: String = CredentialType.PublicKey,
      transports: Seq[String] = Seq.empty
  ): PublicKeyCredentialDescriptor = {
    val result = js.Dynamic.literal(id = id)
    result.updateDynamic("type")(credentialType)
    if (transports.nonEmpty) result.updateDynamic("transports")(js.Array(transports*))
    result.asInstanceOf[PublicKeyCredentialDescriptor]
  }
}

@js.native
trait AuthenticatorSelectionCriteria extends js.Object {
  val authenticatorAttachment: js.UndefOr[String] = js.native
  val residentKey: js.UndefOr[String]             = js.native
  val requireResidentKey: js.UndefOr[Boolean]     = js.native
  val userVerification: js.UndefOr[String]        = js.native
}

object AuthenticatorSelectionCriteria {
  def apply(
      authenticatorAttachment: Option[String] = None,
      residentKey: Option[String] = None,
      requireResidentKey: Option[Boolean] = None,
      userVerification: Option[String] = None
  ): AuthenticatorSelectionCriteria = {
    val result = js.Dynamic.literal()
    authenticatorAttachment.foreach(result.updateDynamic("authenticatorAttachment")(_))
    residentKey.foreach(result.updateDynamic("residentKey")(_))
    requireResidentKey.foreach(result.updateDynamic("requireResidentKey")(_))
    userVerification.foreach(result.updateDynamic("userVerification")(_))
    result.asInstanceOf[AuthenticatorSelectionCriteria]
  }
}

/** Extension inputs are intentionally open because WebAuthn extensions are registry based. */
type AuthenticationExtensionsClientInputs = js.Object

@js.native
trait PublicKeyCredentialCreationOptions extends js.Object {
  val rp: PublicKeyCredentialRpEntity                                         = js.native
  val user: PublicKeyCredentialUserEntity                                     = js.native
  val challenge: ArrayBuffer                                                  = js.native
  val pubKeyCredParams: js.Array[PublicKeyCredentialParameters]               = js.native
  val timeout: js.UndefOr[Double]                                             = js.native
  val excludeCredentials: js.UndefOr[js.Array[PublicKeyCredentialDescriptor]] = js.native
  val authenticatorSelection: js.UndefOr[AuthenticatorSelectionCriteria]      = js.native
  val hints: js.UndefOr[js.Array[String]]                                     = js.native
  val attestation: js.UndefOr[String]                                         = js.native
  val attestationFormats: js.UndefOr[js.Array[String]]                        = js.native
  val extensions: js.UndefOr[AuthenticationExtensionsClientInputs]            = js.native
}

object PublicKeyCredentialCreationOptions {
  def apply(
      rp: PublicKeyCredentialRpEntity,
      user: PublicKeyCredentialUserEntity,
      challenge: ArrayBuffer,
      pubKeyCredParams: Seq[PublicKeyCredentialParameters],
      timeout: Option[Double] = None,
      attestation: Option[String] = None,
      excludeCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
      authenticatorSelection: Option[AuthenticatorSelectionCriteria] = None,
      hints: Seq[String] = Seq.empty,
      extensions: Option[AuthenticationExtensionsClientInputs] = None,
      attestationFormats: Seq[String] = Seq.empty
  ): PublicKeyCredentialCreationOptions = {
    val result = js.Dynamic.literal(
      rp = rp,
      user = user,
      challenge = challenge,
      pubKeyCredParams = js.Array(pubKeyCredParams*)
    )
    timeout.foreach(result.updateDynamic("timeout")(_))
    attestation.foreach(result.updateDynamic("attestation")(_))
    if (excludeCredentials.nonEmpty)
      result.updateDynamic("excludeCredentials")(js.Array(excludeCredentials*))
    authenticatorSelection.foreach(result.updateDynamic("authenticatorSelection")(_))
    if (hints.nonEmpty) result.updateDynamic("hints")(js.Array(hints*))
    extensions.foreach(result.updateDynamic("extensions")(_))
    if (attestationFormats.nonEmpty)
      result.updateDynamic("attestationFormats")(js.Array(attestationFormats*))
    result.asInstanceOf[PublicKeyCredentialCreationOptions]
  }
}

@js.native
trait PublicKeyCredentialRequestOptions extends js.Object {
  val challenge: ArrayBuffer                                                = js.native
  val timeout: js.UndefOr[Double]                                           = js.native
  val rpId: js.UndefOr[String]                                              = js.native
  val allowCredentials: js.UndefOr[js.Array[PublicKeyCredentialDescriptor]] = js.native
  val userVerification: js.UndefOr[String]                                  = js.native
  val hints: js.UndefOr[js.Array[String]]                                   = js.native
  val extensions: js.UndefOr[AuthenticationExtensionsClientInputs]          = js.native
}

object PublicKeyCredentialRequestOptions {
  def apply(
      challenge: ArrayBuffer,
      timeout: Option[Double] = None,
      rpId: Option[String] = None,
      allowCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
      userVerification: Option[String] = None,
      hints: Seq[String] = Seq.empty,
      extensions: Option[AuthenticationExtensionsClientInputs] = None
  ): PublicKeyCredentialRequestOptions = {
    val result = js.Dynamic.literal(challenge = challenge)
    timeout.foreach(result.updateDynamic("timeout")(_))
    rpId.foreach(result.updateDynamic("rpId")(_))
    if (allowCredentials.nonEmpty)
      result.updateDynamic("allowCredentials")(js.Array(allowCredentials*))
    userVerification.foreach(result.updateDynamic("userVerification")(_))
    if (hints.nonEmpty) result.updateDynamic("hints")(js.Array(hints*))
    extensions.foreach(result.updateDynamic("extensions")(_))
    result.asInstanceOf[PublicKeyCredentialRequestOptions]
  }
}

final case class CredentialCreationSettings(
    signal: Option[AbortSignal] = None,
    mediation: Option[String] = None
)

final case class CredentialRequestSettings(
    signal: Option[AbortSignal] = None,
    mediation: Option[String] = None
)

@js.native
trait CredentialCreationOptions extends js.Object {
  val publicKey: PublicKeyCredentialCreationOptions = js.native
  val signal: js.UndefOr[AbortSignal]               = js.native
  val mediation: js.UndefOr[String]                 = js.native
}

object CredentialCreationOptions {
  def withSettings(
      publicKey: PublicKeyCredentialCreationOptions,
      settings: CredentialCreationSettings = CredentialCreationSettings()
  ): CredentialCreationOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    settings.signal.foreach(result.updateDynamic("signal")(_))
    settings.mediation.foreach(result.updateDynamic("mediation")(_))
    result.asInstanceOf[CredentialCreationOptions]
  }

  def apply(
      publicKey: PublicKeyCredentialCreationOptions,
      signal: Option[js.Any] = None
  ): CredentialCreationOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialCreationOptions]
  }
}

@js.native
trait CredentialRequestOptions extends js.Object {
  val publicKey: PublicKeyCredentialRequestOptions = js.native
  val mediation: js.UndefOr[String]                = js.native
  val signal: js.UndefOr[AbortSignal]              = js.native
}

object CredentialRequestOptions {
  def withSettings(
      publicKey: PublicKeyCredentialRequestOptions,
      settings: CredentialRequestSettings = CredentialRequestSettings()
  ): CredentialRequestOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    settings.mediation.foreach(result.updateDynamic("mediation")(_))
    settings.signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialRequestOptions]
  }

  def apply(
      publicKey: PublicKeyCredentialRequestOptions,
      mediation: Option[String] = None,
      signal: Option[js.Any] = None
  ): CredentialRequestOptions = {
    val result = js.Dynamic.literal(publicKey = publicKey)
    mediation.foreach(result.updateDynamic("mediation")(_))
    signal.foreach(result.updateDynamic("signal")(_))
    result.asInstanceOf[CredentialRequestOptions]
  }
}

@js.native
trait AuthenticatorResponse extends js.Object {
  val clientDataJSON: ArrayBuffer = js.native
}

@js.native
trait AuthenticatorAttestationResponse extends AuthenticatorResponse {
  val attestationObject: ArrayBuffer                              = js.native
  val getAuthenticatorData: js.UndefOr[js.Function0[ArrayBuffer]] = js.native
  val getPublicKey: js.UndefOr[js.Function0[ArrayBuffer | Null]]  = js.native
  val getPublicKeyAlgorithm: js.UndefOr[js.Function0[Int]]        = js.native
  val getTransports: js.UndefOr[js.Function0[js.Array[String]]]   = js.native
}

@js.native
trait AuthenticatorAssertionResponse extends AuthenticatorResponse {
  val authenticatorData: ArrayBuffer = js.native
  val signature: ArrayBuffer         = js.native
  val userHandle: ArrayBuffer | Null = js.native
}

@js.native
trait PublicKeyCredential extends js.Object {
  val id: String                             = js.native
  val rawId: ArrayBuffer                     = js.native
  val response: AuthenticatorResponse        = js.native
  val authenticatorAttachment: String | Null = js.native
  @JSName("type")
  val credentialType: String                      = js.native
  def getClientExtensionResults(): js.Object      = js.native
  val toJSON: js.UndefOr[js.Function0[js.Object]] = js.native
}

final case class UnknownCredentialOptions(rpId: String, credentialId: String) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, credentialId = credentialId)
}

final case class AllAcceptedCredentialsOptions(
    rpId: String,
    userId: String,
    allAcceptedCredentialIds: Seq[String]
) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(
      rpId = rpId,
      userId = userId,
      allAcceptedCredentialIds = js.Array(allAcceptedCredentialIds*)
    )
}

final case class CurrentUserDetailsOptions(
    rpId: String,
    userId: String,
    name: String,
    displayName: String
) {
  private[webauthn] def toJsObject: js.Object =
    js.Dynamic.literal(rpId = rpId, userId = userId, name = name, displayName = displayName)
}
