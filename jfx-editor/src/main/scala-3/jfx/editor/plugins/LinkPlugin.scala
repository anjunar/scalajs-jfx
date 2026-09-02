package jfx.editor.plugins

import jfx.editor.Editor
import lexical.{
  BaseSelection,
  Lexical,
  LexicalEditor,
  LexicalLink,
  LinkModule,
  NodeSelection,
  RangeSelection,
  ToolbarElement,
  getDialogService,
  getSelectionWrapper
}
import org.scalajs.dom.{HTMLElement, HTMLInputElement, document}

import scala.scalajs.js

final case class LinkDialogContext(
    editor: LexicalEditor,
    selection: BaseSelection | Null,
    currentUrl: String,
    dialogTitle: String,
    urlLabel: String,
    urlPlaceholder: String
)

final class LinkPlugin extends EditorPlugin {
  override val name: String = "link"

  var dialogTitle: String                                     = "Insert link"
  var urlLabel: String                                        = "URL"
  var urlPlaceholder: String                                  = "https://example.com"
  var defaultUrl: String                                      = ""
  var buildDialogContent: LinkDialogContext => HTMLElement    = LinkPlugin.defaultBuildDialogContent
  var confirmDialog: (LinkDialogContext, HTMLElement) => Unit = LinkPlugin.defaultConfirmDialog

  private val linkDialogModule = new LinkDialogModule()

  override val toolbarElements: Seq[ToolbarElement] = Seq(linkDialogModule)
  override val modules: Seq[lexical.EditorModule]    = Seq(linkDialogModule)
  override val nodes: Seq[js.Any]                    = Seq(LexicalLink.LinkNode)

  private final class LinkDialogModule extends LinkModule {
    override def execute(editor: LexicalEditor): Unit = openLinkEditor(editor)
  }

  private def openLinkEditor(editor: LexicalEditor): Unit = {
    val context = LinkDialogContext(
      editor = editor,
      selection = currentSelection(editor),
      currentUrl = currentLinkUrl(editor),
      dialogTitle = dialogTitle,
      urlLabel = urlLabel,
      urlPlaceholder = urlPlaceholder
    )

    editor.getDialogService.show(
      dialogTitle,
      () => buildDialogContent(context),
      content => confirmDialog(context, content)
    )
  }

  private def currentSelection(editor: LexicalEditor): BaseSelection | Null =
    editor.read(() => {
      val selection = Lexical.$getSelection()
      if (selection != null) selection.clone() else null
    })

  private def currentLinkUrl(editor: LexicalEditor): String =
    editor
      .getEditorState()
      .read(() => {
        editor
          .getSelectionWrapper()
          .getNodes
          .find(node => LexicalLink.$isLinkNode(node))
          .map(_.asInstanceOf[js.Dynamic].getURL().asInstanceOf[String])
          .getOrElse(defaultUrl)
      })
      .asInstanceOf[String]
}

object LinkPlugin {
  def defaultBuildDialogContent(context: LinkDialogContext): HTMLElement = {
    val content = document.createElement("div").asInstanceOf[HTMLElement]
    content.className = "link-plugin-dialog"

    val intro = document.createElement("div").asInstanceOf[HTMLElement]
    intro.textContent = "Use this dialog to edit or insert a link."

    val label = document.createElement("label").asInstanceOf[HTMLElement]
    label.textContent = context.urlLabel
    label.setAttribute("for", "link-url-input")

    val input = document.createElement("input").asInstanceOf[HTMLInputElement]
    input.`type` = "url"
    input.id = "link-url-input"
    input.placeholder = context.urlPlaceholder
    input.value = context.currentUrl

    content.appendChild(intro)
    content.appendChild(label)
    content.appendChild(input)
    content
  }

  def defaultConfirmDialog(context: LinkDialogContext, content: HTMLElement): Unit = {
    val input = content.querySelector("#link-url-input").asInstanceOf[HTMLInputElement | Null]
    val url   = Option(input).map(_.value.trim).filter(_.nonEmpty).orNull

    context.editor.update(
      () => {
        if (context.selection != null)
          Lexical.$setSelection(
            context.selection.clone().asInstanceOf[RangeSelection | NodeSelection]
          )
        LexicalLink.$toggleLink(url)
      },
      js.Dynamic.literal().asInstanceOf[lexical.EditorUpdateOptions]
    )
  }

  def linkPlugin(body: LinkPlugin ?=> Unit = {})(using editor: Editor): LinkPlugin = {
    val plugin = new LinkPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
