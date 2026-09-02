package jfx.webauthn

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

object WebAuthnCodecs {

  /** Converts the server's JSON representation into browser-ready creation options. Modern browsers
    * use the native WebAuthn Level 3 parser, including extension-specific buffer fields.
    */
  def creationOptionsFromJson(value: js.Dynamic): PublicKeyCredentialCreationOptions =
    nativeParser("parseCreationOptionsFromJSON")
      .map(_(value).asInstanceOf[PublicKeyCredentialCreationOptions])
      .getOrElse(creationOptionsFallback(value))

  def creationOptionsFromJson(json: String): PublicKeyCredentialCreationOptions =
    creationOptionsFromJson(parseJsonObject(json))

  /** Converts the server's JSON representation into browser-ready request options. Modern browsers
    * use the native WebAuthn Level 3 parser, including extension-specific buffer fields.
    */
  def requestOptionsFromJson(value: js.Dynamic): PublicKeyCredentialRequestOptions =
    nativeParser("parseRequestOptionsFromJSON")
      .map(_(value).asInstanceOf[PublicKeyCredentialRequestOptions])
      .getOrElse(requestOptionsFallback(value))

  def requestOptionsFromJson(json: String): PublicKeyCredentialRequestOptions =
    requestOptionsFromJson(parseJsonObject(json))

  /** Returns the standards-compatible JSON payload for a registration credential. */
  def registrationCredential(credential: PublicKeyCredential): RegistrationCredential = {
    val response       = credential.response.asInstanceOf[AuthenticatorAttestationResponse]
    val native         = nativeCredentialJson(credential)
    val nativeResponse = native.map(requiredObject(_, "response"))

    RegistrationCredential(
      id = native.map(requiredString(_, "id")).getOrElse(credential.id),
      rawId = native.map(requiredString(_, "rawId")).getOrElse(Base64Url.encode(credential.rawId)),
      response = RegistrationResponse(
        clientDataJSON = nativeResponse
          .map(requiredString(_, "clientDataJSON"))
          .getOrElse(Base64Url.encode(response.clientDataJSON)),
        attestationObject = nativeResponse
          .map(requiredString(_, "attestationObject"))
          .getOrElse(Base64Url.encode(response.attestationObject)),
        transports = nativeResponse
          .map(stringArray(_, "transports").toSeq)
          .getOrElse(response.getTransports.toOption.map(_().toSeq).getOrElse(Seq.empty)),
        authenticatorData = nativeResponse
          .flatMap(optionString(_, "authenticatorData"))
          .orElse(response.getAuthenticatorData.toOption.map(method => Base64Url.encode(method()))),
        publicKey = nativeResponse
          .flatMap(optionString(_, "publicKey"))
          .orElse(
            response.getPublicKey.toOption
              .flatMap(method => Option(method()))
              .map(Base64Url.encode)
          ),
        publicKeyAlgorithm = nativeResponse
          .flatMap(optionInt(_, "publicKeyAlgorithm"))
          .orElse(response.getPublicKeyAlgorithm.toOption.map(_()))
      ),
      authenticatorAttachment = native
        .flatMap(optionString(_, "authenticatorAttachment"))
        .orElse(Option(credential.authenticatorAttachment)),
      clientExtensionResults = native
        .flatMap(optionObject(_, "clientExtensionResults"))
        .getOrElse(jsonSafeObject(credential.getClientExtensionResults())),
      credentialType = native
        .flatMap(optionString(_, "type"))
        .getOrElse(credential.credentialType)
    )
  }

