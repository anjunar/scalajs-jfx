package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.Runtime
import jfx.core.i18n.I18nRuntime
import jfx.core.render.SsrCursor
import jfx.core.request.RequestContext
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

/** The seam where SSR, the router, i18n, the theme and the request context meet.
  *
  * Every module below has tests; this layer had none, which is why P4-1 (hydration against async
  * loaders) and the empty-text hydration fault both got as far as they did.
  *
  * Hydration itself cannot be exercised here: `HydratingCursor` needs a real DOM, and
  * `scalajs-env-jsdom-nodejs` has no Scala 3 build, so sbt 2 cannot load it. See CHANGE.md P5-6.
  * What is covered is the server half plus the router driving the mounted tree.
  */
class AppSsrSpec extends AsyncFlatSpec with Matchers {

  // The /rendering route resolves through setTimeout. ScalaTest's serial context cannot drive a
  // macrotask, so the suite runs on the JS queue instead.
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.queue

  private def desktopRequest: RequestContext =
    RequestContext.withUserAgent(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
    )

  "SSR" should "render the start route to the same HTML twice" in {
    for {
      first <- Runtime.renderToStringAsync(cursor =>
        Runtime.mount(new App(desktopRequest, "/"), cursor)
      )
      second <- Runtime.renderToStringAsync(cursor =>
        Runtime.mount(new App(desktopRequest, "/"), cursor)
      )
    } yield {
      first should include("class=\"app-shell\"")
      first should include("Welcome to")
      second shouldBe first
    }
  }

  it should "render an asynchronous route once its loader completed" in {
    Runtime
      .renderToStringAsync(cursor => Runtime.mount(new App(desktopRequest, "/rendering"), cursor))
      .map { html =>
        html should include("class=\"app-shell\"")
        html should not include "jfx:Route:pending"
      }
  }

  it should "answer 200 for a known route and 404 for an unknown one" in {
    val known   = new App(desktopRequest, "/")
    val unknown = new App(desktopRequest, "/no-such-page")

    // The status only exists once the router composed, so the app has to be mounted first.
    Runtime.mount(known, new SsrCursor())
    Runtime.mount(unknown, new SsrCursor())

    known.ssrStatus shouldBe 200
    unknown.ssrStatus shouldBe 404
  }

  "The app" should "publish request, i18n and theme through the component context" in {
    val app = new App(desktopRequest, "/")

    Runtime.mount(app, new SsrCursor())

    RequestContext.current(using app) should not be empty
    I18nRuntime.current(using app) should not be empty
    AppTheme.current(using app) should not be empty

    AppTheme.current(using app).map(_.modeProperty.get) shouldBe Some(AppTheme.Mode.Light)
  }

  it should "give every instance its own theme, not a shared one" in {
    val first  = new App(desktopRequest, "/")
    val second = new App(desktopRequest, "/")

    Runtime.mount(first, new SsrCursor())
    Runtime.mount(second, new SsrCursor())

    val firstTheme  = AppTheme.current(using first).get
    val secondTheme = AppTheme.current(using second).get

    firstTheme should not be theSameInstanceAs(secondTheme)

    firstTheme.set(AppTheme.Mode.Dark)

    firstTheme.modeProperty.get shouldBe AppTheme.Mode.Dark
    secondTheme.modeProperty.get shouldBe AppTheme.Mode.Light
  }

  it should "carry a mobile user agent through to the request context" in {
    val mobile = RequestContext.withUserAgent(
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
    )
    val app = new App(mobile, "/")

    Runtime.mount(app, new SsrCursor())

    RequestContext.current(using app).map(_.isMobile) shouldBe Some(true)
  }

  "The router" should "change the rendered tree on navigation" in {
    val async  = new AsyncRenderContext()
    val cursor = new SsrCursor(async)
    val app    = new App(desktopRequest, "/")

    Runtime.mount(app, cursor)

    async.drain().flatMap { _ =>
      val before = cursor.collectHtml()
      before should include("Welcome to")

      app.appRouter.navigate("/button")

      async.drain().map { _ =>
        val after = cursor.collectHtml()
        after should not be before
        after should not include "Welcome to"
        app.ssrStatus shouldBe 200
      }
    }
  }
}
