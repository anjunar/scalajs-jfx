package jfx.editor

import lexical.*
import lexical.media.ImageReference
import org.scalajs.dom
import scala.collection.mutable
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.Thenable.Implicits.*
import scala.util.{Failure, Success, Try}

@js.native
@JSImport("@lexical/clipboard", JSImport.Namespace)
private object MediaClipboard extends js.Object {
  def $insertDataTransferForRichText(
      data: js.Dynamic,
      selection: BaseSelection,
      editor: LexicalEditor
  ): Unit                                              = js.native
  def caretFromPoint(x: Double, y: Double): js.Dynamic = js.native
}

/** Owns pending uploads and browser media interactions for one editor instance. */
private[editor] final class MediaCoordinator(
    editor: LexicalEditor,
    uploader: Option[MediaUploader],
    policy: MediaUrlPolicy,
    onStatus: MediaUploadStatus => Unit
) extends AutoCloseable {
  private given ExecutionContext         = scala.scalajs.concurrent.JSExecutionContext.queue
  private val pending                    = mutable.Set.empty[dom.AbortController]
  private val cleanup                    = mutable.ArrayBuffer.empty[() => Unit]
  private var generation                 = 0
  private var closed                     = false
  private var error: Option[String]      = None
  private var cancelResize: () => Unit   = () => ()
  private var draggedKey: Option[String] = None
  private final case class ImageMetadata(
      alt: String = "",
      title: Option[String] = None,
      width: Option[Int] = None
  )

  def available: Boolean                                 = uploader.nonEmpty
  private def failureMessage(failure: Throwable): String = failure match {
    case js.JavaScriptException(reason) =>
      val value = reason.asInstanceOf[js.Dynamic]
      if (value != null && js.typeOf(value.selectDynamic("message")) == "string")
        value.message.asInstanceOf[String]
      else String.valueOf(reason)
    case other => Option(other.getMessage).getOrElse("Image upload failed")
  }
  def report(message: String): Unit = { error = Some(message); publish() }
  private def publish(): Unit       = onStatus(MediaUploadStatus(pending.size, error))
  def invalidate(): Unit            = {
    generation += 1
    pending.toVector.foreach(_.abort())
    pending.clear()
    cancelResize()
    error = None
    publish()
  }
  override def close(): Unit = {
    closed = true
    invalidate()
    cleanup.reverseIterator.foreach(_())
    cleanup.clear()
  }

  def upload(file: dom.File, signal: dom.AbortSignal): Future[UploadedMediaReference] = {
    if (!editor.isEditable() || closed)
      return Future.failed(new IllegalStateException("Editor is no longer editable"))
    if (signal.aborted)
      return Future.failed(new IllegalStateException("Image upload was cancelled"))
    if (uploader.isEmpty)
      return Future.failed(new IllegalStateException("No media uploader is configured"))
    val service                                  = uploader.get
    val epoch                                    = generation
    val controller                               = new dom.AbortController()
    val completion                               = Promise[UploadedMediaReference]()
    val cancel: js.Function1[dom.Event, Unit]    = _ => controller.abort()
    val cancelled: js.Function1[dom.Event, Unit] = _ => {
      completion.tryFailure(new IllegalStateException("Image upload was cancelled"))
      ()
    }
    controller.signal.addEventListener("abort", cancelled)
    signal.addEventListener("abort", cancel)
    pending += controller
    error = None
    publish()
    Future
      .fromTry(Try(service.upload(file, controller.signal)))
      .flatten
      .map { uploaded =>
        if (closed || epoch != generation || controller.signal.aborted || !editor.isEditable())
          throw new IllegalStateException("Image upload was cancelled")
        require(
          uploaded != null && uploaded.mediaId != null && uploaded.mediaId.trim.nonEmpty,
          "Upload did not return a media ID"
        )
        val resolved = MediaUrlPolicy
          .checked(policy, uploaded.src)
          .getOrElse(
            throw new IllegalArgumentException(
              "Upload did not return an allowed internal media URL"
            )
          )
        require(
          resolved.mediaId.forall(_ == uploaded.mediaId),
          "Upload media ID does not match its URL"
        )
        uploaded.copy(src = resolved.src)
      }
      .onComplete(outcome => { completion.tryComplete(outcome); () })
    val result = completion.future
    result.onComplete { outcome =>
      signal.removeEventListener("abort", cancel)
      controller.signal.removeEventListener("abort", cancelled)
      pending -= controller
      if (!closed && epoch == generation) {
        outcome.failed.foreach(e => if (!controller.signal.aborted) error = Some(failureMessage(e)))
        publish()
      }
    }
    result
  }

  final class Target private[MediaCoordinator] (selection: BaseSelection | Null, epoch: Int) {
    def insert(images: Seq[ImageReference]): Unit = {
      require(
        !closed && generation == epoch && editor.isEditable(),
        "The document changed during upload"
      )
      images.foreach(_.validated)
      // Check before update: Lexical forwards errors inside update to onError instead of
      // rejecting the upload operation, which would otherwise falsely report success.
      editor.read(() => {
        if (selection != null)
          require(selection.getNodes().forall(_.isAttached()), "The insertion position was removed")
      })
      editor.update(
        () => {
          if (selection != null) Lexical.$setSelection(selection.clone())
          else Lexical.$getRoot().selectEnd()
          Lexical.$insertNodes(js.Array(images.map(image => new ImageNode(image.validated))*))
        },
        js.Dynamic.literal(discrete = true, tag = "history-push").asInstanceOf[EditorUpdateOptions]
      )
    }
    def replace(key: String, image: ImageReference): Unit = {
      require(
        !closed && generation == epoch && editor.isEditable(),
        "The document changed during upload"
      )
      image.validated
      editor.read(() => {
        val node = Lexical.$getNodeByKey(key)
        require(
          node != null && node.isAttached() && node.getType() == "image",
          "The image was removed"
        )
      })
      editor.update(
        () => Lexical.$getNodeByKey(key).asInstanceOf[ImageNode].setReference(image),
        js.Dynamic.literal(discrete = true, tag = "history-push").asInstanceOf[EditorUpdateOptions]
      )
    }
  }
  def target(): Target = editor.read(() => targetInUpdate())

  // Commands already run in a writable update. editor.read here would flush/freeze that update
  // before the remaining clipboard nodes can be inserted.
  private def targetInUpdate(): Target = {
    val selected = Lexical.$getSelection()
    new Target(if (selected == null) null else selected.clone(), generation)
  }

  private def targetAfterUpdate(): Future[Target] = {
    val result                        = Promise[Target]()
    val epoch                         = generation
    val committed: js.Function0[Unit] = () => {
      result.tryComplete(Try {
        require(
          !closed && epoch == generation && editor.isEditable(),
          "The document changed during paste"
        )
        target()
      })
      ()
    }
    // Normalization can merge adjacent text nodes. Capture the corrected selection after
    // commit, never a transient node key from the middle of a paste/drop transaction.
    editor.update(
      () => (),
      js.Dynamic.literal(discrete = true, onUpdate = committed).asInstanceOf[EditorUpdateOptions]
    )
    result.future
  }

  private def uploadFiles(
      files: Seq[dom.File],
      destination: Future[Target],
      metadata: Seq[ImageMetadata] = Seq.empty
  ): Unit = {
    val abort = new dom.AbortController()
    val epoch = generation
    // Parallel storage requests, one ordered insertion transaction.
    Future
      .sequence(files.map(file => upload(file, abort.signal)))
      .zip(destination)
      .map { (uploaded, target) =>
        target.insert(uploaded.zipWithIndex.map { (ref, index) =>
          val data = metadata.lift(index).getOrElse(ImageMetadata())
          ImageReference(ref.src, data.alt, data.title, data.width, Some(ref.mediaId))
        })
      }
      .failed
      .foreach(e => if (!closed && epoch == generation) report(failureMessage(e)))
  }

  private def eventFiles(data: js.Dynamic): Seq[dom.File] = {
    val files  = data.files
    val listed =
      if (files == null || js.isUndefined(files)) Seq.empty
      else
        (0 until files.length.asInstanceOf[Int])
          .map(index => files.item(index).asInstanceOf[dom.File])
          .filter(file => file != null && file.`type`.startsWith("image/"))
    if (listed.nonEmpty) listed
    else {
      val items = data.items
      if (items == null || js.isUndefined(items)) Seq.empty
      else
        (0 until items.length.asInstanceOf[Int]).flatMap { index =>
          val item = items.selectDynamic(index.toString)
          if (item.kind.asInstanceOf[String] == "file")
            Option(item.getAsFile().asInstanceOf[dom.File])
              .filter(_.`type`.startsWith("image/"))
          else None
        }
    }
  }

  private def insertClipboard(data: js.Dynamic, html: String, serialized: String): Unit = {
    val transfer = js.Dynamic.literal(
      types = js.Array("application/x-lexical-editor", "text/html", "text/plain", "text/uri-list"),
      getData = (
          (format: String) =>
            if (format == "text/html") html
            else if (format == "application/x-lexical-editor") serialized
            else data.getData(format).asInstanceOf[String]
      ): js.Function1[String, String]
    )
    val selection = Lexical.$getSelection()
    if (selection != null)
      MediaClipboard.$insertDataTransferForRichText(transfer, selection, editor)
  }

  private def paste(data: js.Dynamic): Boolean = {
    val files       = eventFiles(data)
    val html        = data.getData("text/html").asInstanceOf[String]
    val lexicalJson = data.getData("application/x-lexical-editor").asInstanceOf[String]
    val document    = new dom.DOMParser().parseFromString(html, dom.MIMEType.`text/html`)
    val images      = document.querySelectorAll("img")
    if (files.isEmpty && images.length == 0 && lexicalJson.isEmpty) return false
    val embedded     = mutable.ArrayBuffer.empty[(String, ImageMetadata)]
    val fileMetadata = mutable.ArrayBuffer.empty[ImageMetadata]
    val serialized   =
      if (lexicalJson.isEmpty) ""
      else
        Try {
          val json = js.JSON.parse(lexicalJson).asInstanceOf[js.Dynamic]
          def sanitize(nodes: js.Array[js.Dynamic]): js.Array[js.Dynamic] = nodes.flatMap { node =>
            if (node.selectDynamic("type").asInstanceOf[String] == "image") {
              val src = node.src.asInstanceOf[String]
              if (src != null && (src.startsWith("data:image/") || src.startsWith("blob:"))) {
                if (files.isEmpty)
                  embedded += src -> ImageMetadata(
                    Option(node.altText.asInstanceOf[String]).getOrElse("")
                  )
                js.Array[js.Dynamic]()
              } else
                MediaUrlPolicy.checked(policy, src) match {
                  case Some(reference) =>
                    node.src = reference.src
                    // Validate the complete serialized payload, including width and metadata.
                    val parsed  = ImageNode.importJSON(node)
                    val checked = MediaUrlPolicy
                      .image(policy, parsed.reference)
                      .getOrElse(
                        throw new IllegalArgumentException(
                          "Clipboard media ID does not match its URL"
                        )
                      )
                    parsed.setReference(checked)
                    js.Array(parsed.exportJSON())
                  case None =>
                    report("Only internal image URLs are allowed"); js.Array[js.Dynamic]()
                }
            } else {
              if (!js.isUndefined(node.children))
                node.children = sanitize(node.children.asInstanceOf[js.Array[js.Dynamic]])
              js.Array(node)
            }
          }
          json.nodes = sanitize(json.nodes.asInstanceOf[js.Array[js.Dynamic]])
          js.JSON.stringify(json)
        }.recover { case failure => report(failureMessage(failure)); "" }.get
    images.foreach { image =>
      val src      = Option(image.getAttribute("src")).getOrElse("")
      val metadata = ImageMetadata(
        Option(image.getAttribute("alt")).getOrElse(""),
        Option(image.getAttribute("title")),
        Option(image.getAttribute("width")).flatMap(_.toIntOption).filter(_ > 0)
      )
      if (src.startsWith("data:image/") || src.startsWith("blob:")) {
        if (files.isEmpty) embedded += src -> metadata else fileMetadata += metadata
        image.parentNode.removeChild(image)
      } else if (MediaUrlPolicy.checked(policy, src).isEmpty) {
        image.parentNode.removeChild(image)
        report("Only internal image URLs are allowed")
      }
    }
    insertClipboard(data, document.querySelector("body").innerHTML, serialized)
    // Capture the remaining caret after replacing the selected text with clipboard text.
    // The old selection may refer to text nodes that the paste has just removed.
    if (files.isEmpty && embedded.isEmpty) return true
    val destination = targetAfterUpdate()
    if (files.nonEmpty) uploadFiles(files, destination, fileMetadata.toSeq)
    else if (embedded.nonEmpty) {
      val epoch   = generation
      val sources = embedded.toSeq.distinctBy(_._1)
      Future
        .sequence(sources.map { (src, _) =>
          dom.fetch(src).toFuture.flatMap(_.blob().toFuture).map { blob =>
            require(blob.`type`.startsWith("image/"), "Clipboard did not contain an image")
            new dom.File(
              js.Array(blob),
              "clipboard-image",
              js.Dynamic.literal(`type` = blob.`type`).asInstanceOf[dom.FilePropertyBag]
            )
          }
        })
        .onComplete {
          case Success(selected) =>
            if (!closed && generation == epoch)
              uploadFiles(selected, destination, sources.map(_._2))
          case Failure(failure) =>
            if (!closed && generation == epoch) report(failureMessage(failure))
        }
    }
    true
  }

  def install(): Unit = {
    val runtime      = Lexical.asInstanceOf[js.Dynamic]
    val pasteCommand =
      runtime.selectDynamic("PASTE_COMMAND").asInstanceOf[LexicalCommand[dom.ClipboardEvent]]
    val unregister = editor.registerCommand(
      pasteCommand,
      (event: dom.ClipboardEvent, _: LexicalEditor) => {
        if (!editor.isEditable() || event.clipboardData == null) false
        else {
          val handled = paste(event.clipboardData.asInstanceOf[js.Dynamic])
          if (handled) event.preventDefault()
          handled
        }
      },
      4
    )
    cleanup += (() => unregister())
    // Enforce application-specific media routes on HTML imports and direct commands as well.
    val transform = editor.registerNodeTransform(
      js.constructorOf[ImageNode].asInstanceOf[js.Dynamic],
      (node: ImageNode) => {
        MediaUrlPolicy.image(policy, node.reference) match {
          case Some(image) => if (image != node.reference) node.setReference(image)
          case None => node.remove(false); report("Only configured internal media URLs are allowed")
        }
      }
    )
    cleanup += (() => transform())

    val root                                                            = editor.getRootElement()
    def listen[A <: dom.Event](name: String)(callback: A => Unit): Unit = {
      val listener: js.Function1[A, Unit] = callback
      root.addEventListener(name, listener, true)
      cleanup += (() => root.removeEventListener(name, listener, true))
    }
    listen[dom.DragEvent]("dragstart") { event =>
      draggedKey = imageKey(event.target)
      draggedKey.foreach { key =>
        if (event.dataTransfer != null) event.dataTransfer.setData("application/x-jfx-image", key)
      }
    }
    listen[dom.DragEvent]("dragend")(_ => draggedKey = None)
    listen[dom.DragEvent]("dragover") { event =>
      if (
        editor.isEditable() && event.dataTransfer != null &&
        (draggedKey.nonEmpty || eventFiles(event.dataTransfer.asInstanceOf[js.Dynamic]).nonEmpty ||
          event.dataTransfer.types.asInstanceOf[js.Array[String]].contains("Files"))
      ) event.preventDefault()
    }
    listen[dom.DragEvent]("drop") { event =>
      if (editor.isEditable() && event.dataTransfer != null) {
        val files       = eventFiles(event.dataTransfer.asInstanceOf[js.Dynamic])
        val data        = event.dataTransfer.asInstanceOf[js.Dynamic]
        val mediaMarkup =
          data.getData("text/html").asInstanceOf[String].toLowerCase.contains("<img") ||
            data.getData("application/x-lexical-editor").asInstanceOf[String].contains("\"image\"")
        if (files.nonEmpty || draggedKey.nonEmpty || mediaMarkup) {
          event.preventDefault(); event.stopPropagation()
          editor.update(
            () => {
              val document = dom.document.asInstanceOf[js.Dynamic]
              val caret    =
                if (
                  js.typeOf(document.caretRangeFromPoint) == "function" ||
                  js.typeOf(document.caretPositionFromPoint) == "function"
                )
                  MediaClipboard.caretFromPoint(event.clientX, event.clientY)
                else null
              if (caret != null && root.contains(caret.node.asInstanceOf[dom.Node])) {
                val range = dom.document.createRange()
                range.setStart(caret.node.asInstanceOf[dom.Node], caret.offset.asInstanceOf[Int])
                range.collapse(true)
                val selected = runtime
                  .selectDynamic("$createRangeSelection")
                  .asInstanceOf[js.Function0[js.Dynamic]]()
                selected.applyDOMRange(range)
                Lexical.$setSelection(selected.asInstanceOf[BaseSelection])
              } else {
                val node = Option(event.target)
                  .collect { case element: dom.Element => element }
                  .map(Lexical.$getNearestNodeFromDOMNode)
                node.filter(_ != null).fold(Lexical.$getRoot().selectEnd())(_.selectEnd())
              }
              draggedKey match {
                case Some(key) =>
                  val node = Lexical.$getNodeByKey(key)
                  if (node != null && node.isAttached()) Lexical.$insertNodes(js.Array(node))
                case None =>
                  if (files.nonEmpty) uploadFiles(files, targetAfterUpdate()) else paste(data)
              }
            },
            js.Dynamic.literal(discrete = true).asInstanceOf[EditorUpdateOptions]
          )
          draggedKey = None
        }
      }
    }
    listen[dom.MouseEvent]("mousedown") { event =>
      Option(event.target)
        .collect { case element: dom.Element => element }
        .filter(_.classList.contains("image-resizer"))
        .foreach { handle =>
          if (editor.isEditable()) imageKey(handle).foreach { key =>
            event.preventDefault()
            cancelResize()
            val img = handle.parentNode
              .asInstanceOf[dom.Element]
              .querySelector("img")
              .asInstanceOf[dom.HTMLImageElement]
            val original = img.getAttribute("width")
            val start    = img.getBoundingClientRect().width.toInt match {
              case 0     => Option(original).flatMap(_.toIntOption).getOrElse(img.naturalWidth)
              case value => value
            }
            var width                                    = math.max(1, start)
            val move: js.Function1[dom.MouseEvent, Unit] = next => {
              width = math.max(1, (start + next.clientX - event.clientX).toInt)
              img.setAttribute("width", width.toString)
            }
            val up: js.Function1[dom.MouseEvent, Unit] = _ => {
              cancelResize()
              if (!closed && editor.isEditable())
                editor.update(
                  () => {
                    val node = Lexical.$getNodeByKey(key)
                    if (node != null && node.isAttached()) {
                      val image = node.asInstanceOf[ImageNode]
                      image.setReference(image.reference.copy(widthPx = Some(width)))
                    }
                  },
                  js.Dynamic
                    .literal(discrete = true, tag = "history-push")
                    .asInstanceOf[EditorUpdateOptions]
                )
            }
            cancelResize = () => {
              dom.window.removeEventListener("mousemove", move)
              dom.window.removeEventListener("mouseup", up)
              if (original == null) img.removeAttribute("width")
              else img.setAttribute("width", original)
              cancelResize = () => ()
            }
            dom.window.addEventListener("mousemove", move)
            dom.window.addEventListener("mouseup", up)
          }
        }
    }
  }

  private def imageKey(target: dom.EventTarget | Null): Option[String] = Option(target)
    .collect { case element: dom.Element => element }
    .flatMap(element => Option(element.closest("[data-image-key]")))
    .map(_.getAttribute("data-image-key"))
}
