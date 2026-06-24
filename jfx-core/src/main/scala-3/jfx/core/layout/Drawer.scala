package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo.{child, render}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.render.Cursor
import jfx.core.state.Property

final class Drawer extends AbstractComponent {
  val tagName = "div"

  val openProperty = Property(false)
  val drawerWidthProperty = Property("280px")
  val sideProperty = Property(Drawer.Side.Start)
  val closeOnScrimClickProperty = Property(true)

  private var navigationHost: Div = _
  private var contentHost: Div = _

  private val panelShellWidth =
    openProperty.flatMap { open =>
      drawerWidthProperty.map { width =>
        if (open) s"min(92vw, $width)" else "0px"
      }
    }

  private[layout] def navigationSlot: Div = navigationHost
  private[layout] def contentSlot: Div = contentHost

  override def compose(cursor: Cursor): Unit = {
    given AbstractComponent = this

    addClass("jfx-drawer")
    classIf("jfx-drawer--open", openProperty)
    classIf("jfx-drawer--start", sideProperty.map(_ == Drawer.Side.Start))
    classIf("jfx-drawer--end", sideProperty.map(_ == Drawer.Side.End))

    style {
      display = "flex"
      width = "100%"
      height = "100%"
      position = "relative"
    }

    render(this, cursor) {
      div {
        addClass("jfx-drawer__panel-shell")

        style {
          width = panelShellWidth
          position = "relative"
          height = "100%"
        }

        div {
          addClass("jfx-drawer__panel")

          style {
            width = drawerWidthProperty
            height = "100%"
            overflow = "hidden"
          }
          

          navigationHost = div {
            addClass("jfx-drawer__navigation")

            style {
              display = "flex"
              flexDirection = "column"
              height = "100%"
            }
          }
        }
      }

      div {
        addClass("jfx-drawer__scrim")

        onClick { _ =>
          if (closeOnScrimClickProperty.get && openProperty.get) {
            openProperty.set(false)
          }
        }
      }

      contentHost = div {
        addClass("jfx-drawer__content")

        style {
          display = "flex"
          flexDirection = "column"
          flex = "1"
          height = "100%"
          minWidth = "0"
        }
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

  def drawerNavigation(body: AbstractComponent ?=> Cursor ?=> Unit)(using drawer: Drawer, cursor: Cursor): Unit = {
    val childCursor = cursor.sub(drawer.navigationSlot.host)
    render(drawer.navigationSlot, childCursor) {
      body
    }
  }

  def drawerContent(body: AbstractComponent ?=> Cursor ?=> Unit)(using drawer: Drawer, cursor: Cursor): Unit = {
    val childCursor = cursor.sub(drawer.contentSlot.host)
    render(drawer.contentSlot, childCursor) {
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
