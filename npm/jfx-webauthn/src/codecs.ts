import { Base64Url } from "./base64-url.js";
import {
  authenticatorSelectionCriteria,
  publicKeyCredentialCreationOptions,
  publicKeyCredentialDescriptor,
  publicKeyCredentialParameters,
  publicKeyCredentialRequestOptions,
  publicKeyCredentialRpEntity,
  publicKeyCredentialUserEntity,
  type AuthenticationExtensionsClientInputs,
  type PublicKeyCredentialCreationOptions,
  type PublicKeyCredentialCreationOptionsOverrides,
  type PublicKeyCredentialDescriptor,
  type PublicKeyCredentialRequestOptions,
  type PublicKeyCredentialRequestOptionsOverrides,
} from "./facades.js";

export interface PublicKeyCredentialRpEntityJSON {
  readonly id?: string;
  readonly name: string;
  readonly icon?: string;
}

export interface PublicKeyCredentialUserEntityJSON {
  readonly id: string;
  readonly name: string;
  readonly displayName: string;
  readonly icon?: string;
}

export interface PublicKeyCredentialParametersJSON {
  readonly type?: string;
  readonly alg: number;
}

export interface PublicKeyCredentialDescriptorJSON {
  readonly type?: string;
  readonly id: string;
  readonly transports?: readonly string[];
}

export interface PublicKeyCredentialCreationOptionsJSON {
  readonly rp: PublicKeyCredentialRpEntityJSON;
  readonly user: PublicKeyCredentialUserEntityJSON;
  readonly challenge: string;
  readonly pubKeyCredParams: readonly PublicKeyCredentialParametersJSON[];
  readonly timeout?: number;
  readonly excludeCredentials?: readonly PublicKeyCredentialDescriptorJSON[];
  readonly authenticatorSelection?: {
    readonly authenticatorAttachment?: string;
    readonly residentKey?: string;
    readonly requireResidentKey?: boolean;
    readonly userVerification?: string;
  };
  readonly hints?: readonly string[];
  readonly attestation?: string;
  readonly attestationFormats?: readonly string[];
  readonly extensions?: AuthenticationExtensionsClientInputs;
}

export interface PublicKeyCredentialRequestOptionsJSON {
  readonly challenge: string;
  readonly timeout?: number;
  readonly rpId?: string;
  readonly allowCredentials?: readonly PublicKeyCredentialDescriptorJSON[];
  readonly userVerification?: string;
  readonly hints?: readonly string[];
  readonly extensions?: AuthenticationExtensionsClientInputs;
}

export interface RegistrationResponse {
  readonly clientDataJSON: string;
  readonly attestationObject: string;
  readonly transports: readonly string[];
  readonly authenticatorData?: string;
  readonly publicKey?: string;
  readonly publicKeyAlgorithm?: number;
}

export interface AuthenticationResponse {
  readonly clientDataJSON: string;
  readonly authenticatorData: string;
  readonly signature: string;
  readonly userHandle?: string;
}

export interface WebAuthnCredentialPayload {
  readonly id: string;
  readonly rawId: string;
  readonly response: RegistrationResponse | AuthenticationResponse;
  readonly authenticatorAttachment?: string;
  readonly clientExtensionResults: Record<string, unknown>;
  readonly type: string;
}

export interface RegistrationCredential extends WebAuthnCredentialPayload {
  readonly response: RegistrationResponse;
}

export interface AuthenticationCredential extends WebAuthnCredentialPayload {
  readonly response: AuthenticationResponse;
}

export function creationOptionsFromJson(
  value: unknown,
): PublicKeyCredentialCreationOptions {
  const input = typeof value === "string" ? parseJsonObject(value) : value;
  const parser = nativeParser("parseCreationOptionsFromJSON");
  return parser
    ? parser(input) as PublicKeyCredentialCreationOptions
    : creationOptionsFallback(input);
}

export function requestOptionsFromJson(value: unknown): PublicKeyCredentialRequestOptions {
  const input = typeof value === "string" ? parseJsonObject(value) : value;
  const parser = nativeParser("parseRequestOptionsFromJSON");
  return parser
    ? parser(input) as PublicKeyCredentialRequestOptions
    : requestOptionsFallback(input);
}

