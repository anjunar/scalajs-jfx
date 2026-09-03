package jfx.router

import jfx.core.component.Runtime
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

class NestedRouteSpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "Router" should "render matched parent and child routes through an outlet" in {
    val loaded = ArrayBuffer.empty[(String, RouteContext)]
    val routes = nestedRoutes(
      parent = context => {
        loaded += (("parent", context))
        Future.successful(Route.component {
          div {
            text("team-layout") {}
            Router.routerOutlet()
          }
        })
      },
      child = context => {
        loaded += (("child", context))
        Future.successful(Route.component {
          text("post-page") {}
        })
      }
    )

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new Router(routes, "/teams/core/posts/nested-routing"), cursor)
    }

    html should include("team-layout")
    html should include("post-page")
    loaded.map(_._1).toSeq shouldBe Seq("parent", "child")

    val parentContext = loaded.head._2
    parentContext.fullPath shouldBe "/teams/:team"
    parentContext.pathParams shouldBe Map("team" -> "core")

    val childContext = loaded.last._2
    childContext.fullPath shouldBe "/teams/:team/posts/:slug"
    childContext.pathParams shouldBe Map(
      "team" -> "core",
      "slug" -> "nested-routing"
    )
  }

  it should "load no child when the parent renders no outlet" in {
    var childLoads = 0
    val routes     = nestedRoutes(
      parent = _ =>
        Future.successful(Route.component {
          text("parent-only") {}
        }),
      child = _ => {
        childLoads += 1
        Future.successful(Route.component {
          text("unreachable-child") {}
        })
      }
    )

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new Router(routes, "/teams/core/posts/nested-routing"), cursor)
    }

    html should include("parent-only")
    html should not include "unreachable-child"
    childLoads shouldBe 0
  }

  it should "retain parameters on every match in the route chain" in {
    val matches =
      RouteMatcher.resolve(
        nestedRoutes(
          parent = _ => Future.successful(Route.component {}),
          child = _ => Future.successful(Route.component {})
        ),
        "/teams/core/posts/nested-routing"
      )

    matches should have size 2
    matches.head.params shouldBe Map("team" -> "core")
    matches.last.params shouldBe Map(
      "team" -> "core",
      "slug" -> "nested-routing"
    )
  }

  private def nestedRoutes(
      parent: RouteContext => Future[jfx.core.component.AbstractComponent],
      child: RouteContext => Future[jfx.core.component.AbstractComponent]
  ): Seq[Route] =
    Seq(
      Route.view(
        "/teams/:team",
        children = Seq(Route.view("posts/:slug")(child))
      )(parent)
    )
}

class NestedRouteAsyncSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.parasitic

  "Asynchronous nested routes" should "join the SSR render before HTML is collected" in {
    val child  = Promise[jfx.core.component.AbstractComponent]()
    val routes =
      Seq(
        Route.view(
          "/parent",
          children = Seq(Route.view("child")(_ => child.future))
        ) { _ =>
          Future.successful(Route.component {
            text("parent") {}
            Router.routerOutlet()
          })
        }
      )

    val rendered = Runtime.renderToStringAsync { cursor =>
      Runtime.mount(new Router(routes, "/parent/child"), cursor)
    }

    child.success(Route.component { text("async-child") {} })

    rendered.map { html =>
      html should include("parent")
      html should include("async-child")
      html should not include "Loading..."
    }
  }
}
