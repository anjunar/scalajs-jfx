package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future, Promise}

class RouterBoundarySpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "RouterConfig" should "render an application-provided not-found component" in {
    val router =
      new Router(
        Nil,
        "/missing",
        RouterConfig(
          notFound = state => component(s"custom 404: ${state.path}")
        )
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("custom 404: /missing")
    router.responseStatus.get shouldBe 404
  }

  it should "render an application-provided loading component" in {
    val pending = Promise[AbstractComponent]()
    val router  =
      new Router(
        Seq(Route.view("/slow")(_ => pending.future)),
        "/slow",
        RouterConfig(
          loading = context => component(s"loading: ${context.path}")
        )
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("loading: /slow")
    router.responseStatus.get shouldBe 200
  }

  it should "keep throwing loader failures during SSR by default" in {
    val failure = new RuntimeException("database-url-internal")
    val router  =
      new Router(
        Seq(Route.view("/broken")(_ => Future.failed(failure))),
        "/broken"
      )

    val thrown = the[RuntimeException] thrownBy {
      Runtime.renderToString(cursor => Runtime.mount(router, cursor))
    }

    thrown shouldBe failure
  }

  it should "render a configured SSR error boundary with status 500" in {
    val failure = new RuntimeException("database-url-internal")
    val router  =
      new Router(
        Seq(Route.view("/broken")(_ => Future.failed(failure))),
        "/broken",
        RouterConfig(
          error = (error, context) => component(s"failed: ${context.path}: ${error.getMessage}"),
          renderErrorsOnServer = true
        )
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("failed: /broken: database-url-internal")
    router.responseStatus.get shouldBe 500
  }

  it should "not expose loader details from the default error component" in {
    val router =
      new Router(
        Seq(
          Route.view("/broken")(_ => Future.failed(new RuntimeException("private backend URL")))
        ),
        "/broken",
        RouterConfig(renderErrorsOnServer = true)
      )

    val html = Runtime.renderToString(cursor => Runtime.mount(router, cursor))

    html should include("Route could not be loaded")
    html should not include "private backend URL"
  }

  private def component(value: String): AbstractComponent =
    Route.component {
      text(value) {}
    }
}

class RouterBoundaryAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.parasitic

  "An asynchronous loader failure" should "render the configured SSR boundary and status" in {
    val pending = Promise[AbstractComponent]()
    val router  =
      new Router(
        Seq(Route.view("/broken")(_ => pending.future)),
        "/broken",
        RouterConfig(
          error = (_, context) => component(s"async failure: ${context.path}"),
          renderErrorsOnServer = true
        )
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
