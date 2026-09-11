# @anjunar/scalajs-ui-webauthn

The browser-side WebAuthn and passkey API for UI 3. It is independent of the
UI component runtime and is the TypeScript counterpart of
[`scalajs-ui-webauthn`](../../scalajs-ui-webauthn/README.md).

## Installation

```bash
npm install @anjunar/scalajs-ui-webauthn
```

## Registration and authentication

Use the JSON methods with the options returned by a relying-party backend. The
module uses the browser's Level 3 JSON parser when available and has a strict
Level 2 fallback for older browsers.

```ts
import { authenticateJson, registerJson } from "@anjunar/scalajs-ui-webauthn";

const registration = await registerJson(await fetch("/webauthn/register/options").then((r) => r.json()));
await fetch("/webauthn/register/verify", {
  method: "POST",
  body: JSON.stringify(registration),
  headers: { "content-type": "application/json" },
});

const authentication = await authenticateJson(await fetch("/webauthn/auth/options").then((r) => r.json()));
```

Already decoded browser dictionaries can be passed to `register` and
`authenticate`. `CredentialCreationSettings` and `CredentialRequestSettings`
carry an `AbortSignal` and mediation mode. All operations return native
Promises; the `*Promise` names are provided as migration-friendly aliases for
the Scala.js API.

## Capabilities and credential synchronization

```ts
import {
  CredentialMediationRequirement,
  isAvailable,
  isConditionalMediationAvailable,
  authenticateJson,
} from "@anjunar/scalajs-ui-webauthn";

if (isAvailable() && await isConditionalMediationAvailable()) {
  await authenticateJson(optionsFromServer, {
    mediation: CredentialMediationRequirement.Conditional,
  });
}
```

`clientCapabilities` and the individual capability queries safely return empty
or false outside a supporting browser. `signalUnknownCredential`,
`signalAllAcceptedCredentials`, and `signalCurrentUserDetails` reject when the
browser does not expose the corresponding WebAuthn Level 3 method.

## JSON and Base64URL behavior

Credential responses contain base64url strings suitable for sending to a
backend. `Base64Url` is public for application-specific binary fields.
`credentialToJson` uses native `PublicKeyCredential.toJSON()` where available;
the fallback recursively converts binary extension results to base64url.

WebAuthn ceremonies are only the browser boundary. Challenge generation and
single use, origin and RP-ID validation, attestation policy, signature
verification, sign-count handling, credential storage, and session
establishment remain backend responsibilities.

## API overview

- `register`, `registerJson`, `authenticate`, `authenticateJson`
- `isSupported`, `isAvailable`, and capability queries
- credential synchronization signal methods
- typed public-key option facades and WebAuthn value constants
- `Base64Url`, `WebAuthnCodecs`, `RegistrationCredential`, and `AuthenticationCredential`
