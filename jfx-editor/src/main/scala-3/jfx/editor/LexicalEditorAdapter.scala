package jfx.editor

import jfx.core.component.AbstractComponent
import jfx.editor.plugins.{DefaultDialogService, EditorPlugin}
import lexical.*
import org.scalajs.dom
import org.scalajs.dom.{HTMLDivElement, HTMLElement}

import scala.collection.mutable
import scala.scalajs.js
import scala.util.control.NonFatal

/** Owns the complete browser-side Lexical lifecycle for one [[Editor]].
  *
  * The adapter has no form or JFX control state. Markdown enters and leaves through callbacks;
  * Lexical editor state never crosses this boundary.
  */
private[editor] final class LexicalEditorAdapter(
    name: String,
    owner: AbstractComponent,
    surface: HTMLDivElement,
    toolbar: HTMLElement,
    plugins: Seq[EditorPlugin],
    toolbarMode: EditorToolbarMode,
    configuredDialogService: Option[DialogService],
    onMarkdownChanged: String => Unit,
    onFocusChanged: Boolean => Unit
) extends AutoCloseable {

  private val registrations = mutable.ArrayBuffer.empty[js.Function0[Unit]]

  private var editor: LexicalEditor | Null                    = null
  private var ownedDialogService: DefaultDialogService | Null = null
  private var updateUnregister: js.Function0[Unit] | Null     = null
  private var floatingUnregister: js.Function0[Unit] | Null   = null
  private var lastMarkdown: String | Null                     = null
  private var applyingMarkdown                                = false
  private var toolbarRendered                                 = false
  private var closed                                          = false

  def mount(markdown: String, editable: Boolean): Unit =
    if (editor == null && !closed) {
      val nodes   = defaultNodes()
      val modules = collectModules()
      val config  = js.Dynamic
        .literal(
          namespace = name,
          theme = defaultTheme(),
          nodes = nodes,
          editable = editable,
          onError = (error: js.Error) => dom.console.error(error)
        )
        .asInstanceOf[CreateEditorArgs]
      val mountedEditor = Lexical.createEditor(config)
      editor = mountedEditor
      mountedEditor.setDialogService(resolveDialogService())
      mountedEditor.setRootElement(surface)

      register(LexicalRichText.registerRichText(mountedEditor))
      if (!modules.exists(_.isInstanceOf[HistoryModule]))
        register(
          LexicalHistory.registerHistory(
            mountedEditor,
            LexicalHistory.createEmptyHistoryState(),
            300
          )
        )
      if (hasTableNodes(nodes)) register(LexicalTable.registerTablePlugin(mountedEditor))
      if (hasListNodes(nodes)) register(LexicalList.registerList(mountedEditor))
      modules.distinct.foreach(module => register(module.register(mountedEditor)))
      registerDecoratorMounting(mountedEditor)

      applyMarkdown(mountedEditor, markdown)
      updateUnregister = mountedEditor.registerUpdateListener { (_: js.Dynamic) =>
        if (!applyingMarkdown) publishMarkdown(mountedEditor)
      }
      plugins.foreach(plugin => register(plugin.install(mountedEditor)))
      registerFocusListeners()

      renderToolbar(mountedEditor)
      setEditable(editable)
    }

  def syncMarkdown(markdown: String): Unit =
    Option(editor).foreach { mountedEditor =>
      val normalized = normalize(markdown)
      if (lastMarkdown != normalized) applyMarkdown(mountedEditor, normalized)
    }

  def setEditable(editable: Boolean): Unit =
    Option(editor).foreach { mountedEditor =>
      mountedEditor.setEditable(editable)
      surface.setAttribute("contenteditable", editable.toString)
      surface.setAttribute("aria-readonly", (!editable).toString)
      if (editable) surface.classList.remove("lexical-read-only")
      else {
        surface.classList.add("lexical-read-only")
        mountedEditor.blur()
        Option(floatingUnregister).foreach(safely)
        floatingUnregister = null
      }
      if (editable) renderToolbar(mountedEditor)
      toolbar.style.display = toolbarDisplay(editable)
    }

  override def close(): Unit =
    if (!closed) {
      closed = true
      onFocusChanged(false)

      Option(updateUnregister).foreach(safely)
      updateUnregister = null
      Option(floatingUnregister).foreach(safely)
      floatingUnregister = null

      Option(editor).foreach { mountedEditor =>
        try mountedEditor.setRootElement(null)
        catch { case NonFatal(_) => () }
      }
      editor = null
      registrations.reverseIterator.foreach(safely)
      registrations.clear()
      toolbarRendered = false
      Option(ownedDialogService).foreach(_.close())
      ownedDialogService = null
      toolbar.replaceChildren()
      toolbar.style.display = "none"
    }

  private def register(unregister: js.Function0[Unit] | Null): Unit =
    Option(unregister).foreach(registrations += _)

  private def registerFocusListeners(): Unit = {
    val focusIn: js.Function1[dom.Event, Unit]  = _ => onFocusChanged(true)
    val focusOut: js.Function1[dom.Event, Unit] = _ => onFocusChanged(false)
    surface.addEventListener("focusin", focusIn)
    surface.addEventListener("focusout", focusOut)
    register(() => surface.removeEventListener("focusin", focusIn))
    register(() => surface.removeEventListener("focusout", focusOut))
  }

  private def registerDecoratorMounting(mountedEditor: LexicalEditor): Unit =
    register(
      mountedEditor.registerDecoratorListener { decorators =>
        val values = decorators.asInstanceOf[js.Dictionary[dom.Node]]
        js.Object.entries(values).foreach { entry =>
          val container = mountedEditor.getElementByKey(entry._1)
          val decorator = entry._2
          if (container != null && !container.contains(decorator))
            container.replaceChildren(decorator)
        }
      }
    )

  private def resolveDialogService(): DialogService =
    configuredDialogService.getOrElse {
      val service = new DefaultDialogService(owner)
      ownedDialogService = service
      service
    }

  private def renderToolbar(mountedEditor: LexicalEditor): Unit = {
    val elements = collectToolbarElements()
    if (mountedEditor.isEditable() && elements.nonEmpty)
      toolbarMode match {
        case EditorToolbarMode.Floating =>
          if (floatingUnregister == null)
            floatingUnregister =
              new FloatingToolbarManager(mountedEditor, collectModules()).register()
        case mode =>
          if (!toolbarRendered) {
            val layout =
              if (mode == EditorToolbarMode.Menu) ToolbarLayout.Menu else ToolbarLayout.Ribbon
            val renderer: ToolbarRenderer =
              if (mode == EditorToolbarMode.Menu) new MenuRenderer() else new RibbonRenderer()
            new ToolbarManager(
              mountedEditor,
              new ToolbarRegistry(elements.toList, layout),
              renderer
            ).createToolbar(toolbar)
            toolbarRendered = true
          }
      }
  }

  private def toolbarDisplay(editable: Boolean): String =
    if (
      editable && plugins.exists(_.toolbarElements.nonEmpty) &&
      toolbarMode != EditorToolbarMode.Floating
    ) ""
    else "none"

  private def publishMarkdown(mountedEditor: LexicalEditor): Unit = {
    val markdown =
      mountedEditor.read(() => LexicalMarkdownCodec.toMarkdown(LexicalMarkdownCodec.transformers))
    if (lastMarkdown != markdown) {
      lastMarkdown = markdown
      onMarkdownChanged(markdown)
    }
  }

  private def applyMarkdown(mountedEditor: LexicalEditor, markdown: String): Unit =
    try {
      val normalized = normalize(markdown)
      applyingMarkdown = true
      mountedEditor.update(
        () => LexicalMarkdownCodec.fromMarkdown(normalized, LexicalMarkdownCodec.transformers),
        js.Dynamic.literal(discrete = true).asInstanceOf[EditorUpdateOptions]
      )
      lastMarkdown = normalized
    } catch {
      case NonFatal(error) => dom.console.error("Could not import editor Markdown", error)
    } finally applyingMarkdown = false

  private def normalize(markdown: String): String = Option(markdown).getOrElse("")

  private def hasTableNodes(nodes: js.Array[js.Any]): Boolean =
    Seq(LexicalTable.TableNode, LexicalTable.TableRowNode, LexicalTable.TableCellNode)
      .forall(required => nodes.exists(_ == required))

  private def hasListNodes(nodes: js.Array[js.Any]): Boolean =
    Seq(LexicalList.ListNode, LexicalList.ListItemNode)
      .forall(required => nodes.exists(_ == required))

  private def defaultNodes(): js.Array[js.Any] =
    js.Array(
      (
        Seq(
          LexicalRichText.HeadingNode,
          LexicalRichText.QuoteNode,
          HorizontalRuleNode,
          LexicalList.ListNode,
          LexicalList.ListItemNode,
          LexicalLink.LinkNode,
          LexicalCode.CodeNode,
          js.constructorOf[ImageNode],
          js.constructorOf[HorizontalRuleNode],
          js.constructorOf[lexical.codemirror.CodeMirrorNode],
          LexicalTable.TableNode,
          LexicalTable.TableRowNode,
          LexicalTable.TableCellNode
        ) ++ plugins.iterator.flatMap(_.nodes)
      ).distinct*
    )

  private def collectToolbarElements(): Seq[ToolbarElement] =
    plugins.iterator.flatMap(_.toolbarElements).toSeq

  private def collectModules(): Seq[EditorModule] =
    (
      plugins.iterator.flatMap(_.modules).toSeq ++
        collectToolbarElements().collect { case module: EditorModule => module }
    ).distinct

  private def defaultTheme(): EditorTheme =
    new EditorThemeBuilder()
      .withParagraph(EditorStyles.paragraph)
      .withQuote(EditorStyles.quote)
      .withHeading(1, EditorStyles.heading(1))
      .withHeading(2, EditorStyles.heading(2))
      .withHeading(3, EditorStyles.heading(3))
      .withHeading(4, EditorStyles.heading(4))
      .withHeading(5, EditorStyles.heading(5))
      .withHeading(6, EditorStyles.heading(6))
      .withHorizontalRule(EditorStyles.horizontalRule)
      .withList("ul", EditorStyles.unorderedList)
      .withList("ol", EditorStyles.orderedList)
      .withList("listitem", EditorStyles.listItem)
      .withTextBold(EditorStyles.bold)
      .withTextItalic(EditorStyles.italic)
      .withTextUnderline(EditorStyles.underline)
      .withTextStrikethrough(EditorStyles.strikethrough)
      .withCode(EditorStyles.code)
      .build()

  private def safely(unregister: js.Function0[Unit]): Unit =
    try unregister()
    catch { case NonFatal(_) => () }
}
