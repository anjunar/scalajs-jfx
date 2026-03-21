package app.component

import app.domain.Address
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.form.Input.input
import jfx.form.InputContainer.inputContainer
import jfx.form.SubForm.subForm
import org.scalajs.dom.HTMLDivElement

final class AddressForm(slot: AddressForm ?=> Unit = ()) extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given AddressForm = this

      classes = "address-form"

      subForm[Address]("address") {
        classes = "address-form__card"

        slot

        inputContainer("Strasse") {
          input("street")
        }

        inputContainer("Stadt") {
          input("city")
        }
      }
    }

}

object AddressForm {
  def addressForm(init: AddressForm ?=> Unit = {}): AddressForm =
    composite(new AddressForm(init))
}
