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

  private def documentFor(request: RequestContext, url: String): AppDocument =
    new AppDocument(request, url)

  private def mounted(url: String, request: RequestContext = desktopRequest): AppDocument = {
    val document = documentFor(request, url)
    Runtime.mount(document, new SsrCursor())
    document
  }

  "SSR" should "render the start route to the same HTML twice" in {
    for {
      first <- Runtime.renderToStringAsync(cursor =>
        Runtime.mount(documentFor(desktopRequest, "/"), cursor)
      )
      second <- Runtime.renderToStringAsync(cursor =>
        Runtime.mount(documentFor(desktopRequest, "/"), cursor)
      )
    } yield {
      first should include("class=\"app-shell\"")
      first should include("Welcome to")
      second shouldBe first
    }
  }

  it should "render route metadata in the document head" in {
    Runtime
      .renderToStringAsync(cursor =>
        Runtime.mount(documentFor(desktopRequest, "/de/router"), cursor)
      )
      .map { html =>
        html should startWith("<html lang=\"de\"><head>")
        html should include("<div id=\"root\"><app>")
        html should include("<title data-jfx-head=\"title\">Router | scalajs-jfx</title>")
        html should include(
          "<link data-jfx-head=\"link:canonical\" rel=\"canonical\" href=\"https://anjunar.github.io/scalajs-jfx/de/router/\">"
        )
        html should include("hreflang=\"de\"")
        html should include("<html lang=\"de\">")
        html should not include "%SITE_"
      }
  }

  it should "keep document head entries and assets in stable order" in {
    val assets = Seq(
      jfx.core.document.HeadEntry(
        "asset:0",
        "link",
        Seq("rel" -> "stylesheet", "href" -> "/assets/app.css")
      ),
      jfx.core.document.HeadEntry(
        "asset:1",
        "script",
        Seq("type" -> "module", "src" -> "/assets/app.js")
      )
    )

    Runtime
      .renderToStringAsync(cursor =>
        Runtime.mount(new AppDocument(desktopRequest, "/en/", assets), cursor)
      )
      .map { html =>
        html
          .indexOf("data-jfx-head=\"title\"") should be < html.indexOf("data-jfx-head=\"asset:0\"")
        html should include(
          "<link data-jfx-head=\"asset:0\" rel=\"stylesheet\" href=\"/assets/app.css\">"
        )
        html should include(
          "<script data-jfx-head=\"asset:1\" type=\"module\" src=\"/assets/app.js\"></script>"
        )
      }
  }

  it should "render an asynchronous route once its loader completed" in {
    Runtime
      .renderToStringAsync(cursor =>
        Runtime.mount(documentFor(desktopRequest, "/rendering"), cursor)
      )
      .map { html =>
        html should include("class=\"app-shell\"")
        html should not include "jfx:Route:pending"
      }
  }

  it should "render the router demo child inside its parent route" in {
    Runtime
      .renderToStringAsync(cursor =>
        Runtime.mount(documentFor(desktopRequest, "/router/user/42"), cursor)
      )
      .map { html =>
        html should include("Router &amp; route model")
        html should include("Nested route demo")
        html should include("Explicit route context")
        html should include("<div class=\"showcase-metric__label\">42</div>")
      }
  }

  it should "answer 200 for a known route and 404 for an unknown one" in {
    // The status only exists once the router composed, so the app has to be mounted first.
    mounted("/").ssrStatus shouldBe 200
    mounted("/no-such-page").ssrStatus shouldBe 404
  }

  it should "render the localized application boundary for an unknown route" in {
    val document = documentFor(desktopRequest, "/de/no-such-page")

    Runtime
      .renderToStringAsync(cursor => Runtime.mount(document, cursor))
      .map { html =>
        document.ssrStatus shouldBe 404
        html should include("Seite nicht gefunden")
        html should include("/de/no-such-page")
        html should include("Zur Übersicht")
      }
  }

  "The app" should "publish request, i18n and theme through the component context" in {
    val app = mounted("/").app

    RequestContext.current(using app) should not be empty
    I18nRuntime.current(using app) should not be empty
    AppTheme.current(using app) should not be empty

    AppTheme.current(using app).map(_.modeProperty.get) shouldBe Some(AppTheme.Mode.Light)
  }

  it should "give every instance its own theme, not a shared one" in {
    val first  = mounted("/").app
    val second = mounted("/").app

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

    RequestContext.current(using mounted("/", mobile).app).map(_.isMobile) shouldBe Some(true)
  }

  it should "update sidebar section titles when the locale changes" in {
    val cursor   = new SsrCursor()
    val document = documentFor(desktopRequest, "/")
    Runtime.mount(document, cursor)

    cursor.collectHtml() should include(">Foundation<")

    document.app.appRouter.navigate("/de/")

    cursor.collectHtml() should include(">Grundlagen<")
    cursor.collectHtml() should not include ">Foundation<"
  }

  "The router" should "change the rendered tree on navigation" in {
    val async    = new AsyncRenderContext()
    val cursor   = new SsrCursor(async)
    val document = documentFor(desktopRequest, "/")

    Runtime.mount(document, cursor)

    async.drain().flatMap { _ =>
      val before = cursor.collectHtml()
      before should include("Welcome to")

      document.app.appRouter.navigate("/button")

      async.drain().map { _ =>
        val after = cursor.collectHtml()
        after should not be before
        after should not include "Welcome to"
        document.ssrStatus shouldBe 200
      }
    }
  }
}
