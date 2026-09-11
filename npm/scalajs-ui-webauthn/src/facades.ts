export type AuthenticationExtensionsClientInputs = Record<string, unknown>;

export interface PublicKeyCredentialRpEntity {
  readonly id?: string;
  readonly name: string;
  /** WebAuthn no longer defines RP icons; retained for compatibility. */
  readonly icon?: string;
}

export function publicKeyCredentialRpEntity(
  name: string,
  options: { readonly id?: string; readonly icon?: string } = {},
): PublicKeyCredentialRpEntity {
  return { name, ...defined(options) } as PublicKeyCredentialRpEntity;
}

export interface PublicKeyCredentialUserEntity {
  readonly id: ArrayBuffer;
  readonly name: string;
  readonly displayName: string;
  /** WebAuthn no longer defines user icons; retained for compatibility. */
  readonly icon?: string;
}

export function publicKeyCredentialUserEntity(
  id: ArrayBuffer,
  name: string,
  displayName: string,
  options: { readonly icon?: string } = {},
): PublicKeyCredentialUserEntity {
  return { id, name, displayName, ...defined(options) } as PublicKeyCredentialUserEntity;
}

export interface PublicKeyCredentialParameters {
  readonly type: string;
  readonly alg: number;
}

export function publicKeyCredentialParameters(
  alg: number,
  credentialType = "public-key",
): PublicKeyCredentialParameters {
  return { type: credentialType, alg };
}

export interface PublicKeyCredentialDescriptor {
  readonly type: string;
  readonly id: ArrayBuffer;
  readonly transports?: readonly string[];
}

export function publicKeyCredentialDescriptor(
  id: ArrayBuffer,
  options: { readonly credentialType?: string; readonly transports?: readonly string[] } = {},
): PublicKeyCredentialDescriptor {
  return {
    type: options.credentialType ?? "public-key",
    id,
    ...defined({ transports: options.transports?.length ? [...options.transports] : undefined }),
  } as PublicKeyCredentialDescriptor;
}

export interface AuthenticatorSelectionCriteria {
  readonly authenticatorAttachment?: string;
  readonly residentKey?: string;
  readonly requireResidentKey?: boolean;
  readonly userVerification?: string;
}

export function authenticatorSelectionCriteria(
  options: AuthenticatorSelectionCriteria = {},
): AuthenticatorSelectionCriteria {
  return defined({ ...options }) as AuthenticatorSelectionCriteria;
}

export interface PublicKeyCredentialCreationOptions {
  readonly rp: PublicKeyCredentialRpEntity;
  readonly user: PublicKeyCredentialUserEntity;
  readonly challenge: ArrayBuffer;
  readonly pubKeyCredParams: readonly PublicKeyCredentialParameters[];
  readonly timeout?: number;
  readonly excludeCredentials?: readonly PublicKeyCredentialDescriptor[];
  readonly authenticatorSelection?: AuthenticatorSelectionCriteria;
  readonly hints?: readonly string[];
  readonly attestation?: string;
  readonly attestationFormats?: readonly string[];
  readonly extensions?: AuthenticationExtensionsClientInputs;
}

export type PublicKeyCredentialCreationOptionsOverrides = Omit<
  PublicKeyCredentialCreationOptions,
  "rp" | "user" | "challenge" | "pubKeyCredParams"
>;

export function publicKeyCredentialCreationOptions(
  rp: PublicKeyCredentialRpEntity,
  user: PublicKeyCredentialUserEntity,
  challenge: ArrayBuffer,
  pubKeyCredParams: readonly PublicKeyCredentialParameters[],
  options: PublicKeyCredentialCreationOptionsOverrides = {},
): PublicKeyCredentialCreationOptions {
  return defined({
    rp,
    user,
    challenge,
    pubKeyCredParams: [...pubKeyCredParams],
    timeout: options.timeout,
    attestation: options.attestation,
    excludeCredentials: options.excludeCredentials?.length ? [...options.excludeCredentials] : undefined,
    authenticatorSelection: options.authenticatorSelection,
    hints: options.hints?.length ? [...options.hints] : undefined,
    extensions: options.extensions,
    attestationFormats: options.attestationFormats?.length ? [...options.attestationFormats] : undefined,
  }) as unknown as PublicKeyCredentialCreationOptions;
}

