package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.layout.Drawer
import jfx.core.render.Cursor

import scala.scalajs.js

/** TypeScript's imperative handle for the Drawer enclosing its two slots. */
@js.native
private[bridge] trait DrawerHandleFacade extends js.Object {
  def isOpen(): Boolean              = js.native
  def setOpen(value: Boolean): Unit  = js.native
  def toggle(): Unit                 = js.native
}

private[bridge] object DrawerFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    Drawer.drawer {
      val self = summon[Drawer]

      options.get("open").foreach(value => self.openProperty.set(ControlFactories.bool(value)))
      options.get("drawerWidth").foreach(value =>
        self.drawerWidthProperty.set(ControlFactories.str(value))
      )
      options.get("closeOnScrimClick").foreach(value =>
        self.closeOnScrimClickProperty.set(ControlFactories.bool(value))
      )
      options.get("side").foreach { value =>
        self.sideProperty.set(
          if (ControlFactories.str(value) == "end") Drawer.Side.End else Drawer.Side.Start
        )
      }

      val isOpenFn: js.Function0[Boolean] = () => self.openProperty.get
      val setOpenFn: js.Function1[Boolean, Unit] = value => self.openProperty.set(value)
      val toggleFn: js.Function0[Unit] = () => self.openProperty.set(!self.openProperty.get)

      val handle = js.Dynamic
        .literal(isOpen = isOpenFn, setOpen = setOpenFn, toggle = toggleFn)
        .asInstanceOf[DrawerHandleFacade]

      options("compose")
        .asInstanceOf[
          js.Function3[DrawerHandleFacade, ComponentHandleBridge, ScopeHandleBridge, Unit]
        ](
          handle,
          new ComponentHandleBridge(self),
          new ScopeHandleBridge(self, summon[Cursor])
        )
    }
}

private[bridge] object DrawerNavigationFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    parent match {
      case drawer: Drawer =>
        given Drawer = drawer
        Drawer.drawerNavigation {
          val slotParent = summon[AbstractComponent]
          val slotCursor = summon[Cursor]
          body(
            new ComponentHandleBridge(slotParent),
            new ScopeHandleBridge(slotParent, slotCursor)
          )
        }
        parent
      case _ =>
        throw new IllegalStateException("drawerNavigation() must be composed inside drawer().")
    }
}

private[bridge] object DrawerContentFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    parent match {
      case drawer: Drawer =>
        given Drawer = drawer
        Drawer.drawerContent {
          val slotParent = summon[AbstractComponent]
          val slotCursor = summon[Cursor]
          body(
            new ComponentHandleBridge(slotParent),
            new ScopeHandleBridge(slotParent, slotCursor)
          )
        }
        parent
      case _ =>
        throw new IllegalStateException("drawerContent() must be composed inside drawer().")
    }
}
