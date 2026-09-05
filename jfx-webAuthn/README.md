# scalajs-jfx-webauthn

The browser-side WebAuthn and passkey API for Scala.js. It decodes relying-party JSON options, starts registration or authentication in the browser, and returns JSON-safe credential payloads for backend verification.

## Overview

This module is independent of the JFX component runtime. It handles the browser ceremony boundary only. Challenge creation and single-use rules, origin and RP-ID validation, attestation policy, signature verification, sign-count handling, credential storage, and session establishment remain backend responsibilities.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-webauthn" % "3.0.0-SNAPSHOT"
```

## Quick start

```scala
import jfx.webauthn.WebAuthn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

val optionsFromServer: js.Dynamic = ???

WebAuthn.register(optionsFromServer).map { credential =>
  val payload = credential.toJson
  // POST payload to the backend for verification.
}
```

## Authentication and capabilities

Use `authenticate` for decoded browser dictionaries or `registerJson` / `authenticateJson` for JSON returned by a backend. Every Future-based operation has a Promise counterpart. `isSupported` checks for the browser APIs; `isAvailable` also requires a secure context.

```scala
WebAuthn.isConditionalMediationAvailable().flatMap {
  case true => WebAuthn.authenticateJson(optionsFromServer)
  case false => WebAuthn.authenticate(optionsFromServer)
}
```

The module exposes capability queries, credential synchronization signals, typed public-key option facades, credential settings for abort signals and mediation, and `Base64Url` for application-specific binary payloads.

## Non-browser behavior and security

Capability checks are safe to inspect outside a browser and report unavailable features. Ceremony calls fail asynchronously when the browser API is absent. Use HTTPS or another secure context in production. The module does not verify credentials; the backend must treat all browser output as untrusted input.

## API overview

- `WebAuthn.register`, `registerJson`, `authenticate`, `authenticateJson`
- `WebAuthn.isSupported`, `isAvailable`, and capability queries
- `WebAuthn.signalUnknownCredential`, `signalAllAcceptedCredentials`, `signalCurrentUserDetails`
- `CredentialCreationSettings`, `CredentialRequestSettings`
- `PublicKeyCredentialCreationOptions`, `PublicKeyCredentialRequestOptions`
- `Base64Url`, `RegistrationCredential`, `AuthenticationCredential`
