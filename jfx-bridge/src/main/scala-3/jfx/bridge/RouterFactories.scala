package jfx.bridge

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.layout.{Anchor, TextComponent}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.router.{Route, RouteContext => CoreRouteContext, RouteFailure, Router, RouterConfig, RouterLink}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Step 5 of JAVASCRIPT_API.md §9: the router facade.
  *
  * The trigger from CLAUDE_REVIEW_3.md §5 was "`jfx-bridge` gets `dependsOn(jfxRouter)` **and**
  * exports (a) a registry entry that mounts a `jfx.router.Router` with a route table translated
  * from JS, (b) `router-outlet`, (c) `router-link`". This file is those three, plus the JS <-> Scala
  * translation `jfx.router.Router` needs and the deleted `router.ts` only sketched.
  *
  * The hard part is `load`: `jfx.router.Route` takes `RouteContext => Future[AbstractComponent]`,
  * where the component is a virtual boundary the router renders. TypeScript writes a
  * `(ctx) => Promise<PageBody>` where `PageBody = () => void` runs the ambient-scope DSL. The
  * TS facade (`npm/jfx-router/src/router.ts`) already rewrites that into
  * `(ctx) => Promise<(scope) => void>` by wrapping the body in `withScope`; this side turns the
  * resolved `(scope) => void` into an `AbstractComponent` via [[Route.component]].
  */

/** The shape TypeScript hands in for one route. Native: Scala never builds one.
  *
  * `load` returns either a `ScopeBody` (`js.Function1[ScopeHandleBridge, Unit]`) for a synchronous
  * loader, or a `js.Promise` of one for an asynchronous loader -- [[RouterFactories.buildRoute]]
  * branches on `js.typeOf`. A synchronous loader takes the same one-pass path as `Future.successful`
  * on the Scala side, which is the path that hydrates cleanly.
  */
@js.native
private[bridge] trait RouteFacade extends js.Object {
  val path: String                                                          = js.native
  val load: js.Function1[RouteContextHandle, js.Any]                         = js.native
  val children: js.UndefOr[js.Array[RouteFacade]]                            = js.native
  val constraints: js.UndefOr[js.Dictionary[js.Function1[String, Boolean]]]  = js.native
  val status: js.UndefOr[Int]                                                = js.native
}

/** Mirrors `router.ts`'s `RouterConfig`. Native, same reason. */
@js.native
private[bridge] trait RouterConfigFacade extends js.Object {
  val basePath: js.UndefOr[String]                             = js.native
  val initialUrl: js.UndefOr[String]                           = js.native
  val onFailure: js.UndefOr[js.Function1[js.Object, js.Any]]   = js.native
  val renderErrorsOnServer: js.UndefOr[Boolean]                = js.native
}

/** The JS projection of `jfx.router.RouteContext`, handed to a TS route loader.
  *
  * Only the four fields the deleted `router.ts` declared: no `state`, `routeMatch` or `locale` --
  * those are Scala-internal routing types with no TypeScript meaning.
  */
private[bridge] final class RouteContextHandle(source: CoreRouteContext) extends js.Object {
  val path: String                    = source.path
  val params: js.Dictionary[String]   = source.pathParams.toJSDictionary
  val queryParams: js.Dictionary[String] =
    source.queryParams.entries.toMap.toJSDictionary
  val failure: String | Null =
    source.failure.map(RouterFactories.failureKind).orNull
}