  /** Returns the standards-compatible JSON payload for an authentication credential. */
  def authenticationCredential(credential: PublicKeyCredential): AuthenticationCredential = {
    val response       = credential.response.asInstanceOf[AuthenticatorAssertionResponse]
    val native         = nativeCredentialJson(credential)
    val nativeResponse = native.map(requiredObject(_, "response"))

    AuthenticationCredential(
      id = native.map(requiredString(_, "id")).getOrElse(credential.id),
      rawId = native.map(requiredString(_, "rawId")).getOrElse(Base64Url.encode(credential.rawId)),
      response = AuthenticationResponse(
        clientDataJSON = nativeResponse
          .map(requiredString(_, "clientDataJSON"))
          .getOrElse(Base64Url.encode(response.clientDataJSON)),
        authenticatorData = nativeResponse
          .map(requiredString(_, "authenticatorData"))
          .getOrElse(Base64Url.encode(response.authenticatorData)),
        signature = nativeResponse
          .map(requiredString(_, "signature"))
          .getOrElse(Base64Url.encode(response.signature)),
        userHandle = nativeResponse
          .flatMap(optionString(_, "userHandle"))
          .orElse(Option(response.userHandle).map(Base64Url.encode))
      ),
      authenticatorAttachment = native
        .flatMap(optionString(_, "authenticatorAttachment"))
        .orElse(Option(credential.authenticatorAttachment)),
      clientExtensionResults = native
        .flatMap(optionObject(_, "clientExtensionResults"))
        .getOrElse(jsonSafeObject(credential.getClientExtensionResults())),
      credentialType = native
        .flatMap(optionString(_, "type"))
        .getOrElse(credential.credentialType)
    )
  }

  /** Uses native `PublicKeyCredential.toJSON()` when available, with a Level 2 fallback. */
  def credentialToJson(credential: PublicKeyCredential): js.Object =
    nativeCredentialJson(credential).map(_.asInstanceOf[js.Object]).getOrElse {
      val response = credential.response.asInstanceOf[js.Dynamic]
      if (!js.isUndefined(response.selectDynamic("attestationObject")))
        registrationCredential(credential).toJsObject
      else
        authenticationCredential(credential).toJsObject
    }

  private def creationOptionsFallback(value: js.Dynamic): PublicKeyCredentialCreationOptions = {
    val root      = requireObject(value, "creation options")
    val rp        = requiredObject(root, "rp")
    val user      = requiredObject(root, "user")
    val selection = optionObject(root, "authenticatorSelection").map { raw =>
      val selectionObject = raw.asInstanceOf[js.Dynamic]
      AuthenticatorSelectionCriteria(
        authenticatorAttachment = optionString(selectionObject, "authenticatorAttachment"),
        residentKey = optionString(selectionObject, "residentKey"),
        requireResidentKey = optionBoolean(selectionObject, "requireResidentKey"),
        userVerification = optionString(selectionObject, "userVerification")
      )
    }

    PublicKeyCredentialCreationOptions(
      rp = PublicKeyCredentialRpEntity(
        name = requiredString(rp, "name"),
        id = optionString(rp, "id")
      ),
      user = PublicKeyCredentialUserEntity(
        id = decodeBase64Field(user, "id"),
        name = requiredString(user, "name"),
        displayName = requiredString(user, "displayName")
      ),
      challenge = decodeBase64Field(root, "challenge"),
      pubKeyCredParams = objectArray(root, "pubKeyCredParams", required = true).map { item =>
        PublicKeyCredentialParameters(
          alg = requiredInt(item, "alg"),
          credentialType = optionString(item, "type").getOrElse(CredentialType.PublicKey)
        )
      }.toSeq,
      timeout = optionNonNegativeDouble(root, "timeout"),
      attestation = optionString(root, "attestation"),
      excludeCredentials = objectArray(root, "excludeCredentials").map(descriptorFromJson).toSeq,
      authenticatorSelection = selection,
      hints = stringArray(root, "hints").toSeq,
      extensions = optionObject(root, "extensions"),
      attestationFormats = stringArray(root, "attestationFormats").toSeq
    )
  }

