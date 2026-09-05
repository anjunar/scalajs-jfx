package jfx.editor

import jfx.core.component.AbstractComponent
import jfx.core.context.UrlScope
import jfx.core.di.Context
import jfx.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import jfx.core.dsl.ClassDsl.{addClass, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.editor.plugins.EditorPlugin
import jfx.forms.Form.FormContext
import jfx.forms.{Control, Editable, Placeholder}
import lexical.DialogService
import org.scalajs.dom.{HTMLDivElement, HTMLElement}

import scala.collection.mutable
import scala.compiletime.uninitialized

enum EditorToolbarMode:
  case Ribbon, Menu, Floating

/** A Markdown-valued editor.
  *
  * Markdown is the public value in every environment. On the server, readonly mode produces
  * semantic HTML and editable mode produces a textarea. In the browser the textarea is the
  * no-JavaScript/hydration representation and is progressively enhanced to Lexical after compose;
  * Lexical imports and exports Markdown at the component boundary.
  */
final class Editor private[editor] (
    val name: String,
    val standalone: Boolean,
    configure: Editor ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[String],
      Placeholder {

  override val tagName: String = "div"

  override val valueProperty: Property[String] = Property("")

  private val placeholderProperty = Property("")
  private val plugins             = mutable.ArrayBuffer.empty[EditorPlugin]

  private var toolbarModeValue: EditorToolbarMode         = EditorToolbarMode.Ribbon
  private var dialogServiceValue: Option[DialogService]   = None
  private var editUrlValue: Option[String]                = None
  private var editLabelValue                              = "Edit"
  private var readonlyUrlValue: Option[String]            = None
  private var readonlyLabelValue                          = "Readonly"
  private var toolbarHost: Div                            = uninitialized
  private var fallbackHost: Div                           = uninitialized
  private var surfaceHost: Div                            = uninitialized
  private var lexicalAdapter: LexicalEditorAdapter | Null = null
  private var browserRendering                            = false

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      // Form registration can synchronously replace the constructor value with the model value.
      // It must happen before the dynamic Markdown fallback is built, otherwise hydration would
      // claim a different subtree than SSR produced.
      installControlObservers()
      registerWithForm()

      browserRendering = cursor.isBrowser
      addClass("jfx-editor-host")
      addClass("jfx-editor-host--markdown")
      setAttribute("name", name)
      setAttribute("role", "group")
      setAttribute("data-jfx-editor-format", "markdown")
      setAttribute("data-jfx-editor-loading", cursor.isBrowser.toString)

      div {
        classes = Seq("jfx-editor")

        div {
          classes = Seq("jfx-editor__shell")

          toolbarHost = div {
            classes = Seq("jfx-editor__toolbar")
            setDslAttribute("aria-label", "Editor toolbar")
            style { display = "none" }
          }

          div {
            classes = Seq("jfx-editor__markdown-actions")
            dynamic(editableProperty.map[AbstractComponent] { editable =>
              if (editable)
                new MarkdownModeLink(
                  readonlyUrlValue.getOrElse(modeUrl("readonly")),
                  readonlyLabelValue,
                  readonly = true
                )
              else
                new MarkdownModeLink(
                  editUrlValue.getOrElse(modeUrl("editable")),
                  editLabelValue,
                  readonly = false
                )
            })
          }

          div {
            classes = Seq("jfx-editor__surface-wrap")

            fallbackHost = div {
              classes = Seq("jfx-editor__fallback")
              dynamic(editableProperty.map[AbstractComponent] { editable =>
                if (editable)
                  new MarkdownTextArea(
                    name,
                    valueProperty,
                    placeholderProperty,
                    publishMarkdown,
                    updateFocus
                  )
                else new MarkdownReadonly(valueProperty)
              })
            }

            surfaceHost = div {
              classes =
                Seq("jfx-editor__surface", "lexical-editor-container", "lexical-editor-input")
              setDslAttribute("role", "textbox")
              setDslAttribute("aria-multiline", "true")
              setDslAttribute("contenteditable", editableProperty.get.toString)
              setDslAttribute("aria-readonly", (!editableProperty.get).toString)
              setDslAttribute("spellcheck", "true")
              style {
                display = "none"
                opacity = "0"
              }
            }

            div {
              classes = Seq("jfx-editor__placeholder")
              style {
                display = valueProperty.flatMap { value =>
                  placeholderProperty.map { placeholder =>
                    if (value.trim.isEmpty && placeholder.trim.nonEmpty) "" else "none"
                  }
                }
              }
              when(placeholderProperty.map(_.trim.nonEmpty)) {
                text(placeholderProperty.map(_.trim)) {}
              }
            }
          }
        }
      }
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser && editableProperty.get) mountLexical()

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  private[editor] def registerPlugin(plugin: EditorPlugin): Unit =
    if (!plugins.exists(_.name == plugin.name)) plugins += plugin

  private[editor] def toolbarMode: EditorToolbarMode = toolbarModeValue

  private[editor] def toolbarMode_=(mode: EditorToolbarMode): Unit =
    toolbarModeValue = Option(mode).getOrElse(EditorToolbarMode.Ribbon)

  private[editor] def dialogService: Option[DialogService] = dialogServiceValue

  private[editor] def dialogService_=(service: DialogService): Unit =
    dialogServiceValue = Option(service)

  private[editor] def editUrl: Option[String] = editUrlValue

  private[editor] def editUrl_=(value: String): Unit =
    editUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def editLabel: String = editLabelValue

  private[editor] def editLabel_=(value: String): Unit =
    editLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Edit")

  private[editor] def readonlyUrl: Option[String] = readonlyUrlValue

  private[editor] def readonlyUrl_=(value: String): Unit =
    readonlyUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def readonlyLabel: String = readonlyLabelValue

  private[editor] def readonlyLabel_=(value: String): Unit =
    readonlyLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Readonly")

  private def modeUrl(mode: String): String = {
    val parameter = s"$name.editor"
    UrlScope
      .current(using this)
      .map(scope => withQueryParameter(scope.url, parameter, mode))
      .getOrElse(s"?$parameter=$mode")
  }

  private def withQueryParameter(url: String, name: String, value: String): String = {
    val hashIndex           = url.indexOf('#')
    val (withoutHash, hash) =
      if (hashIndex >= 0) url.splitAt(hashIndex) else (url, "")
    val queryIndex    = withoutHash.indexOf('?')
    val (path, query) =
      if (queryIndex >= 0)
        (withoutHash.substring(0, queryIndex), withoutHash.substring(queryIndex + 1))
      else (withoutHash, "")
    val retained = query
      .split('&')
      .iterator
      .filter(_.nonEmpty)
      .filterNot(entry => entry.takeWhile(_ != '=') == name)
      .toSeq
    val nextQuery = (retained :+ s"$name=$value").mkString("&")
    s"$path?$nextQuery$hash"
  }

  private def installControlObservers(): Unit = {
    addDisposable(valueProperty.observe { _ => validate() })
    addDisposable(valueProperty.observeWithoutInitial(syncExternalValue))
    addDisposable(validators.observe(_ => validate()))
    addDisposable(dirtyProperty.observe(_ => validate()))
    addDisposable(editableProperty.observe { editable =>
      setAttribute("aria-disabled", (!editable).toString)
      updateEditable(editable)
    })
    addDisposable(Disposable(destroyEditor()))
  }

  private def registerWithForm(): Unit =
    if (!standalone) {
      val controller = FormContext
        .inject(using this)
        .getOrElse(
          throw new IllegalStateException(s"Editor '$name' requires a Form or FieldSet context.")
        )
      controller.register(this)
      addDisposable(() => controller.unregister(this))
    }

  private def mountLexical(): Unit =
    if (lexicalAdapter == null)
      for {
        surface <- domElement[HTMLDivElement](surfaceHost)
        toolbar <- domElement[HTMLElement](toolbarHost)
      } {
        val adapter = new LexicalEditorAdapter(
          name = name,
          owner = this,
          surface = surface,
          toolbar = toolbar,
          plugins = plugins.toSeq,
          toolbarMode = toolbarModeValue,
          configuredDialogService = dialogServiceValue.orElse(
            Editor.DialogServiceContext.inject(using this)
          ),
          onMarkdownChanged = publishMarkdown,
          onFocusChanged = updateFocus
        )
        lexicalAdapter = adapter
        adapter.mount(valueProperty.get, editableProperty.get)
        syncPresentation(editableProperty.get)
        setAttribute("data-jfx-editor-loading", "false")
      }

  private def updateEditable(editable: Boolean): Unit = {
    if (
      browserRendering && editable && lexicalAdapter == null && Option(surfaceHost).exists(
        _.isBound
      )
    )
      mountLexical()

    Option(lexicalAdapter).foreach(_.setEditable(editable))
    syncPresentation(editable)
  }

  private def syncPresentation(editable: Boolean): Unit = {
    val enhanced = lexicalAdapter != null && editable
    Option(fallbackHost).foreach(_.setStyle("display", if (enhanced) "none" else ""))
    Option(surfaceHost).foreach { surface =>
      surface.setStyle("display", if (enhanced) "" else "none")
      surface.setStyle("opacity", if (enhanced) "1" else "0")
    }
    if (!enhanced) Option(toolbarHost).foreach(_.setStyle("display", "none"))
  }

  private def publishMarkdown(markdown: String): Unit = {
    dirtyProperty.set(true)
    if (valueProperty.get != markdown) valueProperty.set(markdown)
  }

  private def syncExternalValue(value: String): Unit =
    Option(lexicalAdapter).foreach(_.syncMarkdown(value))

  private def updateFocus(focused: Boolean): Unit = {
    focusedProperty.set(focused)
    if (!focused) validate()
  }

  private def destroyEditor(): Unit = {
    Option(lexicalAdapter).foreach(_.close())
    lexicalAdapter = null
    focusedProperty.set(false)
  }

  private def domElement[A <: HTMLElement](component: AbstractComponent): Option[A] =
    Option(component).flatMap { current =>
      current.host match {
        case domHost: DomHostElement => Some(domHost.node.asInstanceOf[A])
        case _                       => None
      }
    }

}

object Editor {
  val DialogServiceContext: Context[DialogService] =
    Context.create[DialogService]("jfx-editor-dialog-service")

  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  def editor(
      name: String,
      standalone: Boolean = false
  )(body: Editor ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Editor =
    DslLayer.child(new Editor(name, standalone, body)) {}

  def value(using editor: Editor): String = editor.valueProperty.get

  def value_=(nextValue: String)(using editor: Editor): Unit =
    editor.valueProperty.set(Option(nextValue).getOrElse(""))

  def valueProperty(using editor: Editor): Property[String] = editor.valueProperty

  def errorsProperty(using editor: Editor): jfx.core.state.ListProperty[String] = editor.errors

  def toolbarMode(using editor: Editor): EditorToolbarMode = editor.toolbarMode

  def toolbarMode_=(mode: EditorToolbarMode)(using editor: Editor): Unit =
    editor.toolbarMode = mode

  def ribbonToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Ribbon

  def menuToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Menu

  def floatingToolbar()(using editor: Editor): Unit = editor.toolbarMode =
    EditorToolbarMode.Floating

  def dialogService(using editor: Editor): Option[DialogService] = editor.dialogService

  def dialogService_=(service: DialogService)(using editor: Editor): Unit =
    editor.dialogService = service

  def editUrl(using editor: Editor): Option[String] = editor.editUrl

  def editUrl_=(value: String)(using editor: Editor): Unit = editor.editUrl = value

  def editLabel(using editor: Editor): String = editor.editLabel

  def editLabel_=(value: String)(using editor: Editor): Unit = editor.editLabel = value

  def readonlyUrl(using editor: Editor): Option[String] = editor.readonlyUrl

  def readonlyUrl_=(value: String)(using editor: Editor): Unit = editor.readonlyUrl = value

  def readonlyLabel(using editor: Editor): String = editor.readonlyLabel

  def readonlyLabel_=(value: String)(using editor: Editor): Unit = editor.readonlyLabel = value
}