private[bridge] object RouterFactories {

  def failureKind(failure: RouteFailure): String =
    failure match {
      case _: RouteFailure.NotMatched => "not-matched"
      case _: RouteFailure.LoadFailed => "load-failed"
    }

  def buildRoutes(defs: js.Array[RouteFacade])(using ExecutionContext): Seq[Route] =
    defs.toSeq.map(buildRoute)

  private def routeComponent(jsBody: js.Function1[ScopeHandleBridge, Unit]): AbstractComponent =
    Route.component {
      jsBody(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
    }

  private def buildRoute(facade: RouteFacade)(using ec: ExecutionContext): Route =
    Route(
      path = facade.path,
      load = context => {
        val produced = facade.load(new RouteContextHandle(context))

        // A synchronous loader stays synchronous: `Future.successful` with a settled `value`, so
        // `Router.loadRoute` renders in one pass instead of flashing the loading boundary. Only a
        // real promise goes through `.map`, which the global EC always defers.
        if (js.typeOf(produced) == "function")
          scala.concurrent.Future.successful(
            routeComponent(produced.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
          )
        else
          produced
            .asInstanceOf[js.Promise[js.Function1[ScopeHandleBridge, Unit]]]
            .toFuture
            .map(routeComponent)
      },
      constraints = facade.constraints.toOption
        .map(_.view.mapValues(fn => (value: String) => fn(value)).toMap)
        .getOrElse(Map.empty),
      children = facade.children.toOption.map(buildRoutes).getOrElse(Nil),
      status = facade.status.getOrElse(200)
    )

  def projectConfig(facade: js.UndefOr[RouterConfigFacade]): RouterConfig =
    facade.toOption match {
      case None => RouterConfig()
      case Some(config) =>
        val base = RouterConfig()

        base.copy(
          basePath = config.basePath.getOrElse(base.basePath),
          onFailure = config.onFailure.toOption match {
            case None => base.onFailure
            case Some(fn) =>
              failure => {
                val projected = js.Dynamic.literal(
                  kind = failureKind(failure),
                  path = failure.state.browserPath
                )
                val result = fn(projected.asInstanceOf[js.Object])
                if (js.isUndefined(result) || result == null) None
                else Some(result.asInstanceOf[String])
              }
          },
          renderErrorsOnServer =
            config.renderErrorsOnServer.getOrElse(base.renderErrorsOnServer)
        )
    }
}

/** The component `router()` mounts: it owns one `jfx.router.Router` and puts the application shell
  * around it.
  *
  * This is what `app.App.compose` assembles by hand on the Scala side -- `Router.provide(appRouter)`,
  * then a sidebar of `routerLink`s, then `child(appRouter)`. A Scala user writes that directly; a
  * TypeScript user goes through `router(routes, config, shell)`, so the assembly lives here. The
  * shell body runs with the router in context, so its `routerLink`s resolve; the routed page renders
  * straight after it. An empty shell (`router(routes, config)` with no third argument) just renders
  * the routed page.
  */
private[bridge] final class RouterViewRoot(
    routerComponent: Router,
    shell: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
) extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit = {
    Router.provide(routerComponent)(using this)

    DslLayer.render(this, cursor) {
      shell(new ComponentHandleBridge(this), new ScopeHandleBridge(this, cursor))
      DslLayer.child(routerComponent) {}
    }
  }
}

/** `router` -- mounts a [[RouterViewRoot]] around a `jfx.router.Router` with the translated table. */
private[bridge] object RouterFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val routes = options("routes").asInstanceOf[js.Array[RouteFacade]]
    val config = options.get("config").map(_.asInstanceOf[RouterConfigFacade]).orUndefined

    val startUrl =
      config.toOption
        .flatMap(_.initialUrl.toOption)
        .getOrElse(cursor.browserUrl.getOrElse("/"))

    val routerComponent =
      new Router(
        RouterFactories.buildRoutes(routes),
        startUrl,
        RouterFactories.projectConfig(config)
      )

    // SSR only: let the response carry an error route's own status. No-op under `mount`/`hydrate`,
    // which never open a slot.
    SsrStatus.current.foreach(_.bind(() => routerComponent.responseStatus.get))

    DslLayer.child(new RouterViewRoot(routerComponent, body)) {}
  }
}

/** `router-outlet` -- renders the next match in a nested route chain. */
private[bridge] object RouterOutletFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    Router.routerOutlet()
}

/** `router-link` -- a navigating anchor. `dsl.ts`/`router.ts` fold `{ href, label, ...options }`
  * into one options object; this factory takes it back apart, exactly like [[ButtonFactory]].
  */
private[bridge] object RouterLinkFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val href        = options.getOrElse("href", "").asInstanceOf[String]
    val activeClass = options.get("activeClass").map(_.asInstanceOf[String]).getOrElse("active")
    val label       = options.get("label")

    RouterLink.routerLink(href, activeClass) {
      val link       = summon[Anchor]
      val linkCursor  = summon[Cursor]

      // `Cursor` is already a given from the context function; only the component is missing --
      // the body runs as the anchor, so `child()` and friends mount under it.
      given AbstractComponent = link

      label.foreach { value =>
        DslLayer.child(TextComponent.bind(ReactiveBridge.asProperty[String](value))) {}
      }

      body(new ComponentHandleBridge(link), new ScopeHandleBridge(link, linkCursor))
    }
  }
}
