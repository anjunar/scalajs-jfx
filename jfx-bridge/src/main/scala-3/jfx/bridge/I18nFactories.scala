package jfx.bridge

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayer
import jfx.core.i18n.*
import jfx.core.render.Cursor

import scala.scalajs.js

/** Step 7 of JAVASCRIPT_API.md §9: the i18n facade.
  *
  * `jfx.core.i18n.i18n"..."` is a Scala-3 macro (`I18nInterpolator.scala`): it derives the message
  * source, placeholder names and a fingerprint from the AST at compile time. TypeScript has no
  * macros, so `npm/jfx-core/src/i18n.ts`'s `` i18n`...` `` tag does the same derivation at runtime
  * instead -- the same source-reconstruction and the same FNV-1a fingerprint, just computed from a
  * `TemplateStringsArray` instead of a quasiquote. Placeholder names are never inferred from the
  * substituted expression (`I18nMacros.placeholderName` reads the *identifier*, which TypeScript
  * does not preserve at runtime): every substitution must be `named("x", value)`, enforced by
  * `i18n.ts` itself.
  *
  * That symmetry is what makes this file small: a `RuntimeMessage` built in TypeScript has the
  * exact same shape as one the Scala macro builds, so [[I18nFactories.toScala]] is a straight
  * field-by-field crossing, not a re-derivation. Resolution itself -- locale fallback, catalog
  * lookup, placeholder interpolation -- stays entirely in `I18nResolver`; nothing here duplicates
  * it.
  */
@js.native
private[bridge] trait MessageSourcePositionFacade extends js.Object {
  val file: String   = js.native
  val line: Int      = js.native
  val column: Int    = js.native
}

@js.native
private[bridge] trait MessageKeyFacade extends js.Object {
  val source: String                                     = js.native
  val context: js.UndefOr[String]                        = js.native
  val fingerprint: String                                = js.native
  val placeholders: js.Array[String]                     = js.native
  val position: js.UndefOr[MessageSourcePositionFacade]  = js.native
}

@js.native
private[bridge] trait MessageArgFacade extends js.Object {
  val name: String  = js.native
  val value: js.Any = js.native
}

@js.native
private[bridge] trait RuntimeMessageFacade extends js.Object {
  val key: MessageKeyFacade            = js.native
  val args: js.Array[MessageArgFacade] = js.native
}

/** `i18n.ts`'s `CatalogEntry`: a message key plus one translation string per locale code. Native,
  * for the same reason as [[RouteFacade]] -- Scala never constructs one, only reads what
  * `i18nProvider()` handed across.
  */
@js.native
private[bridge] trait CatalogEntryFacade extends js.Object {
  val key: MessageKeyFacade               = js.native
  val translations: js.Dictionary[String] = js.native
}

private[bridge] object I18nFactories {

  def toScala(facade: MessageKeyFacade): MessageKey =
    MessageKey(
      source = facade.source,
      context = facade.context.toOption.map(MessageContext(_)),
      fingerprint = MessageFingerprint(facade.fingerprint),
      placeholders = facade.placeholders.toVector,
      position = facade.position.toOption.map(p => MessageSourcePosition(p.file, p.line, p.column))
    )

  def toScala(facade: RuntimeMessageFacade): RuntimeMessage =
    RuntimeMessage(
      key = toScala(facade.key),
      args = facade.args.toVector.map(arg => MessageArg(arg.name, arg.value))
    )

  def toScala(facade: CatalogEntryFacade): CatalogEntry =
    CatalogEntry(
      key = toScala(facade.key),
      value = MessageValue(
        translations = facade.translations.toMap.map { case (code, pattern) =>
          I18nLocale(code) -> LocalizedPattern(pattern)
        }
      )
    )

}

/** The component `i18nProvider()` mounts: it owns one [[I18nRuntime]] and puts `body` under it.
  *
  * This is what `app.App.compose` assembles by hand on the Scala side -- `I18nRuntime.managed(...)`,
  * then `I18nRuntime.provide(i18nRuntime)(using this)`. A TypeScript user gets both from one call;
  * everything nested inside `body` -- including a `router()`, which reads `I18nRuntime.current` for
  * its own locale-prefixed URLs (`jfx.router.Router.synchronizeI18n`) -- sees this runtime through
  * the ordinary component-context walk, exactly as it would on the Scala side.
  */
private[bridge] final class I18nProviderRoot(
    runtime: I18nRuntime,
    body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
) extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit = {
    I18nRuntime.provide(runtime)(using this)

    DslLayer.render(this, cursor) {
      body(new ComponentHandleBridge(this), new ScopeHandleBridge(this, cursor))
    }
  }
}

/** `i18n-provider` -- mounts an [[I18nProviderRoot]] around a runtime built from the translated
  * catalog and locale config.
  */
private[bridge] object I18nProviderFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    // `js.Dictionary.get` cannot tell "key absent" from "key present with value `undefined`"
    // apart -- both are legitimate here (TypeScript's optional `initialUrl?`/`basePath?` produce
    // the former when omitted from the object literal, the latter when spread from a variable that
    // happens to hold `undefined`). Reading through `js.UndefOr` instead of `Option` treats both
    // the same way a native facade's own `UndefOr` field would.
    def optionalString(key: String): Option[String] =
      options.getOrElse(key, js.undefined).asInstanceOf[js.UndefOr[String]].toOption

    val catalogEntries       = options("catalog").asInstanceOf[js.Array[CatalogEntryFacade]]
    val supportedLocaleCodes = options("supportedLocales").asInstanceOf[js.Array[String]]
    val defaultLocaleCode    = options("defaultLocale").asInstanceOf[String]
    val initialUrl           = optionalString("initialUrl")
    val basePath             = optionalString("basePath").getOrElse("")

    val config = I18nConfig(
      resolver = I18nResolver(MessageCatalog(catalogEntries.toSeq.map(I18nFactories.toScala)*)),
      supportedLocales = supportedLocaleCodes.toSeq.map(I18nLocale(_)),
      defaultLocale = I18nLocale(defaultLocaleCode)
    )

    // Same default as RouterFactory.mount: an explicit `initialUrl` (SSR) wins, otherwise the
    // browser's own location -- consistent so a locale segment `Router` strips or adds agrees with
    // the locale this runtime resolved its initial `locale` property from.
    val startUrl = initialUrl.getOrElse(cursor.browserUrl.getOrElse("/"))
    val runtime  = I18nRuntime.managed(config, startUrl, basePath)

    DslLayer.child(new I18nProviderRoot(runtime, body)) {}
  }
}