export interface PublicKeyCredentialRequestOptions {
  readonly challenge: ArrayBuffer;
  readonly timeout?: number;
  readonly rpId?: string;
  readonly allowCredentials?: readonly PublicKeyCredentialDescriptor[];
  readonly userVerification?: string;
  readonly hints?: readonly string[];
  readonly extensions?: AuthenticationExtensionsClientInputs;
}

export type PublicKeyCredentialRequestOptionsOverrides = Omit<
  PublicKeyCredentialRequestOptions,
  "challenge"
>;

export function publicKeyCredentialRequestOptions(
  challenge: ArrayBuffer,
  options: PublicKeyCredentialRequestOptionsOverrides = {},
): PublicKeyCredentialRequestOptions {
  return defined({
    challenge,
    timeout: options.timeout,
    rpId: options.rpId,
    allowCredentials: options.allowCredentials?.length ? [...options.allowCredentials] : undefined,
    userVerification: options.userVerification,
    hints: options.hints?.length ? [...options.hints] : undefined,
    extensions: options.extensions,
  }) as unknown as PublicKeyCredentialRequestOptions;
}

export interface CredentialCreationSettings {
  readonly signal?: AbortSignal;
  readonly mediation?: string;
}

export interface CredentialRequestSettings {
  readonly signal?: AbortSignal;
  readonly mediation?: string;
}

export interface CredentialCreationOptions {
  readonly publicKey: PublicKeyCredentialCreationOptions;
  readonly signal?: AbortSignal;
  readonly mediation?: string;
}

export function credentialCreationOptions(
  publicKey: PublicKeyCredentialCreationOptions,
  settings: CredentialCreationSettings = {},
): CredentialCreationOptions {
  return defined({ publicKey, signal: settings.signal, mediation: settings.mediation }) as unknown as CredentialCreationOptions;
}

export interface CredentialRequestOptions {
  readonly publicKey: PublicKeyCredentialRequestOptions;
  readonly mediation?: string;
  readonly signal?: AbortSignal;
}

export function credentialRequestOptions(
  publicKey: PublicKeyCredentialRequestOptions,
  settings: CredentialRequestSettings = {},
): CredentialRequestOptions {
  return defined({ publicKey, mediation: settings.mediation, signal: settings.signal }) as unknown as CredentialRequestOptions;
}

export interface UnknownCredentialOptions {
  readonly rpId: string;
  readonly credentialId: string;
}

export interface AllAcceptedCredentialsOptions {
  readonly rpId: string;
  readonly userId: string;
  readonly allAcceptedCredentialIds: readonly string[];
}

export interface CurrentUserDetailsOptions {
  readonly rpId: string;
  readonly userId: string;
  readonly name: string;
  readonly displayName: string;
}

function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}

// Upper-case aliases mirror the Scala companion-object names while the
// lower-case functions fit normal TypeScript call-site conventions.
export const PublicKeyCredentialRpEntity = publicKeyCredentialRpEntity;
export const PublicKeyCredentialUserEntity = publicKeyCredentialUserEntity;
export const PublicKeyCredentialParameters = publicKeyCredentialParameters;
export const PublicKeyCredentialDescriptor = publicKeyCredentialDescriptor;
export const AuthenticatorSelectionCriteria = authenticatorSelectionCriteria;
export const PublicKeyCredentialCreationOptions = publicKeyCredentialCreationOptions;
export const PublicKeyCredentialRequestOptions = publicKeyCredentialRequestOptions;
export const CredentialCreationOptions = credentialCreationOptions;
export const CredentialRequestOptions = credentialRequestOptions;
