/** Extensible WebAuthn string values. Unknown future values remain accepted by the facades. */

export const CredentialType = {
  PublicKey: "public-key",
} as const;

export const CoseAlgorithmIdentifier = {
  ES256: -7,
  EdDSA: -8,
  ES384: -35,
  ES512: -36,
  RS256: -257,
  RS384: -258,
  RS512: -259,
} as const;

export const AuthenticatorAttachment = {
  Platform: "platform",
  CrossPlatform: "cross-platform",
} as const;

export const ResidentKeyRequirement = {
  Discouraged: "discouraged",
  Preferred: "preferred",
  Required: "required",
} as const;

export const UserVerificationRequirement = {
  Discouraged: "discouraged",
  Preferred: "preferred",
  Required: "required",
} as const;

export const AttestationConveyancePreference = {
  None: "none",
  Indirect: "indirect",
  Direct: "direct",
  Enterprise: "enterprise",
} as const;

export const PublicKeyCredentialHint = {
  SecurityKey: "security-key",
  ClientDevice: "client-device",
  Hybrid: "hybrid",
} as const;

export const AuthenticatorTransport = {
  Usb: "usb",
  Nfc: "nfc",
  Ble: "ble",
  SmartCard: "smart-card",
  Hybrid: "hybrid",
  Internal: "internal",
} as const;

export const CredentialMediationRequirement = {
  Silent: "silent",
  Optional: "optional",
  Required: "required",
  Conditional: "conditional",
} as const;

export const WebAuthnClientCapability = {
  ConditionalCreate: "conditionalCreate",
  ConditionalGet: "conditionalGet",
  HybridTransport: "hybridTransport",
  PasskeyPlatformAuthenticator: "passkeyPlatformAuthenticator",
  UserVerifyingPlatformAuthenticator: "userVerifyingPlatformAuthenticator",
  RelatedOrigins: "relatedOrigins",
  SignalAllAcceptedCredentials: "signalAllAcceptedCredentials",
  SignalCurrentUserDetails: "signalCurrentUserDetails",
  SignalUnknownCredential: "signalUnknownCredential",
} as const;
