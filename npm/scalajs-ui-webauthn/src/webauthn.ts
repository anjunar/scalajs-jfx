import {
  authenticationCredential,
  creationOptionsFromJson,
  requestOptionsFromJson,
  registrationCredential,
  type AuthenticationCredential,
  type PublicKeyCredentialCreationOptionsJSON,
  type PublicKeyCredentialRequestOptionsJSON,
  type RegistrationCredential,
} from "./codecs.js";
import {
  credentialCreationOptions,
  credentialRequestOptions,
  type AllAcceptedCredentialsOptions,
  type CredentialCreationSettings,
  type CredentialRequestSettings,
  type CurrentUserDetailsOptions,
  type PublicKeyCredentialCreationOptions,
  type PublicKeyCredentialRequestOptions,
  type UnknownCredentialOptions,
} from "./facades.js";

/** Whether the browser exposes both PublicKeyCredential and navigator.credentials. */
export function isSupported(): boolean {
  return publicKeyCredentialApi() !== undefined && credentialsContainer() !== undefined;
}

/** Whether WebAuthn can run in the current secure browser context. */
export function isAvailable(): boolean {
  const secure = globalThis.isSecureContext;
  return isSupported() && (secure === undefined || secure);
}

export async function isUserVerifyingPlatformAuthenticatorAvailable(): Promise<boolean> {
  return isUserVerifyingPlatformAuthenticatorAvailablePromise();
}

export function isUserVerifyingPlatformAuthenticatorAvailablePromise(): Promise<boolean> {
  return booleanCapability("isUserVerifyingPlatformAuthenticatorAvailable");
}

export async function isConditionalMediationAvailable(): Promise<boolean> {
  return isConditionalMediationAvailablePromise();
}

export function isConditionalMediationAvailablePromise(): Promise<boolean> {
  return booleanCapability("isConditionalMediationAvailable");
}

/** Returns all capabilities disclosed by the browser. Missing capabilities are not inferred. */
export async function clientCapabilities(): Promise<Record<string, boolean>> {
  return clientCapabilitiesPromise();
}

export async function clientCapabilitiesPromise(): Promise<Record<string, boolean>> {
  const value = await clientCapabilitiesValue();
  const result: Record<string, boolean> = {};
  if (value && typeof value === "object") {
    for (const [key, capability] of Object.entries(value)) {
      if (typeof capability === "boolean") result[key] = capability;
    }
  }
  return result;
}

export function register(
  options: PublicKeyCredentialCreationOptions,
  settings: CredentialCreationSettings = {},
): Promise<RegistrationCredential> {
  return registerPromise(options, settings);
}

export function registerJson(
  options: PublicKeyCredentialCreationOptionsJSON | string,
  settings: CredentialCreationSettings = {},
): Promise<RegistrationCredential> {
  return registerJsonPromise(options, settings);
}

export function registerPromise(
  options: PublicKeyCredentialCreationOptions,
  settings: CredentialCreationSettings = {},
): Promise<RegistrationCredential> {
  return credentialOperation(
    "create",
    credentialCreationOptions(options, settings),
  ).then((credential) => registrationCredential(credential));
}

export function registerJsonPromise(
  options: PublicKeyCredentialCreationOptionsJSON | string,
  settings: CredentialCreationSettings = {},
): Promise<RegistrationCredential> {
  return attemptPromise(() => registerPromise(parseCreationOptions(options), settings));
}

export function authenticate(
  options: PublicKeyCredentialRequestOptions,
  settings: CredentialRequestSettings = {},
): Promise<AuthenticationCredential> {
  return authenticatePromise(options, settings);
}

export function authenticateJson(
  options: PublicKeyCredentialRequestOptionsJSON | string,
  settings: CredentialRequestSettings = {},
): Promise<AuthenticationCredential> {
  return authenticateJsonPromise(options, settings);
}

export function authenticatePromise(
  options: PublicKeyCredentialRequestOptions,
  settings: CredentialRequestSettings = {},
): Promise<AuthenticationCredential> {
  return credentialOperation(
    "get",
    credentialRequestOptions(options, settings),
  ).then((credential) => authenticationCredential(credential));
}

export function authenticateJsonPromise(
  options: PublicKeyCredentialRequestOptionsJSON | string,
  settings: CredentialRequestSettings = {},
): Promise<AuthenticationCredential> {
  return attemptPromise(() => authenticatePromise(parseRequestOptions(options), settings));
}

export async function signalUnknownCredential(options: UnknownCredentialOptions): Promise<void> {
  await signalUnknownCredentialPromise(options);
}