  private def requestOptionsFallback(value: js.Dynamic): PublicKeyCredentialRequestOptions = {
    val root = requireObject(value, "request options")
    PublicKeyCredentialRequestOptions(
      challenge = decodeBase64Field(root, "challenge"),
      timeout = optionNonNegativeDouble(root, "timeout"),
      rpId = optionString(root, "rpId"),
      allowCredentials = objectArray(root, "allowCredentials").map(descriptorFromJson).toSeq,
      userVerification = optionString(root, "userVerification"),
      hints = stringArray(root, "hints").toSeq,
      extensions = optionObject(root, "extensions")
    )
  }

  private def descriptorFromJson(value: js.Dynamic): PublicKeyCredentialDescriptor =
    PublicKeyCredentialDescriptor(
      id = decodeBase64Field(value, "id"),
      credentialType = optionString(value, "type").getOrElse(CredentialType.PublicKey),
      transports = stringArray(value, "transports").toSeq
    )

  private def nativeParser(name: String): Option[js.Function1[js.Any, js.Any]] =
    publicKeyCredentialApi.flatMap { api =>
      val method = api.selectDynamic(name)
      if (js.typeOf(method) == "function")
        Some(
          ((value: js.Any) => method.call(api, value)).asInstanceOf[js.Function1[js.Any, js.Any]]
        )
      else None
    }

  private def nativeCredentialJson(credential: PublicKeyCredential): Option[js.Dynamic] =
    credential.toJSON.toOption.map(method => method.call(credential).asInstanceOf[js.Dynamic])

  private def publicKeyCredentialApi: Option[js.Dynamic] = {
    val api = globalObject.selectDynamic("PublicKeyCredential")
    if (js.isUndefined(api) || api == null) None else Some(api.asInstanceOf[js.Dynamic])
  }

  private def globalObject: js.Dynamic =
    js.Dynamic.global.selectDynamic("globalThis").asInstanceOf[js.Dynamic]

  private def parseJsonObject(json: String): js.Dynamic =
    try requireObject(js.JSON.parse(json), "JSON root")
    catch {
      case error: IllegalArgumentException => throw error
      case error: Throwable => throw new IllegalArgumentException("Invalid JSON", error)
    }

  private def decodeBase64Field(value: js.Dynamic, field: String): ArrayBuffer =
    try Base64Url.decode(requiredString(value, field))
    catch {
      case error: IllegalArgumentException =>
        throw new IllegalArgumentException(
          s"Invalid WebAuthn field '$field': base64url expected",
          error
        )
    }

  private def requiredString(value: js.Dynamic, field: String): String =
    option(value, field) match {
      case Some(raw) if js.typeOf(raw) == "string" => raw.asInstanceOf[String]
      case Some(_)                                 => throw invalidField(field, "string expected")
      case None                                    => throw missingField(field)
    }

  private def requiredInt(value: js.Dynamic, field: String): Int =
    option(value, field) match {
      case Some(raw) if js.typeOf(raw) == "number" =>
        val number = raw.asInstanceOf[Double]
        if (number.isNaN || number.isInfinity || number != number.toInt.toDouble)
          throw invalidField(field, "integer expected")
        number.toInt
      case Some(_) => throw invalidField(field, "integer expected")
      case None    => throw missingField(field)
    }

  private def optionInt(value: js.Dynamic, field: String): Option[Int] =
    option(value, field).map { _ => requiredInt(value, field) }

  private def optionString(value: js.Dynamic, field: String): Option[String] =
    option(value, field).map {
      case raw if js.typeOf(raw) == "string" => raw.asInstanceOf[String]
      case _                                 => throw invalidField(field, "string expected")
    }

  private def optionBoolean(value: js.Dynamic, field: String): Option[Boolean] =
    option(value, field).map {
      case raw if js.typeOf(raw) == "boolean" => raw.asInstanceOf[Boolean]
      case _                                  => throw invalidField(field, "boolean expected")
    }

