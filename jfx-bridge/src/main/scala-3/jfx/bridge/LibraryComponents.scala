package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.layout.{Button, HBox, VBox}
import jfx.core.render.Cursor

import scala.scalajs.js

/** The registry entries the prototype ships with. `vbox` and `hbox` live in `jfx.core.layout`
  * already, so registering them costs nothing beyond the wiring below; a fourth entry is one more
  * `object` plus one line in [[BridgeRuntime]], exactly as JAVASCRIPT_API.md §4 describes.
  *
  * `jfx-controls` is deliberately not reached into here: this module depends on `jfx-core` alone
  * (JAVASCRIPT_API.md §9, step 2 -- "nur core"). Filling out the registry with combo-box, table-view
  * and friends is step 6, once the boundary itself has proven out.
  */
private[bridge] object VBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    VBox.vbox {
      val self        = summon[VBox]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
}

private[bridge] object HBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    HBox.hbox {
      val self        = summon[HBox]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
}

/** `dsl.ts`'s `button(label, options, body)` folds `label` and `ButtonOptions` into one options
  * object (`{ label, ...options }`); this factory is the one place that takes it back apart.
  */
private[bridge] object ButtonFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val labelProperty = ReactiveBridge.asProperty[String](options.getOrElse("label", ""))

    Button.button(labelProperty) {
      val self        = summon[Button]
      val childCursor = summon[Cursor]

      options.get("type").foreach(value => self.buttonType(value.asInstanceOf[String]))
      options.get("disabled").foreach(value => self.disabled = ReactiveBridge.asProperty[Boolean](value))

      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }
  }
}
