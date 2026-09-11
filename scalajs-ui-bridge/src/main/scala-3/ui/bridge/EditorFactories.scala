package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.render.Cursor
import ui.core.state.{Property as CoreProperty}
import ui.editor.Editor
import ui.editor.plugins.{
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
import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.ExecutionContext
import ui.editor.{MediaUploader, MediaUrlPolicy, MediaReference, UploadedMediaReference}

/** Step 6 of JAVASCRIPT_API.md §9, the editor half -- the trigger was FINAL.md Priorität 4
  * ("`scalajs-ui-editor` veröffentlichen oder bewusst ausklammern"), settled as: veröffentlichen, with a
  * facade like every other family package (npm-Modularisierung Lauf 7). `ui.editor.Editor` is is a
  * plain `ui.forms.Control[String]` -- SSR/hydration, the placeholder contract and form
  * registration are the ones every other control already has, so this factory needs no counterpart
  * to `FormFactories.DynamicFormular`: an `editor` registered under a `form`/`subForm` binds
  * through the exact same generic `(_, s: CoreProperty[Any], t: CoreProperty[Any])` branch of
  * `DynamicFormular.bindNow` that `input` uses. Markdown is the stable value contract on both sides
  * of the bridge; Lexical EditorState JSON remains an implementation detail.
  *
  * The one thing this factory does that no other does: `ui.editor.plugins.basePlugin()`/
  * `headingPlugin()`/... are Scala functions, not values, so a JS `plugins` list is turned into
  * calls rather than into constructor arguments. MediaUploader and MediaUrlPolicy are projected
  * independently of the plugin list; Scala Future and JavaScript Promise cross only at this factory
  * boundary. The generic forms Media/Cropper payload is deliberately not involved.
  *
  * Like `ComboBox`, `linkPlugin()`/`imagePlugin()` need a `viewport` ancestor: their dialogs are
  * `Viewport.WindowConf`s (`DefaultDialogService`, `scalajs-ui-editor`'s own doc comment).
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

      options.get("mediaUploader").foreach { value =>
        val facade = value.asInstanceOf[js.Dynamic]
        self.mediaUploader = Some(new MediaUploader {
          def upload(file: dom.File, signal: dom.AbortSignal) = {
            given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
            facade.upload(file, signal).asInstanceOf[js.Promise[js.Dynamic]].toFuture.map {
              result =>
                require(
                  result != null && js.typeOf(result.src) == "string" && js
                    .typeOf(result.mediaId) == "string",
                  "Media upload must return src and mediaId strings"
                )
                UploadedMediaReference(
                  result.src.asInstanceOf[String],
                  result.mediaId.asInstanceOf[String]
                )
            }
          }
        })
      }
      options.get("mediaUrlPolicy").foreach { value =>
        val facade = value.asInstanceOf[js.Dynamic]
        self.mediaUrlPolicy = new MediaUrlPolicy {
          def resolve(src: String): Option[MediaReference] = {
            val result = facade.resolve(src)
            if (result == null || js.isUndefined(result)) None
            else {
              require(
                js.typeOf(result.src) == "string",
                "Media URL policy must return a src string"
              )
              val id = result.mediaId
              require(js.isUndefined(id) || js.typeOf(id) == "string", "Media ID must be a string")
              Some(
                MediaReference(
                  result.src.asInstanceOf[String],
                  if (js.isUndefined(id)) None else Some(id.asInstanceOf[String])
                )
              )
            }
          }
        }
      }
      options.get("onMediaStatus").foreach { value =>
        val callback = value.asInstanceOf[js.Function1[js.Object, Unit]]
        self.onMediaStatus = status =>
          callback(js.Dynamic.literal(pending = status.pending, error = status.error.orNull))
      }

      options.get("value").foreach(value => self.valueProperty.set(ControlFactories.str(value)))
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options.get("editable").foreach {
        case handle: PropertyHandle[?] =>
          self.configureEditable(
            handle.underlyingProperty.asInstanceOf[CoreProperty[Boolean]]
          )
        case value => self.configureEditable(ControlFactories.bool(value))
      }
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
