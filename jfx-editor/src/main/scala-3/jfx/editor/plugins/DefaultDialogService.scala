package jfx.editor.plugins

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{onClick, onWindowKeyDown}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.{button, buttonType}
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import jfx.viewport.Viewport
import lexical.DialogService
import org.scalajs.dom.HTMLElement

final class DefaultDialogService(owner: AbstractComponent) extends DialogService, AutoCloseable {
  private var activeWindow: Viewport.WindowConf | Null = null

  override def show(
      title: String,
      contentProvider: () => HTMLElement,
      onConfirm: HTMLElement => Unit
  ): Unit =
    showAsync(title, contentProvider, content => Future.fromTry(Try(onConfirm(content))))

  override def showAsync(
      title: String,
      contentProvider: () => HTMLElement,
      onConfirm: HTMLElement => Future[Unit]
  ): Unit = {
    close()

    val content                   = contentProvider()
    var conf: Viewport.WindowConf = null
    conf = new Viewport.WindowConf(
      body = {
        DslLayer.child(
          new DialogBody(
            content,
            onCancel = () => closeWindow(conf),
            onConfirm = () => {
              onConfirm(content)
            },
            onSuccess = () => {
              if (activeWindow eq conf) closeWindow(conf)
            }
          )
        ) {}
      },
      widthPx = 720,
      heightPx = 560,
      onClose = Some(_ => windowClosed(conf))
    )
    conf.title = title
    activeWindow = conf
    Viewport.addWindow(conf)(using owner)
  }

  override def close(): Unit =
    Option(activeWindow).foreach(closeWindow)

  private def closeWindow(conf: Viewport.WindowConf): Unit = {
    if (activeWindow eq conf) activeWindow = null
    Viewport.closeWindow(conf)
  }

  private def windowClosed(conf: Viewport.WindowConf): Unit =
    if (activeWindow eq conf) activeWindow = null
}

private final class DialogBody(
    content: HTMLElement,
    onCancel: () => Unit,
    onConfirm: () => Future[Unit],
    onSuccess: () => Unit
) extends AbstractComponent {
  override val tagName: String = "div"

  private var contentHost: Div   = null
  private val busy               = Property(false)
  private val error              = Property("")
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("jfx-editor-dialog")
      style {
        boxSizing = "border-box"
        display = "flex"
        flexDirection = "column"
        gap = "12px"
        height = "100%"
        padding = "16px"
      }

      contentHost = div {
        classes = Seq("jfx-dialog__content", "jfx-editor-dialog__content")
        style {
          flex = "1 1 auto"
          minHeight = "0"
          overflow = "auto"
        }
      }

      div {
        setAttribute("role", "alert")
        jfx.core.layout.TextComponent.text(error) {}
      }

      div {
        classes = Seq("jfx-dialog__actions", "jfx-editor-dialog__actions")

        button("Cancel") {
          classes = Seq("jfx-dialog__button", "jfx-dialog__button--secondary")
          buttonType("button")
          onClick { _ => onCancel() }
        }

        button("Confirm") {
          classes = Seq("jfx-dialog__button", "jfx-dialog__button--primary")
          buttonType("button")
          val confirmButton = summon[jfx.core.layout.Button]
          addDisposable(busy.observe(value => confirmButton.setProperty("disabled", value)))
          onClick { _ =>
            if (!busy.get) {
              busy.set(true)
              error.set("")
              def completed(outcome: scala.util.Try[Unit]): Unit = outcome match {
                case Success(_)       => busy.set(false); onSuccess()
                case Failure(failure) =>
                  busy.set(false);
                  error.set(Option(failure.getMessage).getOrElse("Image upload failed"))
              }
              val result = Try(onConfirm()).fold(Future.failed, identity)
              result.value match {
                case Some(outcome) => completed(outcome)
                case None          => result.onComplete(completed)
              }
            }
          }
        }
      }

      onWindowKeyDown { event =>
        if (event.key == "Escape") onCancel()
      }
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser && content != null)
      contentHost.host match {
        case domHost: DomHostElement =>
          content.classList.add("jfx-editor-dialog__foreign-content")
          domHost.node.appendChild(content)
          addDisposable(Disposable {
            if (content.parentNode == domHost.node) domHost.node.removeChild(content)
            DialogContent.dispose(content)
          })
          Option(content.querySelector("input, button, select, textarea"))
            .collect { case element: HTMLElement => element }
            .foreach(_.focus())
        case _ => ()
      }
}
