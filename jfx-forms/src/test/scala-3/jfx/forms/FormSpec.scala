package jfx.forms

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.{ListProperty, Property}
import jfx.forms.ArrayForm.*
import jfx.forms.Form.form
import jfx.forms.Input.input
import jfx.forms.InputContainer.inputContainer
import jfx.forms.SubForm.subForm
import jfx.forms.validators.{NotBlank, NotBlankValidator}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.annotation.meta.field

class FormSpec extends AnyFlatSpec with Matchers {

  "Form" should "bind model properties bidirectionally and move bindings on model replacement" in {
    val first = Person()
    first.name.set("Ada")
    val second = Person()
    second.name.set("Grace")
    var mountedForm: Form[Person] = null
    var nameInput: Input = null

    val root = mount {
      mountedForm = form(first) {
        nameInput = input("name") {}
      }
    }

    nameInput.valueProperty.get shouldBe "Ada"
    nameInput.validators.toSeq should contain(NotBlankValidator("Name is required"))

    nameInput.valueProperty.set("Augusta")
    first.name.get shouldBe "Augusta"

    mountedForm.setModel(second)
    nameInput.valueProperty.get shouldBe "Grace"
    nameInput.valueProperty.set("Hopper")
    second.name.get shouldBe "Hopper"
    first.name.get shouldBe "Augusta"

    Runtime.unmount(root)
  }

  it should "bind nested subforms and route nested server errors" in {
    val person = Person()
    person.address.get.street.set("Analytical Engine Way")
    var mountedForm: Form[Person] = null
    var streetInput: Input = null

    val root = mount {
      mountedForm = form(person) {
        subForm[Address]("address") {
          streetInput = input("street") {}
        }
      }
    }

    streetInput.valueProperty.get shouldBe "Analytical Engine Way"
    streetInput.valueProperty.set("Compiler Street")
    person.address.get.street.get shouldBe "Compiler Street"

    streetInput.valueProperty.set("")
    mountedForm.validate() should contain("Street is required")

    mountedForm.setErrorResponses(Seq(ErrorResponse("Unknown street", Seq("address", "street"))))
    streetInput.errors.toSeq shouldBe Seq("Unknown street")

    mountedForm.resetInteractionState()
    streetInput.errors shouldBe empty
    streetInput.dirtyProperty.get shouldBe false

    Runtime.unmount(root)
  }

  it should "render and synchronize array controls across structural list changes" in {
    val person = Person()
    person.tags.setAll(Seq("math", "compilers"))
    var tagsForm: ArrayForm[String] = null

    val root = mount {
      form(person) {
        tagsForm = arrayForm[String]("tags") {
          controlRenderer = index => input(s"tag-$index") {}
        }
      }
    }

    tagsForm.itemControls.map(_.valueProperty.get) shouldBe Seq("math", "compilers")
    tagsForm.itemControls.foreach { control =>
      control.asInstanceOf[AbstractComponent].parent should not contain tagsForm
    }

    person.tags.insert(1, "logic")
    tagsForm.itemControls.map(_.valueProperty.get) shouldBe Seq("math", "logic", "compilers")

    Runtime.unmount(root)
  }

  it should "connect InputContainer labels, state classes, and validation messages" in {
    var container: InputContainer = null
    var nameInput: Input = null
    val label = Property("Name")

    val root = mount {
      container = inputContainer(label) {
        nameInput = input("name", standalone = true) {
          Input.validators += NotBlankValidator("Required")
        }
      }
    }

    nameInput.host.attribute("placeholder") shouldBe Some("Name")
    container.host.attribute("class").getOrElse("") should include("empty")

    nameInput.validate(forceVisible = true)
    descendantText(container) should contain("Required")

    nameInput.valueProperty.set("Ada")
    container.host.attribute("class").getOrElse("") should not include "empty"

    label.set("Display name")
    nameInput.host.attribute("placeholder") shouldBe Some("Display name")
    descendantText(container) should contain("Display name")

    Runtime.unmount(root)
  }

  private def mount(body: AbstractComponent ?=> Cursor ?=> Unit): TestRoot = {
    val root = new TestRoot(body)
    Runtime.mount(root, new SsrCursor())
  }

  private def descendantText(component: AbstractComponent): Seq[String] =
    component.children.flatMap {
      case value: TextComponent => Seq(value.getText)
      case child                => descendantText(child)
    }
}

private final class TestRoot(body: AbstractComponent ?=> Cursor ?=> Unit) extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor)(body)
}

private final class Person(
    @(NotBlank @field)("Name is required")
    var name: Property[String] = Property(""),
    var address: Property[Address] = Property(Address()),
    var tags: ListProperty[String] = ListProperty()
)

private object Person {
  def apply(): Person = new Person()
}

private final class Address(
    @(NotBlank @field)("Street is required")
    var street: Property[String] = Property("")
)

private object Address {
  def apply(): Address = new Address()
}
