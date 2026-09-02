package jfx.webauthn

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

object WebAuthn {

  private given ExecutionContext = ExecutionContext.global

  /** Whether the browser exposes both PublicKeyCredential and navigator.credentials. */
  def isSupported: Boolean =
    publicKeyCredentialApi.nonEmpty && credentialsContainer.nonEmpty

  /** Whether WebAuthn can run in the current secure browser context. */
  def isAvailable: Boolean =
    isSupported && {
      val secure = globalObject.selectDynamic("isSecureContext")
      js.isUndefined(secure) || secure.asInstanceOf[Boolean]
    }

  def isUserVerifyingPlatformAuthenticatorAvailable(): Future[Boolean] =
    isUserVerifyingPlatformAuthenticatorAvailablePromise().toFuture

  def isUserVerifyingPlatformAuthenticatorAvailablePromise(): js.Promise[Boolean] =
    booleanCapability("isUserVerifyingPlatformAuthenticatorAvailable")

  def isConditionalMediationAvailable(): Future[Boolean] =
    isConditionalMediationAvailablePromise().toFuture

  def isConditionalMediationAvailablePromise(): js.Promise[Boolean] =
    booleanCapability("isConditionalMediationAvailable")

  /** Returns all capabilities disclosed by the browser. Missing capabilities are not inferred. */
  def clientCapabilities(): Future[Map[String, Boolean]] =
    clientCapabilitiesPromise().toFuture.map(_.toMap)

  def clientCapabilitiesPromise(): js.Promise[js.Dictionary[Boolean]] =
    clientCapabilitiesValue().`then`[js.Dictionary[Boolean]]((value: js.Any) => {
      val source = value.asInstanceOf[js.Dynamic]
      val result = js.Dictionary.empty[Boolean]
      js.Object.keys(value.asInstanceOf[js.Object]).foreach { key =>
        val capability = source.selectDynamic(key)
        if (js.typeOf(capability) == "boolean")
          result.update(key, capability.asInstanceOf[Boolean])
      }
      result
    })

  def register(options: js.Dynamic): Future[RegistrationCredential] =
    registerPromise(options).toFuture

  def register(
      options: PublicKeyCredentialCreationOptions,
      settings: CredentialCreationSettings = CredentialCreationSettings()
  ): Future[RegistrationCredential] =
    registerPromise(options, settings).toFuture

  def registerJson(
      options: js.Dynamic,
      settings: CredentialCreationSettings = CredentialCreationSettings()
  ): Future[RegistrationCredential] =
    registerJsonPromise(options, settings).toFuture

  def registerJson(
      json: String,
      settings: CredentialCreationSettings
  ): Future[RegistrationCredential] =
    registerJsonPromise(json, settings).toFuture

  def registerJson(json: String): Future[RegistrationCredential] =
    registerJson(json, CredentialCreationSettings())

  def authenticate(options: js.Dynamic): Future[AuthenticationCredential] =
    authenticatePromise(options).toFuture

  def authenticate(
      options: PublicKeyCredentialRequestOptions,
      settings: CredentialRequestSettings = CredentialRequestSettings()
  ): Future[AuthenticationCredential] =
    authenticatePromise(options, settings).toFuture

  def authenticateJson(
      options: js.Dynamic,
      settings: CredentialRequestSettings = CredentialRequestSettings()
  ): Future[AuthenticationCredential] =
    authenticateJsonPromise(options, settings).toFuture

  def authenticateJson(
      json: String,
      settings: CredentialRequestSettings
  ): Future[AuthenticationCredential] =
    authenticateJsonPromise(json, settings).toFuture

  def authenticateJson(json: String): Future[AuthenticationCredential] =
    authenticateJson(json, CredentialRequestSettings())

  def registerPromise(options: js.Dynamic): js.Promise[RegistrationCredential] =
    registerJsonPromise(options)

  def registerJsonPromise(options: js.Dynamic): js.Promise[RegistrationCredential] =
    registerJsonPromise(options, CredentialCreationSettings())

  def registerJsonPromise(
      options: js.Dynamic,
      settings: CredentialCreationSettings
  ): js.Promise[RegistrationCredential] =
    attemptPromise(registerPromise(WebAuthnCodecs.creationOptionsFromJson(options), settings))

  def registerJsonPromise(json: String): js.Promise[RegistrationCredential] =
    registerJsonPromise(json, CredentialCreationSettings())

  def registerJsonPromise(
      json: String,
      settings: CredentialCreationSettings
  ): js.Promise[RegistrationCredential] =
    attemptPromise(registerPromise(WebAuthnCodecs.creationOptionsFromJson(json), settings))

  def registerPromise(
      options: PublicKeyCredentialCreationOptions,
      settings: CredentialCreationSettings = CredentialCreationSettings()
  ): js.Promise[RegistrationCredential] =
    credentialOperation("create", CredentialCreationOptions.withSettings(options, settings))
      .`then`[RegistrationCredential]((credential: PublicKeyCredential) =>
        WebAuthnCodecs.registrationCredential(credential)
      )

  def authenticatePromise(options: js.Dynamic): js.Promise[AuthenticationCredential] =
    authenticateJsonPromise(options)

  def authenticateJsonPromise(options: js.Dynamic): js.Promise[AuthenticationCredential] =
    authenticateJsonPromise(options, CredentialRequestSettings())

