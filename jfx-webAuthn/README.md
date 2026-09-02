# scalajs-jfx-webauthn

`scalajs-jfx-webauthn` is the browser-side WebAuthn and passkey API for Scala.js. It
converts the JSON options returned by a relying-party backend, starts registration or
authentication in the browser, and returns a JSON-safe payload for backend verification.

The module is independent of `scalajs-jfx-core` and can be used on its own.

## Installation

```scala
libraryDependencies += "com.anjunar" %%% "scalajs-jfx-webauthn" % "1.0.0"
```

## Registration

The simplest path accepts the standard JSON object returned by the backend:

```scala
import jfx.webauthn.WebAuthn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

val optionsFromServer: js.Dynamic = ???

WebAuthn.register(optionsFromServer).map { credential =>
  val jsonPayload: js.Object = credential.toJsObject
  val jsonText: String       = credential.toJson
  // POST either representation to the backend for verification.
}
```

String JSON and explicit ceremony settings are available when cancellation or conditional
registration is needed:

```scala
import jfx.webauthn.*
import org.scalajs.dom.AbortController

val abortController = new AbortController()

WebAuthn.registerJson(
  jsonFromServer,
  CredentialCreationSettings(
    signal = Some(abortController.signal),
    mediation = Some(CredentialMediationRequirement.Conditional)
  )
)
```

## Authentication and conditional UI

```scala
import jfx.webauthn.*

WebAuthn.isConditionalMediationAvailable().flatMap {
  case true =>
    WebAuthn.authenticateJson(
      optionsFromServer,
      CredentialRequestSettings(
        mediation = Some(CredentialMediationRequirement.Conditional)
      )
    )
  case false =>
    WebAuthn.authenticate(optionsFromServer)
}
```

For conditional UI, the relevant username input also needs
`autocomplete="username webauthn"`.

Every `Future` operation has a `Promise` counterpart, such as `registerPromise` and
`authenticatePromise`. Already decoded browser dictionaries can be built with
`PublicKeyCredentialCreationOptions` and `PublicKeyCredentialRequestOptions` and passed
directly.

## Capabilities

```scala
if (WebAuthn.isAvailable) {
  for {
    platform <- WebAuthn.isUserVerifyingPlatformAuthenticatorAvailable()
    conditional <- WebAuthn.isConditionalMediationAvailable()
    capabilities <- WebAuthn.clientCapabilities()
  } yield {
    println(platform)
    println(conditional)
    println(capabilities.get(WebAuthnClientCapability.HybridTransport))
  }
}
```

`isSupported` checks for the APIs, while `isAvailable` additionally checks the secure-context
requirement. Capability queries return `false` or an empty map on older browsers that do not
expose the corresponding query method.

## Credential synchronization

WebAuthn Level 3 can synchronize relying-party credential changes with authenticators:

```scala
WebAuthn.signalUnknownCredential(
  UnknownCredentialOptions(
    rpId = "example.com",
    credentialId = deletedCredentialId
  )
)

// Only use the full accepted-credential list for an authenticated user.
WebAuthn.signalAllAcceptedCredentials(
  AllAcceptedCredentialsOptions(
    rpId = "example.com",
    userId = base64UrlUserId,
    allAcceptedCredentialIds = activeCredentialIds
  )
)
```

The signal methods fail with `UnsupportedOperationException` when unavailable. Call
`clientCapabilities()` before using them when graceful degradation is required.

## JSON and Base64URL behavior

On current browsers, the module delegates option parsing to
`PublicKeyCredential.parseCreationOptionsFromJSON` and
`PublicKeyCredential.parseRequestOptionsFromJSON`. This includes extension-specific binary
fields. A strict fallback covers the Level 2 fields on older browsers.

Credential output uses native `PublicKeyCredential.toJSON()` when present. The fallback
serializes all standard registration and authentication fields and recursively converts binary
extension outputs to base64url. `Base64Url` is also public for application-specific payloads.

## Security boundary

This module only handles the browser side of a WebAuthn ceremony. Challenge generation and
single use, origin and RP-ID validation, attestation policy, signature verification, sign-count
handling, credential storage, and session establishment belong on the backend.
