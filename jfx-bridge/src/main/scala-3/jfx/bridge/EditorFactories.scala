package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.editor.Editor
import jfx.editor.plugins.{
  basePlugin,
  codePlugin,
  headingPlugin,
  horizontalRulePlugin,
  imagePlugin,
  linkPlugin,
  listPlugin,
  tablePlugin
}
import org.scalajs.dom

import scala.scalajs.js

/** Step 6 of JAVASCRIPT_API.md §9, the editor half -- the trigger was FINAL.md Priorität 4
  * ("`jfx-editor` veröffentlichen oder bewusst ausklammern"), settled as: veröffentlichen, with a
  * facade like every other family package (npm-Modularisierung Lauf 7). `jfx.editor.Editor` is is a
  * plain `jfx.forms.Control[String]` -- SSR/hydration, the placeholder contract and form
  * registration are the ones every other control already has, so this factory needs no counterpart
  * to `FormFactories.DynamicFormular`: an `editor` registered under a `form`/`subForm` binds
  * through the exact same generic `(_, s: CoreProperty[Any], t: CoreProperty[Any])` branch of
  * `DynamicFormular.bindNow` that `input` uses. Markdown is the stable value contract on both sides
  * of the bridge; Lexical EditorState JSON remains an implementation detail.
  *
  * The one thing this factory does that no other does: `jfx.editor.plugins.basePlugin()`/
  * `headingPlugin()`/... are Scala functions, not values, so a JS `plugins` list is turned into
  * calls rather than into constructor arguments. Each of the eight plugins is self-contained with
  * its default, no-argument body -- `imagePlugin()`'s upload dialog reads a local file into a data
  * URL itself, no `MediaLike`/upload hook required (that FINAL.md item is about
  * `jfx.forms.ImageCropper`, a different control). Per-plugin configuration
  * (`ImagePlugin.dialogTitle`, `defaultWidthPx`, ...), `dialogService` overriding the default
  * `Viewport`-window one, and per-plugin bodies are not projected -- each has an obvious trigger to
  * add later, the same deferral shape as `ComboBoxFactory`'s `valueRenderer`/`identityBy`.
  *
  * Like `ComboBox`, `linkPlugin()`/`imagePlugin()` need a `viewport` ancestor: their dialogs are
  * `Viewport.WindowConf`s (`DefaultDialogService`, `jfx-editor`'s own doc comment).
  */
private[bridge] object EditorFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    Editor.editor(name, standalone) {
      val self = summon[Editor]

      options.get("value").foreach(value => self.valueProperty.set(ControlFactories.str(value)))
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options
        .get("editable")
        .foreach(value => self.editableProperty.set(ControlFactories.bool(value)))
      options
        .get("editUrl")
        .foreach(value => Editor.editUrl_=(ControlFactories.str(value))(using self))
      options
        .get("editLabel")
        .foreach(value => Editor.editLabel_=(ControlFactories.str(value))(using self))
      options
        .get("readonlyUrl")
        .foreach(value => Editor.readonlyUrl_=(ControlFactories.str(value))(using self))
      options
        .get("readonlyLabel")
        .foreach(value => Editor.readonlyLabel_=(ControlFactories.str(value))(using self))

      options.get("toolbarMode").map(ControlFactories.str).foreach {
        case "menu"     => Editor.menuToolbar()(using self)
        case "floating" => Editor.floatingToolbar()(using self)
        case _          => Editor.ribbonToolbar()(using self)
      }

      options
        .get("plugins")
        .map(_.asInstanceOf[js.Array[String]].toSeq)
        .getOrElse(Seq.empty)
        .foreach(pluginName => EditorFactories.installPlugin(pluginName)(using self))
    }
  }
}

private[bridge] object EditorFactories {
  def installPlugin(name: String)(using editor: Editor): Unit =
    name match {
      case "base"           => basePlugin()
      case "heading"        => headingPlugin()
      case "list"           => listPlugin()
      case "link"           => linkPlugin()
      case "image"          => imagePlugin()
      case "table"          => tablePlugin()
      case "code"           => codePlugin()
      case "horizontalRule" => horizontalRulePlugin()
      case other            =>
        dom.console.warn(s"editor '${editor.name}': unknown plugin '$other', ignored.")
    }
}
