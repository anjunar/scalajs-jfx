package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.DslLayer.{child, render, renderInto}
import ui.core.dsl.EventDsl.{onClick, onWindowKeyDown}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Div.div
import ui.core.render.Cursor
import ui.core.state.Property

final class Drawer extends AbstractComponent {
  val tagName = "div"

  val openProperty              = Property(false)
  val drawerWidthProperty       = Property("280px")
  val sideProperty              = Property(Drawer.Side.Start)
  val closeOnScrimClickProperty = Property(true)

  private var navigationHost: Div = _
  private var contentHost: Div    = _

  private val panelShellWidth =
    openProperty.flatMap { open =>
      drawerWidthProperty.map { width =>
        if (open) s"min(92vw, $width)" else "0px"
      }
    }

  private[layout] def navigationSlot: Div = navigationHost
  private[layout] def contentSlot: Div    = contentHost

  override def compose(cursor: Cursor): Unit = {
    given AbstractComponent = this

    addClass("ui-drawer")
    classIf("ui-drawer--open", openProperty)
    classIf("ui-drawer--start", sideProperty.map(_ == Drawer.Side.Start))
    classIf("ui-drawer--end", sideProperty.map(_ == Drawer.Side.End))

    render(this, cursor) {
      div {
        classes = Seq("ui-drawer__panel-shell")

        style {
          css("--ui-drawer-panel-width", panelShellWidth)
        }

        div {
          classes = Seq("ui-drawer__panel")

          style {
            css("--ui-drawer-width", drawerWidthProperty)
          }

          navigationHost = div {
            classes = Seq("ui-drawer__navigation")

          }
        }
      }

      div {
        classes = Seq("ui-drawer__scrim")

        onClick { _ =>
          if (closeOnScrimClickProperty.get && openProperty.get) {
            openProperty.set(false)
          }
        }
      }

      contentHost = div {
        classes = Seq("ui-drawer__content")

      }
    }

    onWindowKeyDown { event =>
      if (event.key == "Escape" && openProperty.get) {
        openProperty.set(false)
      }
    }
  }
}

object Drawer {
  enum Side {
    case Start, End
  }

  def drawer(body: Drawer ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Drawer =
    child(new Drawer()) {
      body
    }

  def drawerNavigation(
      body: AbstractComponent ?=> Cursor ?=> Unit
  )(using drawer: Drawer, cursor: Cursor): Unit = {
    renderInto(drawer.navigationSlot) {
      body
    }
  }

  def drawerContent(
      body: AbstractComponent ?=> Cursor ?=> Unit
  )(using drawer: Drawer, cursor: Cursor): Unit = {
    renderInto(drawer.contentSlot) {
      body
    }
  }

  def open(using drawer: Drawer): Boolean =
    drawer.openProperty.get

  def open_=(value: Boolean)(using drawer: Drawer): Unit =
    drawer.openProperty.set(value)

  def side(using drawer: Drawer): Side =
    drawer.sideProperty.get

  def side_=(value: Side)(using drawer: Drawer): Unit =
    drawer.sideProperty.set(value)

  def drawerWidth(using drawer: Drawer): String =
    drawer.drawerWidthProperty.get

  def drawerWidth_=(value: String)(using drawer: Drawer): Unit =
    drawer.drawerWidthProperty.set(value)

  def toggle()(using drawer: Drawer): Unit =
    drawer.openProperty.set(!drawer.openProperty.get)
}
