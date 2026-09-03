package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future, Promise}

/** The router boundary in both of its shapes.
  *
  * Without configured error routes the terminal fallback answers, which is what an application gets
  * before it declares any. With them, a failure forwards to a real route -- and the property worth
  * pinning down is that the forward leaves the request alone. An error page reached by navigation
  * would answer 200 under a different URL, which is the outcome the whole mechanism exists to
  * prevent.
  */
class RouterBoundarySpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  private def notFoundRoute: Route =
    Route.error("/404", status = 404) { context =>
      Future.successful(component(s"404 page for: ${context.browserPath}"))
    }

  private def errorRoute: Route =
    Route.error("/500", status = 500) { context =>
      Future.successful(component(s"500 page for: ${context.path}"))
    }

  private val toErrorRoutes: RouteFailure => Option[String] = {
    case _: RouteFailure.NotMatched => Some("/404")
    case _: RouteFailure.LoadFailed => Some("/500")
  }

  "Without error routes the fallback" should "answer an unmatched path with 404" in {
    val router = new Router(Nil, "/missing")

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("No route matched for: /missing")
    router.responseStatus.get shouldBe 404
  }

  it should "stay replaceable by the application" in {
    val router =
      new Router(
        Nil,
        "/missing",
        RouterConfig(fallback = failure => component(s"custom: ${failure.state.path}"))
      )

    Runtime.renderToString(cursor => Runtime.mount(router, cursor)) should
      include("custom: /missing")
  }

  "An unmatched path" should "render the configured error route without changing the request" in {
    val router =
      new Router(
        Seq(notFoundRoute),
        "/blog/typo",
        RouterConfig(onFailure = toErrorRoutes)
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    // The error page renders, but the request it describes is still the visitor's.
    html should include("404 page for: /blog/typo")
    router.state.get.path shouldBe "/blog/typo"
    router.state.get.browserPath shouldBe "/blog/typo"
    router.responseStatus.get shouldBe 404
  }

  it should "take its status from the route rather than from the router" in {
    val router =
      new Router(
        Seq(Route.error("/404", status = 410)(_ => Future.successful(component("gone")))),
        "/missing",
        RouterConfig(onFailure = toErrorRoutes)
      )

    Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    router.responseStatus.get shouldBe 410
  }

  it should "let the error route be reached directly by its own path" in {
    val router =
      new Router(Seq(notFoundRoute), "/404", RouterConfig(onFailure = toErrorRoutes))

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("404 page for: /404")
    router.responseStatus.get shouldBe 404
  }

  "A loader failure" should "keep throwing during SSR by default" in {
    val failure = new RuntimeException("database-url-internal")
    val router  =
      new Router(
        Seq(Route.view("/broken")(_ => Future.failed(failure)), errorRoute),
        "/broken",
        RouterConfig(onFailure = toErrorRoutes)
      )

    the[RuntimeException] thrownBy {
      Runtime.renderToString(cursor => Runtime.mount(router, cursor))
    } shouldBe failure
  }

  it should "forward to the error route when the application renders errors on the server" in {
    val router =
      new Router(
        Seq(
          Route.view("/broken")(_ => Future.failed(new RuntimeException("database-url-internal"))),
          errorRoute
        ),
        "/broken",
        RouterConfig(onFailure = toErrorRoutes, renderErrorsOnServer = true)
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("500 page for: /broken")
    html should not include "database-url-internal"
    router.responseStatus.get shouldBe 500
  }

  it should "reach the error page with the failure, not just the path" in {
    val cause = new RuntimeException("internal")
    var seen  = Option.empty[RouteFailure]

    val router =
      new Router(
        Seq(
          Route.view("/broken")(_ => Future.failed(cause)),
          Route.error("/500", status = 500) { context =>
            seen = context.failure
            Future.successful(component("failed"))
          }
        ),
        "/broken",
        RouterConfig(onFailure = toErrorRoutes, renderErrorsOnServer = true)
      )

    Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    seen match {
      case Some(RouteFailure.LoadFailed(error, context)) =>
        error shouldBe cause
        context.path shouldBe "/broken"

      case other =>
        fail(s"Expected a LoadFailed failure, got $other")
    }
  }

  "An error route that fails itself" should "end at the fallback instead of looping" in {
    var loaderCalls = 0

    val router =
      new Router(
        Seq(
          Route.error("/500", status = 500) { _ =>
            loaderCalls += 1
            Future.failed(new RuntimeException("the error page is broken too"))
          },
          Route.view("/broken")(_ => Future.failed(new RuntimeException("original")))
        ),
        "/broken",
        RouterConfig(onFailure = _ => Some("/500"), renderErrorsOnServer = true)
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    loaderCalls shouldBe 1
    html should include("Route could not be loaded")
    html should not include "the error page is broken too"
    router.responseStatus.get shouldBe 500
  }

  "A misconfigured boundary" should "report a path that matches no route" in {
    val router =
      new Router(Nil, "/missing", RouterConfig(onFailure = _ => Some("/404")))

    val thrown = the[IllegalStateException] thrownBy {
      Runtime.renderToString(cursor => Runtime.mount(router, cursor))
    }

    thrown.getMessage should include("/404")
    thrown.getMessage should include("no route matches")
  }

  it should "report an error route that answers 200" in {
    val router =
      new Router(
        Seq(Route.view("/404")(_ => Future.successful(component("oops")))),
        "/missing",
        RouterConfig(onFailure = _ => Some("/404"))
      )

    val thrown = the[IllegalStateException] thrownBy {
      Runtime.renderToString(cursor => Runtime.mount(router, cursor))
    }

    thrown.getMessage should include("status 200")
  }

  "Route.error" should "refuse a success status at the declaration site" in {
    an[IllegalArgumentException] should be thrownBy
      Route.error("/404", status = 200)(_ => Future.successful(component("no")))
  }

  "A loading boundary" should "stay a lambda, since it has no URL" in {
    val pending = Promise[AbstractComponent]()
    val router  =
      new Router(
        Seq(Route.view("/slow")(_ => pending.future)),
        "/slow",
        RouterConfig(loading = context => component(s"loading: ${context.path}"))
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("loading: /slow")
    router.responseStatus.get shouldBe 200
  }

  private def component(value: String): AbstractComponent =
    Route.component {
      text(value) {}
    }
}

class RouterBoundaryAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.parasitic

  "An asynchronous loader failure" should "forward to the error route and carry its status" in {
    val pending = Promise[AbstractComponent]()
    val router  =
      new Router(
        Seq(
          Route.view("/broken")(_ => pending.future),
          Route.error("/500", status = 500) { context =>
            Future.successful(component(s"async failure: ${context.path}"))
          }
        ),
        "/broken",
        RouterConfig(onFailure = _ => Some("/500"), renderErrorsOnServer = true)
      )

    val rendered =
      Runtime.renderToStringAsync(cursor => Runtime.mount(router, cursor))

    pending.failure(new RuntimeException("failed later"))

    rendered.map { html =>
      html should include("async failure: /broken")
      router.responseStatus.get shouldBe 500
    }
  }

  private def component(value: String): AbstractComponent =
    Route.component {
      text(value) {}
    }
}