  private def optionNonNegativeDouble(value: js.Dynamic, field: String): Option[Double] =
    option(value, field).map {
      case raw if js.typeOf(raw) == "number" =>
        val number = raw.asInstanceOf[Double]
        if (
          number.isNaN || number.isInfinity || number < 0 || number > 4294967295d ||
          number % 1 != 0
        )
          throw invalidField(field, "unsigned 32-bit integer expected")
        number
      case _ => throw invalidField(field, "unsigned 32-bit integer expected")
    }

  private def requiredObject(value: js.Dynamic, field: String): js.Dynamic =
    option(value, field) match {
      case Some(raw) => requireObject(raw, field)
      case None      => throw missingField(field)
    }

  private def optionObject(value: js.Dynamic, field: String): Option[js.Object] =
    option(value, field).map(raw => requireObject(raw, field).asInstanceOf[js.Object])

  private def objectArray(
      value: js.Dynamic,
      field: String,
      required: Boolean = false
  ): js.Array[js.Dynamic] =
    option(value, field) match {
      case None if required                   => throw missingField(field)
      case None                               => js.Array()
      case Some(raw) if js.Array.isArray(raw) =>
        raw.asInstanceOf[js.Array[js.Any]].map(item => requireObject(item, field))
      case Some(_) => throw invalidField(field, "array expected")
    }

  private def stringArray(value: js.Dynamic, field: String): js.Array[String] =
    option(value, field) match {
      case None                               => js.Array()
      case Some(raw) if js.Array.isArray(raw) =>
        raw.asInstanceOf[js.Array[js.Any]].map {
          case item if js.typeOf(item) == "string" => item.asInstanceOf[String]
          case _ => throw invalidField(field, "string array expected")
        }
      case Some(_) => throw invalidField(field, "array expected")
    }

  private def requireObject(value: js.Any, label: String): js.Dynamic =
    if (
      value == null || js.isUndefined(value) || js.typeOf(value) != "object" ||
      js.Array.isArray(value)
    ) throw new IllegalArgumentException(s"Invalid WebAuthn $label: object expected")
    else value.asInstanceOf[js.Dynamic]

  private def option(value: js.Dynamic, field: String): Option[js.Any] = {
    val selected = value.selectDynamic(field)
    if (selected == null || js.isUndefined(selected)) None else Some(selected.asInstanceOf[js.Any])
  }

  private def jsonSafeObject(value: js.Object): js.Object =
    jsonSafe(value).asInstanceOf[js.Object]

  private def jsonSafe(value: js.Any): js.Any = {
    if (value == null || js.isUndefined(value)) value
    else if (value.isInstanceOf[ArrayBuffer]) Base64Url.encode(value.asInstanceOf[ArrayBuffer])
    else if (
      js.Dynamic.global.ArrayBuffer
        .selectDynamic("isView")
        .asInstanceOf[js.Function1[js.Any, Boolean]](value)
    ) {
      val view   = value.asInstanceOf[js.Dynamic]
      val source = new Uint8Array(
        view.selectDynamic("buffer").asInstanceOf[ArrayBuffer],
        view.selectDynamic("byteOffset").asInstanceOf[Int],
        view.selectDynamic("byteLength").asInstanceOf[Int]
      )
      Base64Url.encode(source)
    } else if (js.Array.isArray(value)) {
      value.asInstanceOf[js.Array[js.Any]].map(jsonSafe)
    } else if (js.typeOf(value) == "object") {
      val source = value.asInstanceOf[js.Dynamic]
      val result = js.Dynamic.literal()
      js.Object.keys(value.asInstanceOf[js.Object]).foreach { key =>
        result.updateDynamic(key)(jsonSafe(source.selectDynamic(key)))
      }
      result
    } else value
  }

  private def missingField(field: String): IllegalArgumentException =
    new IllegalArgumentException(s"Missing WebAuthn field '$field'")

  private def invalidField(field: String, reason: String): IllegalArgumentException =
    new IllegalArgumentException(s"Invalid WebAuthn field '$field': $reason")
}