export function registrationCredential(credential: PublicKeyCredential): RegistrationCredential {
  const response = credential.response as AuthenticatorAttestationResponse;
  const native = nativeCredentialJson(credential);
  const nativeResponse = native === undefined ? undefined : requireObject(native.response, "response");

  return {
    id: native ? requiredString(native, "id") : credential.id,
    rawId: native ? requiredString(native, "rawId") : Base64Url.encode(credential.rawId),
    type: native ? optionalString(native, "type") ?? credential.type : credential.type,
    response: {
      clientDataJSON: nativeResponse
        ? requiredString(nativeResponse, "clientDataJSON")
        : Base64Url.encode(response.clientDataJSON),
      attestationObject: nativeResponse
        ? requiredString(nativeResponse, "attestationObject")
        : Base64Url.encode(response.attestationObject),
      transports: nativeResponse
        ? stringArray(nativeResponse, "transports")
        : typeof response.getTransports === "function"
          ? [...response.getTransports()]
          : [],
      ...defined({
        authenticatorData: nativeResponse
          ? optionalString(nativeResponse, "authenticatorData")
          : optionalMethodBuffer(response.getAuthenticatorData),
        publicKey: nativeResponse
          ? optionalString(nativeResponse, "publicKey")
          : optionalMethodBuffer(response.getPublicKey),
        publicKeyAlgorithm: nativeResponse
          ? optionalNumber(nativeResponse, "publicKeyAlgorithm")
          : optionalMethodNumber(response.getPublicKeyAlgorithm),
      }),
    },
    ...defined({
      authenticatorAttachment: native
        ? optionalString(native, "authenticatorAttachment")
        : credential.authenticatorAttachment ?? undefined,
    }),
    clientExtensionResults: native && optionalObject(native, "clientExtensionResults")
      ? jsonSafeObject(optionalObject(native, "clientExtensionResults"))
      : jsonSafeObject(credential.getClientExtensionResults() as unknown),
  };
}

export function authenticationCredential(
  credential: PublicKeyCredential,
): AuthenticationCredential {
  const response = credential.response as AuthenticatorAssertionResponse;
  const native = nativeCredentialJson(credential);
  const nativeResponse = native === undefined ? undefined : requireObject(native.response, "response");

  return {
    id: native ? requiredString(native, "id") : credential.id,
    rawId: native ? requiredString(native, "rawId") : Base64Url.encode(credential.rawId),
    type: native ? optionalString(native, "type") ?? credential.type : credential.type,
    response: {
      clientDataJSON: nativeResponse
        ? requiredString(nativeResponse, "clientDataJSON")
        : Base64Url.encode(response.clientDataJSON),
      authenticatorData: nativeResponse
        ? requiredString(nativeResponse, "authenticatorData")
        : Base64Url.encode(response.authenticatorData),
      signature: nativeResponse
        ? requiredString(nativeResponse, "signature")
        : Base64Url.encode(response.signature),
      ...defined({
        userHandle: nativeResponse
          ? optionalString(nativeResponse, "userHandle")
          : response.userHandle === null
            ? undefined
            : Base64Url.encode(response.userHandle),
      }),
    },
    ...defined({
      authenticatorAttachment: native
        ? optionalString(native, "authenticatorAttachment")
        : credential.authenticatorAttachment ?? undefined,
    }),
    clientExtensionResults: native && optionalObject(native, "clientExtensionResults")
      ? jsonSafeObject(optionalObject(native, "clientExtensionResults"))
      : jsonSafeObject(credential.getClientExtensionResults() as unknown),
  };
}

/** Uses native `PublicKeyCredential.toJSON()` when available. */
export function credentialToJson(credential: PublicKeyCredential): Record<string, unknown> {
  const native = nativeCredentialJson(credential);
  if (native !== undefined) return native;

  const response = credential.response as unknown as Record<string, unknown>;
  return Object.prototype.hasOwnProperty.call(response, "attestationObject")
    ? registrationCredential(credential) as unknown as Record<string, unknown>
    : authenticationCredential(credential) as unknown as Record<string, unknown>;
}

