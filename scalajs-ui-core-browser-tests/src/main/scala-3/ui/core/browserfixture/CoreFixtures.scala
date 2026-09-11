package ui.core.browserfixture

import ui.core.component.{AbstractComponent, HydrationBoundary, Runtime}
import ui.core.layout.{TextArea, TextComponent}
import ui.core.render.*
import ui.core.state.Disposable
import ui.core.statement.KeyedChildren
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Test application only. Uses public core contracts, no production bridge or editor dependency. */
@JSExportTopLevel("coreFixtures")
object CoreFixtures {
  private class Box(val tagName: String, id: String = "") extends AbstractComponent {
    override def compose(cursor: Cursor): Unit = if (id.nonEmpty) host.setAttribute("id", id)
  }
  private class Label(value: String, id: String = "") extends Box("p", id) {
    val text = new TextComponent(value)
    override def compose(cursor: Cursor): Unit = {
      super.compose(cursor)
      Runtime.mount(text, cursor, Some(this))
    }
  }
  private class Field(initial: String) extends TextArea(initial) {
    override def compose(cursor: Cursor): Unit = {
      super.compose(cursor)
      host.setAttribute("id", "source")
      host.setAttribute("name", "body")
    }
  }
  private var root: AbstractComponent = _
  private var moving: Label = _
  private var destination: Box = _
  private var area: Field = _
  private var group: KeyedChildren[String, (String, String), Label] = _
  private var lease: Disposable = Disposable.empty
  private var hydration: Cursor = _
  private var ready = 0
  private var repairs = 0
  private var captured = ""

  private def child[C <: AbstractComponent](owner: AbstractComponent, value: C): C =
    Runtime.mount(value, Runtime.contentCursor(owner), Some(owner))

  @JSExport def mountMove(container: dom.Element): Unit = {
    root = Runtime.mount(new Box("div"), DomCursor.root(container))
    val source = child(root, new Box("section", "left"))
    destination = child(root, new Box("section", "right"))
    moving = child(source, new Label("A😀BC", "moving"))
    moving.host.setAttribute("contenteditable", "true")
  }
  @JSExport def move(): Unit = Runtime.move(moving, destination, 0)
  @JSExport def addMovingField(): Unit = { area = child(moving, new Field("control text")) }
  @JSExport def splice(start: Int, count: Int, inserted: String): Unit = moving.text.spliceText(start, count, inserted)
  @JSExport def appendMoved(): Unit = { child(moving, new TextComponent("!")); () }
  @JSExport def protect(): Unit = { lease.dispose(); lease = HostMutationGuard.protect(moving.host) }
  @JSExport def release(): Unit = lease.dispose()
  @JSExport def attempt(action: String): String = {
    try {
      action match {
        case "text" => moving.text.setText("changed")
        case "move" => move()
        case "remove" => Runtime.unmount(root)
        case "property" => moving.host.setProperty("textContent", "changed")
        case "mount" => child(moving, new Label("new"))
      }
      "allowed"
    } catch { case _: HostWriteBlocked => "blocked" }
  }
  @JSExport def dispose(): Unit = { lease.dispose(); if (root != null) Runtime.unmount(root) }

  @JSExport def mountKeyed(container: dom.Element): Unit = {
    root = Runtime.mount(new Box("div"), DomCursor.root(container))
    group = child(root, new KeyedChildren[String, (String, String), Label](
      Seq("a" -> "one", "b" -> "two"), _._1,
      item => new Label(item._2, item._1), (component, item) => component.text.setText(item._2)))
  }
  @JSExport def updateKeyed(): Unit = group.setItems(Seq("b" -> "TWO", "a" -> "one", "c" -> "three"))

  @JSExport def renderArea(value: String): String = Runtime.renderToString { cursor =>
    val form = Runtime.mount(new Box("form"), cursor)
    child(form, new Field(value))
    form
  }
  @JSExport def hydrateArea(container: dom.Element, initial: String): Unit = {
    hydration = HydratingCursor.root(container)
    root = Runtime.mount(new Box("form"), hydration)
    area = Runtime.mount(new Field(initial), Runtime.contentCursor(root), Some(root))
    hydration.completeHydration()
  }
  @JSExport def areaValue(): String = area.value
  @JSExport def areaObserved(): String = area.valueProperty.get
  @JSExport def areaDefault(): String = area.defaultValue
  @JSExport def setAreaValue(value: String): Unit = area.setValue(value)
  @JSExport def setAreaDefault(value: String): Unit = area.setDefaultValue(value)
  @JSExport def mountPendingArea(container: dom.Element): Unit = {
    area = new Field("baseline")
    area.setValue("pending draft")
    root = Runtime.mount(area, DomCursor.root(container))
  }

  private def boundaryPage(): AbstractComponent = new Box("main") {
    override def compose(cursor: Cursor): Unit = {
      area = Runtime.mount(new Field("server"), cursor, Some(this))
      val boundary = new HydrationBoundary[String]("section",
        _ => { captured = area.value; captured },
        (host, _) => require(!host.attribute("data-reject").contains("yes"), "profile mismatch"),
        _ => repairs += 1)({
        for (value <- Seq("first", "second")) {
          val label = new Label(value) {
            override def beforeHostBinding(node: HostNode, current: Cursor): Unit =
              if (current.isHydrating)
                require(DomNodes.raw(node).textContent == value, "text mismatch")
          }
          Runtime.mount(label, summon[Cursor], Some(summon[AbstractComponent]))
          summon[Cursor].afterHydration(() => ready += 1)
        }
      })
      Runtime.mount(boundary, cursor, Some(this))
    }
  }
  @JSExport def renderBoundary(): String = Runtime.renderToString(cursor => Runtime.mount(boundaryPage(), cursor))
  @JSExport def hydrateBoundary(container: dom.Element, complete: Boolean): Unit = {
    ready = 0; repairs = 0; captured = ""
    hydration = HydratingCursor.root(container)
    root = Runtime.mount(boundaryPage(), hydration)
    if (complete) hydration.completeHydration()
  }
  @JSExport def complete(): Unit = hydration.completeHydration()
  @JSExport def readyCount(): Int = ready
  @JSExport def repairCount(): Int = repairs
  @JSExport def capturedValue(): String = captured
}
