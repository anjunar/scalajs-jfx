package jfx.bridge

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Exercises the bridge end to end through its own JS-facing surface -- `ScopeHandleBridge`,
  * `ComponentHandleBridge`, `PropertyHandle`, `ListPropertyHandle` -- the same way a TypeScript
  * consumer would, but from Scala so the suite runs in plain Node without a DOM.
  *
  * `mount` and `hydrate` need a real `dom.Element`, which `HydratingCursor` cannot get in this test
  * environment (see `AppSsrSpec`'s doc comment for why). `renderToString` needs none, so it is the
  * whole of what runs here -- the same split `AppSsrSpec` already lives with.
  */
class JfxRuntimeBridgeSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  private val runtime = BridgeRuntime.bridgeRuntime

  private def render(
      build: js.Function1[ScopeHandleBridge, Unit]
  ): scala.concurrent.Future[SsrResultHandle] =
    runtime.renderToString(build, js.undefined).toFuture

  "renderToString" should "mount a generic element, a library component and reactive text" in {
    val counter = runtime.property[Int](0)
    counter.set(2)

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "vbox",
        js.Dictionary(),
        (self, inner) => {
          self.setClasses(js.Array("app"))
          inner.child("div", (_, divScope) => divScope.text(counter.map(v => s"count: $v")))
        }
      )
      ()
    }

    render(build).map { result =>
      result.status shouldBe 200
      // "vbox" is VBox's own base class (added in its compose); "app" is the one setClasses added.
      result.html should include("class=\"vbox app\"")
      result.html should include("<div>")
      result.html should include("count: 2")
    }
  }

  it should "resolve a button's label and disabled option, both possibly reactive" in {
    val disabled = runtime.property[Boolean](true)

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "button",
        js.Dictionary("label" -> "Go".asInstanceOf[js.Any], "disabled" -> disabled),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("<button")
      result.html should include("Go")
      result.html should include("disabled")
    }
  }

  it should "reconcile forEach over a ListProperty and mount when's body while active" in {
    val items = runtime.listProperty[String](js.Array("a", "b"))
    val flag  = runtime.property[Boolean](true)

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.forEach(
        items.asInstanceOf[JsReadOnlyProperty[js.Array[js.Any]]],
        (value, index, itemScope) => itemScope.text(s"$index:${value.asInstanceOf[String]}")
      )
      scope.when(
        flag.asInstanceOf[JsReadOnlyProperty[Boolean]],
        whenScope => whenScope.text("visible")
      )
    }

    render(build).map { result =>
      result.html should include("0:a")
      result.html should include("1:b")
      result.html should include("visible")
    }
  }

  it should "wait for fetchInto's loader before serialising" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.fetch(
        () => js.Promise.resolve[js.Any]("loaded"),
        (value, loadedScope) => loadedScope.text(value.asInstanceOf[String]),
        (_, _) => ()
      )
    }

    render(build).map { result =>
      result.html should include("loaded")
    }
  }

  it should "surface a rejected loader through onFailed" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.fetch(
        () => js.Promise.reject(new js.Error("boom")).asInstanceOf[js.Promise[js.Any]],
        (_, _) => (),
        (error, failedScope) => failedScope.text(s"failed: ${error.asInstanceOf[js.Error].message}")
      )
    }

    render(build).map { result =>
      result.html should include("failed: boom")
    }
  }

  private def jsRoute(
      path: String,
      body: js.Function1[ScopeHandleBridge, Unit],
      children: js.Array[js.Any] = js.Array()
  ): js.Dictionary[js.Any] =
    js.Dictionary(
      "path"     -> path,
      "load"     -> (((_: js.Any) => body): js.Function1[js.Any, js.Any]),
      "children" -> children,
      "status"   -> 200
    )

  "the router facade" should "mount a route table and render the matched nested route through an outlet" in {
    val child: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.text("leaf: 42"); ()
    }
    val parent: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.child("h1", (_, heading) => heading.text("router shell"))
      scope.component("router-outlet", js.Dictionary(), (_, _) => ())
      ()
    }

    val routes = js.Array[js.Any](
      jsRoute("/shell", parent, js.Array[js.Any](jsRoute("detail/:id", child)))
    )

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "router",
        js.Dictionary(
          "routes" -> routes,
          "config" -> js.Dictionary[js.Any]("initialUrl" -> "/shell/detail/42")
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("router shell")
      result.html should include("leaf: 42")
    }
  }

  it should "forward to the onFailure route for an unmatched path" in {
    val notFound: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.text("not found"); ()
    }
    val home: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.text("home"); ()
    }

    val routes = js.Array[js.Any](
      jsRoute("/", home), {
        val route = jsRoute("/404", notFound)
        route("status") = 404
        route
      }
    )

    val onFailure: js.Function1[js.Object, js.Any] = _ => "/404"

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "router",
        js.Dictionary(
          "routes" -> routes,
          "config" -> js.Dictionary[js.Any](
            "initialUrl"           -> "/nope",
            "onFailure"            -> onFailure,
            "renderErrorsOnServer" -> true
          )
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.status shouldBe 404
      result.html should include("not found")
    }
  }

  // --- the controls facade (JAVASCRIPT_API.md §9, step 6) --------------------

  private type ScopeBody = js.Function1[ScopeHandleBridge, Unit]

  private def scopeBody(text: String): ScopeBody =
    ((scope: ScopeHandleBridge) => { scope.text(text); () })

  private def cellRenderer(render: js.Any => String): js.Function2[js.Any, Int, ScopeBody] =
    ((item: js.Any, _: Int) => scopeBody(render(item)))

  "the tabs facade" should "render only the active panel in active-only mode" in {
    val tabs = js.Array[js.Any](
      js.Dictionary[js.Any]("title" -> "Overview", "content" -> scopeBody("overview body")),
      js.Dictionary[js.Any]("title" -> "Activity", "content" -> scopeBody("activity body"))
    )

    val build: ScopeBody = { scope =>
      scope.component("tabs", js.Dictionary("tabs" -> tabs, "selectedIndex" -> 1), (_, _) => ())
      ()
    }

    render(build).map { result =>
      result.html should include("Overview")
      result.html should include("Activity")
      result.html should include("activity body")
      result.html should not include "overview body"
    }
  }

  "the carousel facade" should "render every slide when ssrShowAllStates is set" in {
    val slides = runtime.listProperty[js.Any](js.Array("Atlas", "Signal", "Harbor"))

    val build: ScopeBody = { scope =>
      scope.component(
        "carousel",
        js.Dictionary(
          "items"            -> slides,
          "slideRenderer"    -> cellRenderer(item => s"slide: ${item.asInstanceOf[String]}"),
          "ssrShowAllStates" -> true
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("slide: Atlas")
      result.html should include("slide: Signal")
      result.html should include("slide: Harbor")
    }
  }

  private def column(text: String, render: js.Any => String): js.Any =
    js.Dictionary[js.Any](
      "text" -> text,
      "cell" -> (((row: js.Any) => scopeBody(render(row))): js.Function1[js.Any, ScopeBody])
    )

  "the table-view facade" should "render one row per item of a local source, with cell renderers" in {
    val books = runtime.listProperty[js.Any](
      js.Array(
        js.Dictionary[js.Any]("title" -> "1984", "author"       -> "Orwell"),
        js.Dictionary[js.Any]("title" -> "Siddhartha", "author" -> "Hesse")
      )
    )

    val build: ScopeBody = { scope =>
      scope.component(
        "table-view",
        js.Dictionary(
          "source"    -> books,
          "crawlable" -> true,
          "crawlId"   -> "books",
          "columns"   -> js.Array[js.Any](
            column("Title", row => row.asInstanceOf[js.Dictionary[String]]("title")),
            column("Author", row => row.asInstanceOf[js.Dictionary[String]]("author"))
          )
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-table-view")
      result.html should include("Title")
      result.html should include("Author")
      result.html should include("1984")
      result.html should include("Orwell")
      result.html should include("Siddhartha")
    }
  }

  it should "load the first page of a remote source before serialising" in {
    val page = js.Dictionary[js.Any](
      "items" -> js.Array[js.Any](
        js.Dictionary[js.Any]("title" -> "Remote One"),
        js.Dictionary[js.Any]("title" -> "Remote Two")
      ),
      "offset"     -> 0,
      "totalCount" -> 2
    )

    val remoteSource = js.Dictionary[js.Any](
      "load" -> (((_: js.Any) => js.Promise.resolve[js.Any](page)): js.Function1[js.Any, js.Any]),
      "initialQuery" -> js.Dictionary[js.Any]("offset" -> 0, "limit" -> 50),
      "initial"      -> js.Array[js.Any](
        js.Dictionary[js.Any]("title" -> "Remote One"),
        js.Dictionary[js.Any]("title" -> "Remote Two")
      ),
      "totalCount" -> 2
    )

    val build: ScopeBody = { scope =>
      scope.component(
        "table-view",
        js.Dictionary(
          "source"    -> remoteSource,
          "crawlable" -> true,
          "crawlId"   -> "remote",
          "columns"   -> js.Array[js.Any](
            column("Title", row => row.asInstanceOf[js.Dictionary[String]]("title"))
          )
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("Remote One")
      result.html should include("Remote Two")
    }
  }

  "the data-grid facade" should "render cells of a local source through the renderer" in {
    val items = runtime.listProperty[js.Any](js.Array("alpha", "beta", "gamma"))

    val build: ScopeBody = { scope =>
      scope.component(
        "data-grid",
        js.Dictionary(
          "source"       -> items,
          "cellRenderer" -> cellRenderer(item => s"cell: ${item.asInstanceOf[String]}"),
          "crawlable"    -> true,
          "crawlId"      -> "grid"
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-data-grid")
      result.html should include("cell: alpha")
      result.html should include("cell: gamma")
    }
  }

  "the virtual-list-view facade" should "render rows of a local source through the renderer" in {
    val items = runtime.listProperty[js.Any](js.Array("one", "two", "three"))

    val build: ScopeBody = { scope =>
      scope.component(
        "virtual-list-view",
        js.Dictionary(
          "source"       -> items,
          "cellRenderer" -> cellRenderer(item => s"line: ${item.asInstanceOf[String]}"),
          "crawlable"    -> true,
          "crawlId"      -> "list"
        ),
        (_, _) => ()
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-virtual-list")
      result.html should include("line: one")
      result.html should include("line: three")
    }
  }

  // --- the viewport facade (JAVASCRIPT_API.md §9, step 7) --------------------

  "the viewport facade" should "mount a window that stays open while present in the tree" in {
    val build: ScopeBody = { scope =>
      scope.component(
        "viewport",
        js.Dictionary(),
        (_, viewportScope) => {
          viewportScope.component(
            "window",
            js.Dictionary[js.Any](
              "title"    -> "A room for thoughts",
              "widthPx"  -> 400,
              "heightPx" -> 300
            ),
            (_, windowScope) => { windowScope.text("window body"); () }
          )
          ()
        }
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-window")
      result.html should include("A room for thoughts")
      result.html should include("window body")
    }
  }

  it should "render a notification's message under its kind class" in {
    val build: ScopeBody = { scope =>
      scope.component(
        "viewport",
        js.Dictionary(),
        (_, viewportScope) => {
          viewportScope.component(
            "notification",
            js.Dictionary[js.Any]("message" -> "Saved", "kind" -> "success"),
            (_, _) => ()
          )
          ()
        }
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-viewport-notification--success")
      result.html should include("Saved")
    }
  }

  it should "render an overlay anchored under the viewport" in {
    val build: ScopeBody = { scope =>
      scope.component(
        "viewport",
        js.Dictionary(),
        (_, viewportScope) => {
          viewportScope.component(
            "overlay",
            js.Dictionary[js.Any]("widthPx" -> 240),
            (_, overlayScope) => { overlayScope.text("overlay body"); () }
          )
          ()
        }
      )
      ()
    }

    render(build).map { result =>
      result.html should include("jfx-viewport-overlay")
      result.html should include("overlay body")
    }
  }

  "the component registry" should "reject an unregistered name" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component("does-not-exist", js.Dictionary(), (_, _) => ())
      ()
    }

    recoverToSucceededIf[IllegalArgumentException](render(build))
  }

  "property" should "notify observers and expose the live value through get" in {
    val prop     = runtime.property[Int](1)
    var observed = Vector.empty[Int]
    val handle   = prop.observe(value => observed = observed :+ value)

    prop.set(2)
    prop.set(3)
    handle.dispose()
    prop.set(4)

    prop.get shouldBe 4
    observed shouldBe Vector(1, 2, 3)
  }

  "listProperty" should "reflect add/insert/removeAt through get and size" in {
    val list = runtime.listProperty[String](js.Array("a"))
    list.add("b")
    list.insert(0, "start")
    list.removeAt(2)

    list.get.toSeq shouldBe Seq("start", "a")
    list.size shouldBe 2
  }
}
