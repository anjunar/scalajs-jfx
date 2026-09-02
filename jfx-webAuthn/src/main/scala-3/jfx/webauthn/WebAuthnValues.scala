package jfx.webauthn

/** Extensible WebAuthn string values. Unknown future values remain accepted by the facades. */
object CredentialType {
  final val PublicKey = "public-key"
}

object CoseAlgorithmIdentifier {
  final val ES256 = -7
  final val EdDSA = -8
  final val ES384 = -35
  final val ES512 = -36
  final val RS256 = -257
  final val RS384 = -258
  final val RS512 = -259
}

object AuthenticatorAttachment {
  final val Platform      = "platform"
  final val CrossPlatform = "cross-platform"
}

object ResidentKeyRequirement {
  final val Discouraged = "discouraged"
  final val Preferred   = "preferred"
  final val Required    = "required"
}

object UserVerificationRequirement {
  final val Discouraged = "discouraged"
  final val Preferred   = "preferred"
  final val Required    = "required"
}

object AttestationConveyancePreference {
  final val None       = "none"
  final val Indirect   = "indirect"
  final val Direct     = "direct"
  final val Enterprise = "enterprise"
}

object PublicKeyCredentialHint {
  final val SecurityKey  = "security-key"
  final val ClientDevice = "client-device"
  final val Hybrid       = "hybrid"
}

object AuthenticatorTransport {
  final val Usb       = "usb"
  final val Nfc       = "nfc"
  final val Ble       = "ble"
  final val SmartCard = "smart-card"
  final val Hybrid    = "hybrid"
  final val Internal  = "internal"
}

object CredentialMediationRequirement {
  final val Silent      = "silent"
  final val Optional    = "optional"
  final val Required    = "required"
  final val Conditional = "conditional"
}

object WebAuthnClientCapability {
  final val ConditionalCreate                  = "conditionalCreate"
  final val ConditionalGet                     = "conditionalGet"
  final val HybridTransport                    = "hybridTransport"
  final val PasskeyPlatformAuthenticator       = "passkeyPlatformAuthenticator"
  final val UserVerifyingPlatformAuthenticator = "userVerifyingPlatformAuthenticator"
  final val RelatedOrigins                     = "relatedOrigins"
  final val SignalAllAcceptedCredentials       = "signalAllAcceptedCredentials"
  final val SignalCurrentUserDetails           = "signalCurrentUserDetails"
  final val SignalUnknownCredential            = "signalUnknownCredential"
}