export function credentialPayloadToJson(payload: WebAuthnCredentialPayload): string {
  return JSON.stringify(payload);
}

function creationOptionsFallback(value: unknown): PublicKeyCredentialCreationOptions {
  const root = requireObject(value, "creation options");
  const rp = requireObject(root.rp, "rp");
  const user = requireObject(root.user, "user");
  const rawSelection = optionalObject(root, "authenticatorSelection");

  return publicKeyCredentialCreationOptions(
    publicKeyCredentialRpEntity(requiredString(rp, "name"),
      defined({ id: optionalString(rp, "id") })),
    publicKeyCredentialUserEntity(
      Base64Url.decode(requiredString(user, "id")),
      requiredString(user, "name"),
      requiredString(user, "displayName"),
    ),
    Base64Url.decode(requiredString(root, "challenge")),
    objectArray(root, "pubKeyCredParams", true).map((item) =>
      publicKeyCredentialParameters(requiredInteger(item, "alg"), optionalString(item, "type") ?? "public-key"),
    ),
    defined({
      timeout: optionalUnsignedInteger(root, "timeout"),
      attestation: optionalString(root, "attestation"),
      excludeCredentials: objectArray(root, "excludeCredentials").map(descriptorFromJson),
      authenticatorSelection: rawSelection
        ? authenticatorSelectionCriteria(defined({
            authenticatorAttachment: optionalString(rawSelection, "authenticatorAttachment"),
            residentKey: optionalString(rawSelection, "residentKey"),
            requireResidentKey: optionalBoolean(rawSelection, "requireResidentKey"),
            userVerification: optionalString(rawSelection, "userVerification"),
          }))
        : undefined,
      hints: stringArray(root, "hints"),
      extensions: optionalObject(root, "extensions"),
      attestationFormats: stringArray(root, "attestationFormats"),
    }) as PublicKeyCredentialCreationOptionsOverrides,
  );
}

function requestOptionsFallback(value: unknown): PublicKeyCredentialRequestOptions {
  const root = requireObject(value, "request options");
  return publicKeyCredentialRequestOptions(Base64Url.decode(requiredString(root, "challenge")), defined({
    timeout: optionalUnsignedInteger(root, "timeout"),
    rpId: optionalString(root, "rpId"),
    allowCredentials: objectArray(root, "allowCredentials").map(descriptorFromJson),
    userVerification: optionalString(root, "userVerification"),
    hints: stringArray(root, "hints"),
    extensions: optionalObject(root, "extensions"),
  }) as PublicKeyCredentialRequestOptionsOverrides);
}

function descriptorFromJson(value: Record<string, unknown>): PublicKeyCredentialDescriptor {
  return publicKeyCredentialDescriptor(Base64Url.decode(requiredString(value, "id")), {
    credentialType: optionalString(value, "type") ?? "public-key",
    transports: stringArray(value, "transports"),
  });
}

function nativeParser(name: string): ((value: unknown) => unknown) | undefined {
  const api = publicKeyCredentialApi();
  const method = api?.[name];
  return typeof method === "function" ? (value) => method.call(api, value) : undefined;
}

