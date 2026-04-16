package jfx.form.editor.plugins

import lexical.{BaseSelection, Lexical, LexicalEditor, LexicalLink, LinkModule, NodeSelection, RangeSelection, ToolbarElement, getDialogService, getSelectionWrapper}
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

class LinkPlugin extends AbstractEditorPlugin("link-plugin") {

  override val name: String = "link"

  var dialogTitle: String = "Link einfuegen"
  var urlLabel: String = "URL"
  var urlPlaceholder: String = "https://example.com"
  var defaultUrl: String = ""
  var buildDialogContent: LinkDialogContext => HTMLElement = LinkPlugin.defaultBuildDialogContent
  var confirmDialog: (LinkDialogContext, HTMLElement) => Unit = LinkPlugin.defaultConfirmDialog

  private val linkDialogModule = new LinkDialogModule()

  override val toolbarElements: Seq[ToolbarElement] =
    Seq(linkDialogModule)

  override val modules: Seq[lexical.EditorModule] =
    Seq(linkDialogModule)

  override val nodes: Seq[js.Any] =
    Seq(LexicalLink.LinkNode)

  private final class LinkDialogModule extends LinkModule {
    override def execute(editor: LexicalEditor): Unit =
      openLinkEditor(editor)
  }

  private def openLinkEditor(editor: LexicalEditor): Unit = {
    val context =
      LinkDialogContext(
        editor = editor,
        selection = editor.read(() => Lexical.$getSelection()),
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

  private def currentLinkUrl(editor: LexicalEditor): String =
    editor.getEditorState().read(() => {
      val nodes = editor.getSelectionWrapper().getNodes
      nodes.find(node => LexicalLink.$isLinkNode(node)).map { node =>
        node.asInstanceOf[js.Dynamic].getURL().asInstanceOf[String]
      }.getOrElse(defaultUrl)
    }).asInstanceOf[String]
}

object LinkPlugin {

  def defaultBuildDialogContent(context: LinkDialogContext): HTMLElement = {
    val content = document.createElement("div").asInstanceOf[HTMLElement]
    content.className = "link-plugin-dialog"
    content.style.display = "flex"
    content.style.setProperty("flex-direction", "column")
    content.style.setProperty("gap", "10px")

    val intro = document.createElement("div").asInstanceOf[HTMLElement]
    intro.textContent = "Use this dialog to edit or insert a link."

    val label = document.createElement("label").asInstanceOf[HTMLElement]
    label.textContent = context.urlLabel

    val input = document.createElement("input").asInstanceOf[HTMLInputElement]
    input.`type` = "url"
    input.id = "link-url-input"
    input.placeholder = context.urlPlaceholder
    input.value = context.currentUrl
    input.style.width = "100%"

    content.appendChild(intro)
    content.appendChild(label)
    content.appendChild(input)
    content
  }

  def defaultConfirmDialog(context: LinkDialogContext, content: HTMLElement): Unit = {
    val urlInput = content.querySelector("#link-url-input").asInstanceOf[HTMLInputElement]
    val url = Option(urlInput).map(_.value.trim).getOrElse("")
    val finalUrl = if (url.isEmpty) null else url
    context.editor.update(
      () => {
        if (context.selection != null) {
          Lexical.$setSelection(context.selection.clone().asInstanceOf[RangeSelection | NodeSelection])
        }
        LexicalLink.$toggleLink(finalUrl)
      },
      js.Dynamic.literal().asInstanceOf[lexical.EditorUpdateOptions]
    )
  }

  def linkPlugin(init: LinkPlugin ?=> Unit = {}): LinkPlugin =
    PluginFactory.build(new LinkPlugin())(init)
}
