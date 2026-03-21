package jfx.layout

import jfx.core.component.ManagedElementComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLDivElement, KeyboardEvent, window}

final class Drawer extends ManagedElementComponent[HTMLDivElement] {

  val openProperty: Property[Boolean] = Property(false)
  val widthProperty: Property[String] = Property("280px")
  val closeOnScrimClickProperty: Property[Boolean] = Property(true)

  private val scrim = new Div()
  private val panelShell = new Div()
  private val panel = new Div()
  private val navigationHost = new Div()
  private val contentHost = new Div()

  private var structureInitialized = false

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-drawer")
    divElement
  }

  private val openObserver = openProperty.observe(syncOpenState)
  addDisposable(openObserver)

  private val widthObserver = widthProperty.observe { width =>
    css.setProperty("--drawer-width", width)
  }
  addDisposable(widthObserver)

  private val scrimClickListener: Event => Unit = _ => {
    if (closeOnScrimClickProperty.get && openProperty.get) {
      close()
    }
  }
  scrim.element.addEventListener("click", scrimClickListener)
  addDisposable(() => scrim.element.removeEventListener("click", scrimClickListener))

  private val keyDownListener: KeyboardEvent => Unit = event => {
    if (event.key == "Escape" && openProperty.get) {
      close()
    }
  }
  window.addEventListener("keydown", keyDownListener)
  addDisposable(() => window.removeEventListener("keydown", keyDownListener))

  def isOpen: Boolean =
    openProperty.get

  def isOpen_=(value: Boolean): Unit =
    openProperty.set(value)

  def width: String =
    widthProperty.get

  def width_=(value: String): Unit =
    widthProperty.set(value)

  def closeOnScrimClick: Boolean =
    closeOnScrimClickProperty.get

  def closeOnScrimClick_=(value: Boolean): Unit =
    closeOnScrimClickProperty.set(value)

  def open(): Unit =
    openProperty.set(true)

  def close(): Unit =
    openProperty.set(false)

  def toggle(): Unit =
    openProperty.set(!openProperty.get)

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      scrim.classProperty += "jfx-drawer__scrim"
      panelShell.classProperty += "jfx-drawer__panel-shell"
      panel.classProperty += "jfx-drawer__panel"
      navigationHost.classProperty += "jfx-drawer__navigation"
      contentHost.classProperty += "jfx-drawer__content"

      addChild(scrim)
      addChild(panelShell)
      addChild(contentHost)

      panelShell.addChild(panel)
      panel.addChild(navigationHost)
    }

  private[jfx] def initializeStructure(): Unit =
    ensureStructure()

  private[jfx] def navigationHostComponent: Div = {
    ensureStructure()
    navigationHost
  }

  private[jfx] def contentHostComponent: Div = {
    ensureStructure()
    contentHost
  }

  private def syncOpenState(isOpen: Boolean): Unit =
    if (isOpen) {
      element.classList.add("jfx-drawer--open")
    } else {
      element.classList.remove("jfx-drawer--open")
    }
}

object Drawer {

  def drawer(init: Drawer ?=> Unit = {}): Drawer =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Drawer()
      component.initializeStructure()

      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given Drawer = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }

  def drawerNavigation(init: => Unit)(using drawer: Drawer): Unit =
    renderSection(drawer.navigationHostComponent)(init)

  def drawerContent(init: => Unit)(using drawer: Drawer): Unit =
    renderSection(drawer.contentHostComponent)(init)

  def drawerOpen(using drawer: Drawer): Boolean =
    drawer.isOpen

  def drawerOpen_=(value: Boolean)(using drawer: Drawer): Unit =
    drawer.isOpen = value

  def drawerWidth(using drawer: Drawer): String =
    drawer.width

  def drawerWidth_=(value: String)(using drawer: Drawer): Unit =
    drawer.width = value

  def closeOnScrimClick(using drawer: Drawer): Boolean =
    drawer.closeOnScrimClick

  def closeOnScrimClick_=(value: Boolean)(using drawer: Drawer): Unit =
    drawer.closeOnScrimClick = value

  def openDrawer(using drawer: Drawer): Unit =
    drawer.open()

  def closeDrawer(using drawer: Drawer): Unit =
    drawer.close()

  def toggleDrawer(using drawer: Drawer): Unit =
    drawer.toggle()

  private def renderSection(host: Div)(init: => Unit): Unit =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      DslRuntime.withComponentContext(ComponentContext(Some(host), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Div = host
        init
      }
    }
}