export function signalUnknownCredentialPromise(options: UnknownCredentialOptions): Promise<void> {
  return callStaticPromise("signalUnknownCredential", options);
}

export async function signalAllAcceptedCredentials(
  options: AllAcceptedCredentialsOptions,
): Promise<void> {
  await signalAllAcceptedCredentialsPromise(options);
}

export function signalAllAcceptedCredentialsPromise(
  options: AllAcceptedCredentialsOptions,
): Promise<void> {
  return callStaticPromise("signalAllAcceptedCredentials", options);
}

export async function signalCurrentUserDetails(options: CurrentUserDetailsOptions): Promise<void> {
  await signalCurrentUserDetailsPromise(options);
}

export function signalCurrentUserDetailsPromise(
  options: CurrentUserDetailsOptions,
): Promise<void> {
  return callStaticPromise("signalCurrentUserDetails", options);
}

function parseCreationOptions(
  value: PublicKeyCredentialCreationOptionsJSON | string,
): PublicKeyCredentialCreationOptions {
  return creationOptionsFromJson(typeof value === "string" ? parseJsonObject(value) : value);
}

function parseRequestOptions(
  value: PublicKeyCredentialRequestOptionsJSON | string,
): PublicKeyCredentialRequestOptions {
  return requestOptionsFromJson(typeof value === "string" ? parseJsonObject(value) : value);
}

function credentialOperation(
  methodName: "create" | "get",
  options: object,
): Promise<PublicKeyCredential> {
  const container = credentialsContainer();
  if (!container) return unsupportedPromise("WebAuthn is not supported in this environment");

  const method = container[methodName];
  if (typeof method !== "function") {
    return unsupportedPromise(`navigator.credentials.${methodName} is not supported`);
  }

  return Promise.resolve(
    method.call(container, options as unknown as globalThis.CredentialCreationOptions),
  ).then((credential) => {
    if (!credential) {
      throw new Error(`navigator.credentials.${methodName} returned no credential`);
    }
    return credential as PublicKeyCredential;
  });
}

function booleanCapability(name: string): Promise<boolean> {
  const api = publicKeyCredentialApi();
  const method = api?.[name];
  if (typeof method !== "function") return Promise.resolve(false);
  return Promise.resolve(method.call(api)).then((value) => value === true);
}

function clientCapabilitiesValue(): Promise<unknown> {
  const api = publicKeyCredentialApi();
  const method = api?.["getClientCapabilities"];
  if (typeof method !== "function") return Promise.resolve({});
  return Promise.resolve(method.call(api));
}

function callStaticPromise(name: string, options: unknown): Promise<void> {
  const api = publicKeyCredentialApi();
  if (!api) return unsupportedPromise("WebAuthn is not supported in this environment");

  const method = api[name];
  if (typeof method !== "function") {
    return unsupportedPromise(`PublicKeyCredential.${name} is not supported`);
  }
  return Promise.resolve(method.call(api, options)).then(() => undefined);
}

function credentialsContainer(): Record<string, unknown> | undefined {
  const navigatorValue = globalThis.navigator;
  const credentials = navigatorValue?.credentials;
  return credentials && typeof credentials === "object"
    ? credentials as unknown as Record<string, unknown>
    : undefined;
}

function publicKeyCredentialApi(): Record<string, unknown> | undefined {
  const api = globalThis.PublicKeyCredential;
  return api && typeof api === "function"
    ? api as unknown as Record<string, unknown>
    : undefined;
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

function attemptPromise<T>(operation: () => Promise<T>): Promise<T> {
  try {
    return operation();
  } catch (error) {
    return Promise.reject(error);
  }
}

function unsupportedPromise<T>(message: string): Promise<T> {
  return Promise.reject(new Error(message));
}

export const WebAuthn = {
  isSupported,
  isAvailable,
  isUserVerifyingPlatformAuthenticatorAvailable,
  isUserVerifyingPlatformAuthenticatorAvailablePromise,
  isConditionalMediationAvailable,
  isConditionalMediationAvailablePromise,
  clientCapabilities,
  clientCapabilitiesPromise,
  register,
  registerJson,
  registerPromise,
  registerJsonPromise,
  authenticate,
  authenticateJson,
  authenticatePromise,
  authenticateJsonPromise,
  signalUnknownCredential,
  signalUnknownCredentialPromise,
  signalAllAcceptedCredentials,
  signalAllAcceptedCredentialsPromise,
  signalCurrentUserDetails,
  signalCurrentUserDetailsPromise,
} as const;
