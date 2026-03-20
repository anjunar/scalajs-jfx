package app

import jfx.core.component.CompositeComponent
import jfx.dsl.*
import org.scalajs.dom.HTMLDivElement

final class AddressForm(slot: AddressForm ?=> Unit = ()) extends CompositeComponent[HTMLDivElement] {

  override lazy val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given AddressForm = this

      style {
        display = "flex"
        setProperty("flex-direction", "column")
        setProperty("gap", "10px")
      }

      subForm[Address]("address") {
        style {
          display = "flex"
          setProperty("flex-direction", "column")
          setProperty("gap", "10px")
          padding = "16px"
          border = "1px solid #e2e8f0"
          borderRadius = "8px"
          backgroundColor = "#f8fafc"
        }

        slot

        input("street") {
          placeholder = "Stra\u00DFe"
          style {
            padding = "10px 12px"
            border = "1px solid #cbd5e1"
            borderRadius = "8px"
            fontSize = "15px"
          }
        }

        input("city") {
          placeholder = "Stadt"
          style {
            padding = "10px 12px"
            border = "1px solid #cbd5e1"
            borderRadius = "8px"
            fontSize = "15px"
          }
        }
      }
    }

}

def addressForm(init: AddressForm ?=> Unit = {}): AddressForm =
  composite(new AddressForm(init))
