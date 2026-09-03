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

  private def render(build: js.Function1[ScopeHandleBridge, Unit]): scala.concurrent.Future[SsrResultHandle] =
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

  "the component registry" should "reject an unregistered name" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component("does-not-exist", js.Dictionary(), (_, _) => ())
      ()
    }

    recoverToSucceededIf[IllegalArgumentException](render(build))
  }

  "property" should "notify observers and expose the live value through get" in {
    val prop      = runtime.property[Int](1)
    var observed  = Vector.empty[Int]
    val handle    = prop.observe(value => observed = observed :+ value)

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
