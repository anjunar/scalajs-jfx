package jfx.bridge

import jfx.core.component.{AbstractComponent}
import jfx.core.state.{Disposable => CoreDisposable}
import jfx.forms.ErrorResponse

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** The JS projection of `AbstractComponent` plus its DSL traits. Mirrors `contract.ts`'s
  * `ComponentHandle`.
  */
final class ComponentHandleBridge(private[bridge] final val underlying: AbstractComponent)
    extends js.Object {

  def tagName: String = underlying.tagName

  def addClass(name: String): Unit = underlying.addClass(name)

  def removeClass(name: String): Unit = underlying.removeClass(name)

  def setClasses(names: js.Array[String]): Unit = underlying.setClasses(names.toSeq)

  def classIf(name: String, condition: JsReadOnlyProperty[Boolean]): Unit =
    underlying.classCondition(name, ReactiveBridge.wrap(condition))

  def setAttribute(name: String, value: String): Unit = underlying.setAttribute(name, value)

  def removeAttribute(name: String): Unit = underlying.removeAttribute(name)

  def attribute(name: String): String = underlying.attribute(name).orNull

  def setDomProperty(name: String, value: js.Any): Unit = underlying.setProperty(name, value)

  def setStyle(name: String, value: String): Unit = underlying.setStyle(name, value)

  def removeStyle(name: String): Unit = underlying.removeStyle(name)

  def on(eventName: String, handler: js.Function1[UiEventHandle, Unit]): Unit =
    underlying.onHandler(eventName)(event => handler(new UiEventHandle(event)))

  def addDisposable(disposable: JsDisposable): Unit =
    underlying.addDisposable(CoreDisposable(disposable.dispose()))

  /** Form operations are exposed on the generic handle so the TypeScript form
    * facade can return a typed view without introducing a second component
    * handle hierarchy in the bridge.
    */
  def validate(): js.Array[String] =
    underlying match {
      case form: DynamicFormular => form.validate().toJSArray
      case _ =>
        throw new IllegalStateException("validate() is only available on a form handle.")
    }

  def validateBindings(): js.Array[String] =
    underlying match {
      case form: DynamicFormular => form.validateBindings().toJSArray
      case _ =>
        throw new IllegalStateException("validateBindings() is only available on a form handle.")
    }

  def setErrorResponses(errors: js.Array[js.Dictionary[js.Any]]): Unit =
    underlying match {
      case form: DynamicFormular =>
        form.setErrorResponses(errors.toSeq.map(error =>
          ErrorResponse(
            error.get("message").map(_.toString).getOrElse(""),
            error
              .get("path")
              .map(_.asInstanceOf[js.Array[String]].toSeq)
              .getOrElse(Seq.empty)
          )
        ))
      case _ =>
        throw new IllegalStateException("setErrorResponses() is only available on a form handle.")
    }

  def clearErrors(): Unit =
    underlying match {
      case form: DynamicFormular => form.clearErrors()
      case _ =>
        throw new IllegalStateException("clearErrors() is only available on a form handle.")
    }

}