function nativeCredentialJson(credential: PublicKeyCredential): Record<string, unknown> | undefined {
  if (typeof credential.toJSON !== "function") return undefined;
  const value = credential.toJSON();
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function publicKeyCredentialApi(): Record<string, unknown> | undefined {
  const value = globalThis.PublicKeyCredential;
  return value && typeof value === "function"
    ? value as unknown as Record<string, unknown>
    : undefined;
}

function requiredString(value: Record<string, unknown>, field: string): string {
  const raw = optional(value, field);
  if (typeof raw === "string") return raw;
  if (raw === undefined) throw missingField(field);
  throw invalidField(field, "string expected");
}

function optionalString(value: Record<string, unknown>, field: string): string | undefined {
  const raw = optional(value, field);
  if (raw === undefined) return undefined;
  if (typeof raw === "string") return raw;
  throw invalidField(field, "string expected");
}

function requiredInteger(value: Record<string, unknown>, field: string): number {
  const raw = optional(value, field);
  if (typeof raw === "number" && Number.isFinite(raw) && Number.isInteger(raw)) return raw;
  if (raw === undefined) throw missingField(field);
  throw invalidField(field, "integer expected");
}

function optionalNumber(value: Record<string, unknown>, field: string): number | undefined {
  const raw = optional(value, field);
  if (raw === undefined) return undefined;
  if (typeof raw === "number") return raw;
  throw invalidField(field, "number expected");
}

function optionalBoolean(value: Record<string, unknown>, field: string): boolean | undefined {
  const raw = optional(value, field);
  if (raw === undefined) return undefined;
  if (typeof raw === "boolean") return raw;
  throw invalidField(field, "boolean expected");
}

function optionalUnsignedInteger(value: Record<string, unknown>, field: string): number | undefined {
  const raw = optional(value, field);
  if (raw === undefined) return undefined;
  if (typeof raw === "number" && Number.isInteger(raw) && raw >= 0 && raw <= 4294967295) {
    return raw;
  }
  throw invalidField(field, "unsigned 32-bit integer expected");
}

function optionalObject(value: Record<string, unknown>, field: string): Record<string, unknown> | undefined {
  const raw = optional(value, field);
  return raw === undefined ? undefined : requireObject(raw, field);
}

function objectArray(
  value: Record<string, unknown>,
  field: string,
  required = false,
): Record<string, unknown>[] {
  const raw = optional(value, field);
  if (raw === undefined) {
    if (required) throw missingField(field);
    return [];
  }
  if (!Array.isArray(raw)) throw invalidField(field, "array expected");
  return raw.map((item) => requireObject(item, field));
}

function stringArray(value: Record<string, unknown>, field: string): string[] {
  const raw = optional(value, field);
  if (raw === undefined) return [];
  if (!Array.isArray(raw)) throw invalidField(field, "array expected");
  return raw.map((item) => {
    if (typeof item !== "string") throw invalidField(field, "string array expected");
    return item;
  });
}

function requireObject(value: unknown, label: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`Invalid WebAuthn ${label}: object expected`);
  }
  return value as Record<string, unknown>;
}

function optional(value: Record<string, unknown>, field: string): unknown {
  const selected = value[field];
  return selected === null || selected === undefined ? undefined : selected;
}

function optionalMethodBuffer(
  method: (() => ArrayBuffer | null) | undefined,
): string | undefined {
  if (typeof method !== "function") return undefined;
  const value = method();
  return value === null ? undefined : Base64Url.encode(value);
}

function optionalMethodNumber(method: (() => number) | undefined): number | undefined {
  return typeof method === "function" ? method() : undefined;
}

function jsonSafeObject(value: unknown): Record<string, unknown> {
  return jsonSafe(value) as Record<string, unknown>;
}

function jsonSafe(value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (value instanceof ArrayBuffer) return Base64Url.encode(value);
  if (ArrayBuffer.isView(value)) {
    return Base64Url.encode(value as ArrayBufferView);
  }
  if (Array.isArray(value)) return value.map(jsonSafe);
  if (typeof value === "object") {
    const result: Record<string, unknown> = {};
    for (const [key, entry] of Object.entries(value)) result[key] = jsonSafe(entry);
    return result;
  }
  return value;
}

function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}

function missingField(field: string): TypeError {
  return new TypeError(`Missing WebAuthn field '${field}'`);
}

function invalidField(field: string, reason: string): TypeError {
  return new TypeError(`Invalid WebAuthn field '${field}': ${reason}`);
}

function parseJsonObject(json: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(json);
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
      throw new TypeError("Invalid WebAuthn JSON root: object expected");
    }
    return value as Record<string, unknown>;
  } catch (error) {
    if (error instanceof TypeError && error.message.includes("object expected")) throw error;
    throw new TypeError("Invalid JSON", { cause: error });
  }
}

export const WebAuthnCodecs = {
  creationOptionsFromJson,
  requestOptionsFromJson,
  registrationCredential,
  authenticationCredential,
  credentialToJson,
} as const;