  def authenticateJsonPromise(
      options: js.Dynamic,
      settings: CredentialRequestSettings
  ): js.Promise[AuthenticationCredential] =
    attemptPromise(authenticatePromise(WebAuthnCodecs.requestOptionsFromJson(options), settings))

  def authenticateJsonPromise(json: String): js.Promise[AuthenticationCredential] =
    authenticateJsonPromise(json, CredentialRequestSettings())

  def authenticateJsonPromise(
      json: String,
      settings: CredentialRequestSettings
  ): js.Promise[AuthenticationCredential] =
    attemptPromise(authenticatePromise(WebAuthnCodecs.requestOptionsFromJson(json), settings))

  def authenticatePromise(
      options: PublicKeyCredentialRequestOptions,
      settings: CredentialRequestSettings = CredentialRequestSettings()
  ): js.Promise[AuthenticationCredential] =
    credentialOperation("get", CredentialRequestOptions.withSettings(options, settings))
      .`then`[AuthenticationCredential]((credential: PublicKeyCredential) =>
        WebAuthnCodecs.authenticationCredential(credential)
      )

  def signalUnknownCredential(options: UnknownCredentialOptions): Future[Unit] =
    signalUnknownCredentialPromise(options).toFuture.map(_ => ())

  def signalUnknownCredentialPromise(options: UnknownCredentialOptions): js.Promise[Unit] =
    callStaticPromise("signalUnknownCredential", js.Array(options.toJsObject)).`then`[Unit](_ => ())

  def signalAllAcceptedCredentials(options: AllAcceptedCredentialsOptions): Future[Unit] =
    signalAllAcceptedCredentialsPromise(options).toFuture.map(_ => ())

  def signalAllAcceptedCredentialsPromise(
      options: AllAcceptedCredentialsOptions
  ): js.Promise[Unit] =
    callStaticPromise("signalAllAcceptedCredentials", js.Array(options.toJsObject)).`then`[Unit](
      _ => ()
    )

  def signalCurrentUserDetails(options: CurrentUserDetailsOptions): Future[Unit] =
    signalCurrentUserDetailsPromise(options).toFuture.map(_ => ())

  def signalCurrentUserDetailsPromise(options: CurrentUserDetailsOptions): js.Promise[Unit] =
    callStaticPromise("signalCurrentUserDetails", js.Array(options.toJsObject)).`then`[Unit](_ =>
      ()
    )

  private def credentialOperation(
      methodName: String,
      options: js.Object
  ): js.Promise[PublicKeyCredential] =
    credentialsContainer match {
      case None            => unsupportedPromise("WebAuthn is not supported in this environment")
      case Some(container) =>
        val method = container.selectDynamic(methodName)
        if (js.typeOf(method) != "function")
          unsupportedPromise(s"navigator.credentials.$methodName is not supported")
        else
          method
            .call(container, options)
            .asInstanceOf[js.Promise[PublicKeyCredential | Null]]
            .`then`[PublicKeyCredential] { credential =>
              Option(credential).getOrElse {
                throw new IllegalStateException(
                  s"navigator.credentials.$methodName returned no credential"
                )
              }
            }
    }

  private def booleanCapability(name: String): js.Promise[Boolean] =
    publicKeyCredentialApi match {
      case None      => js.Promise.resolve(false)
      case Some(api) =>
        val method = api.selectDynamic(name)
        if (js.typeOf(method) != "function") js.Promise.resolve(false)
        else method.call(api).asInstanceOf[js.Promise[Boolean]]
    }

  private def clientCapabilitiesValue(): js.Promise[js.Any] =
    publicKeyCredentialApi match {
      case None      => js.Promise.resolve(js.Dynamic.literal())
      case Some(api) =>
        val method = api.selectDynamic("getClientCapabilities")
        if (js.typeOf(method) != "function") js.Promise.resolve(js.Dynamic.literal())
        else method.call(api).asInstanceOf[js.Promise[js.Any]]
    }

  private def callStaticPromise(
      name: String,
      arguments: js.Array[js.Any]
  ): js.Promise[js.Any] =
    publicKeyCredentialApi match {
      case None      => unsupportedPromise("WebAuthn is not supported in this environment")
      case Some(api) =>
        val method = api.selectDynamic(name)
        if (js.typeOf(method) != "function")
          unsupportedPromise(s"PublicKeyCredential.$name is not supported")
        else method.apply(api, arguments).asInstanceOf[js.Promise[js.Any]]
    }

  private def credentialsContainer: Option[js.Dynamic] = {
    val navigator = globalObject.selectDynamic("navigator")
    if (navigator == null || js.isUndefined(navigator)) None
    else {
      val credentials = navigator.selectDynamic("credentials")
      if (credentials == null || js.isUndefined(credentials)) None
      else Some(credentials.asInstanceOf[js.Dynamic])
    }
  }

  private def publicKeyCredentialApi: Option[js.Dynamic] = {
    val api = globalObject.selectDynamic("PublicKeyCredential")
    if (api == null || js.isUndefined(api)) None else Some(api.asInstanceOf[js.Dynamic])
  }

  private def globalObject: js.Dynamic =
    js.Dynamic.global.selectDynamic("globalThis").asInstanceOf[js.Dynamic]

  private def unsupportedPromise[A](message: String): js.Promise[A] =
    js.Promise.reject(new UnsupportedOperationException(message))

  private def attemptPromise[A](operation: => js.Promise[A]): js.Promise[A] =
    try operation
    catch {
      case error: Throwable => js.Promise.reject(error)
    }
}
