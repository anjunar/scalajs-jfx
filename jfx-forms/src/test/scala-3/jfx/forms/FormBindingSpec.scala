package jfx.forms

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.Property
import jfx.forms.Form.form
import jfx.forms.Input.input
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class FormBindingSpec extends AnyFlatSpec with Matchers {

  "A form binding" should "fail loudly in development mode when a field name does not exist" in {
    val account = new Account()

    val failure = the[FormBindingException] thrownBy {
      Runtime.mount(
        new BindingRoot({
          form(account) {
            input("mail") {} // the model calls it "email"
          }
        }),
        new SsrCursor()
      )
    }

    failure.getMessage should include("cannot bind control 'mail'")
    failure.getMessage should include("no readable property named 'mail'")
  }

  it should "report the same failure through validateBindings" in {
    val account  = new Account()
    val formular = new Form[Account](account, Some(ReflectMacros.reflectWithAccessors[Account]))
    val misspelt = new Input("mail")

    a[FormBindingException] should be thrownBy formular.register(misspelt)

    formular.validateBindings() should have size 1
    formular.validateBindings().head should include("cannot bind control 'mail'")
  }

  it should "report nothing once every control found its model property" in {
    var formular: Form[Account] = null
    val account                 = new Account()

    val root = Runtime.mount(
      new BindingRoot({
        formular = form(account) {
          input("email") {}
        }
      }),
      new SsrCursor()
    )

    formular.validateBindings() shouldBe empty

    Runtime.unmount(root)
  }

  it should "clear a control to the value it was built with, not to null" in {
    var formular: Form[Account] = null
    var emailInput: Input       = null
    val account                 = new Account()
    account.email.set("ada@example.org")

    val root = Runtime.mount(
      new BindingRoot({
        formular = form(account) {
          emailInput = input("email") {}
        }
      }),
      new SsrCursor()
    )

    emailInput.valueProperty.get shouldBe "ada@example.org"

    formular.setModel(null)

    emailInput.valueProperty.get shouldBe ""

    Runtime.unmount(root)
  }
}

private final class BindingRoot(body: AbstractComponent ?=> Cursor ?=> Unit)
    extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor)(body)
}

private final class Account(
    var email: Property[String] = Property("")
)
