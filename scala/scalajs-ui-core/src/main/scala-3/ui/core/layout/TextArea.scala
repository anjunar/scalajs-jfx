package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, DomNodes, TextAreaContent}
import ui.core.state.{Disposable, Property, ReadOnlyProperty}
import org.scalajs.dom
import scala.scalajs.js

/** Native textarea primitive. Form binding, source parsing and composition policy belong to users
  * of this component. Hydration adopts the live value without changing focus or selection.
  */
class TextArea(initial: String = "") extends AbstractComponent {
  val tagName = "textarea"
  private var content: TextAreaContent = _
  private var pending = TextAreaContent.normalize(initial)
  private var baseline = pending
  private val observed = Property(pending)
  val valueProperty: ReadOnlyProperty[String] = observed

  def value: String = if (content == null) pending else content.value
  def defaultValue: String = if (content == null) baseline else content.defaultValue

  def setValue(value: String): Unit = {
    require(!isDisposed, "Textarea is disposed.")
    val next = TextAreaContent.normalize(value)
    if (content != null) content.setValue(next)
    pending = next
    observed.set(this.value)
  }

  def setDefaultValue(value: String): Unit = {
    require(!isDisposed, "Textarea is disposed.")
    val next = TextAreaContent.normalize(value)
    if (content != null) content.setDefaultValue(next)
    else if (pending == baseline) pending = next
    baseline = next
    observed.set(this.value)
  }

  def reset(): Unit = {
    require(!isDisposed, "Textarea is disposed.")
    if (content != null) content.reset() else pending = baseline
    observed.set(value)
  }

  /** Reads native changes, including script-set values which do not dispatch input. */
  def readNativeValue(): String = {
    val current = value
    observed.set(current)
    current
  }

  override def compose(cursor: Cursor): Unit = {
    content = cursor.claimTextAreaContent(baseline)
    if (!cursor.isHydrating && pending != baseline) content.setValue(pending)
    observed.set(value)
    addDisposable(host.on("input")(_ => readNativeValue()))
    DomNodes.option(host).foreach { raw =>
      val node = raw.asInstanceOf[dom.HTMLTextAreaElement]
      // A reset event fires on the form before its default action. Read after that action, and
      // respect cancellation. Use the owner document so later association with another form works.
      val listener: js.Function1[dom.Event, Unit] = event =>
        if (node.form != null && event.target == node.form) {
          js.Promise.resolve[Unit](()).`then`[Unit] { (_: Unit) =>
            if (!isDisposed && !event.defaultPrevented) readNativeValue()
            ()
          }
        }
      node.ownerDocument.addEventListener("reset", listener, true)
      addDisposable(Disposable(node.ownerDocument.removeEventListener("reset", listener, true)))
    }
  }
}

object TextArea {
  def textArea(initial: String = "")(body: TextArea ?=> Cursor ?=> Unit = {})(using
      AbstractComponent, Cursor
  ): TextArea = DslLayer.child(new TextArea(initial))(body)
}
