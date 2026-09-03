package jfx.forms

import org.scalajs.dom

import scala.scalajs.LinkingInfo

/** A control could not be bound to a model property. */
final class FormBindingException(message: String) extends IllegalStateException(message)

/** How a failed binding is reported.
  *
  * A form binding that fails silently is the most expensive kind of mistake in a statically typed
  * language: a typo in a field name leaves a control that looks fine and does nothing. So the
  * failure is loud where somebody is watching and recorded where nobody is.
  *
  * `LinkingInfo.developmentMode` is a linker constant — `fullLinkJS` folds the branch away, so the
  * throw costs a production build nothing.
  */
private[forms] object FormBinding {

  def fail(message: String): Unit =
    if (LinkingInfo.developmentMode) throw new FormBindingException(message)
    else dom.console.error(message)
}
