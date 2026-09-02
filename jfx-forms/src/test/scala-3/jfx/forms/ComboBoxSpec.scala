package jfx.forms

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.Property
import jfx.forms.ComboBox.*
import jfx.forms.Form.form
import jfx.viewport.Viewport
import jfx.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ComboBoxSpec extends AnyFlatSpec with Matchers {

  "ComboBox SSR" should "render its closed value without creating dropdown markup" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new ComboRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            comboBox[Member]("owner", standalone = true) {
              placeholder = "Choose an owner"
              items = members
              converter = _.name
              identityBy = _.id
            }
        },
        cursor
      )
    }

    html should include("class=\"jfx-combo-box\"")
    html should include("role=\"combobox\"")
    html should include("aria-expanded=\"false\"")
    html should include("Choose an owner")
    html should not include "jfx-combo-box__dropdown"
  }

  it should "mount its TableView and contextual renderers through the viewport overlay" in {
    val cursor                  = new SsrCursor()
    var combo: ComboBox[Member] = null
    val root                    = Runtime.mount(
      new ComboRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          viewport {
            combo = comboBox[Member]("owner", standalone = true) {
              items = members
              converter = _.name
              identityBy = _.id
              itemRenderer { (member, selected) =>
                div {
                  classes = Seq("member-row")
                  classIf("selected-member", selected)
                  text(member.name) {}
                }
              }
              valueRenderer { member =>
                div {
                  classes = Seq("member-value")
                  text(member.name) {}
                }
              }
              footerRenderer {
                div {
                  classes = Seq("member-footer")
                  text("Manage members") {}
                }
              }
            }
          }
      },
      cursor
    )

    try {
      combo.selectItem(members.head)
      combo.toggle()
      val html = cursor.collectHtml()
      html should include("jfx-viewport-overlay")
      html should include("jfx-combo-box__dropdown")
      html should include("jfx-combo-box__table")
      html should include("member-row")
      html should include("selected-member")
      html should include("member-value")
      html should include("Alice")
      html should include("Manage members")
      Viewport.overlays.length shouldBe 1
    } finally Runtime.unmount(root)

    Viewport.overlays shouldBe empty
  }

  "ComboBox selection" should "use stable identity and adopt replacement item instances" in {
    val cursor                  = new SsrCursor()
    var combo: ComboBox[Member] = null
    val root                    = Runtime.mount(
      new ComboRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          combo = comboBox[Member]("owner", standalone = true) {
            items = members
            identityBy = _.id
          }
      },
      cursor
    )

    val original = members.head
    combo.selectItem(original)
    combo.valueProperty.get shouldBe original
    combo.dirtyProperty.get shouldBe true

    val replacement = original.copy(name = "Alice Updated")
    combo.itemsProperty.setAll(Seq(replacement, members(1)))
    combo.selectionProperty.head should be theSameInstanceAs replacement
    combo.valueProperty.get should be theSameInstanceAs replacement

    Runtime.unmount(root)
    combo.selectionProperty.clear()
    combo.valueProperty.get should be theSameInstanceAs replacement
  }

  it should "toggle independent selections in multi-select mode" in {
    val cursor                  = new SsrCursor()
    var combo: ComboBox[Member] = null
    val root                    = Runtime.mount(
      new ComboRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          combo = comboBox[Member]("owners", standalone = true) {
            items = members
            identityBy = _.id
            multiSelect = true
            selectionText = values => values.map(_.name).mkString(", ")
          }
      },
      cursor
    )

    combo.selectItem(members(0))
    combo.selectItem(members(1))
    combo.selectionProperty.toSeq shouldBe members
    combo.selectItem(members(0).copy(name = "Same identity"))
    combo.selectionProperty.toSeq shouldBe Seq(members(1))
    combo.valueProperty.get shouldBe members(1)

    Runtime.unmount(root)
  }

  it should "close and reject user selection while readonly" in {
    val cursor = new SsrCursor()
    var combo: ComboBox[Member] = null
    val root = Runtime.mount(
      new ComboRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          combo = comboBox[Member]("owner", standalone = true) {
            items = members
          }
      },
      cursor
    )

    combo.toggle()
    combo.openProperty.get shouldBe true
    combo.editableProperty.set(false)
    combo.openProperty.get shouldBe false
    combo.selectItem(members.head)
    combo.selectionProperty shouldBe empty

    Runtime.unmount(root)
  }

  it should "participate in typed form binding in both directions" in {
    val first                   = members.head
    val second                  = members(1)
    val model                   = SelectionModel(Property(first))
    var combo: ComboBox[Member] = null
    val cursor                  = new SsrCursor()
    val root                    = Runtime.mount(
      new ComboRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          form(model) {
            combo = comboBox[Member]("owner") {
              items = members
              identityBy = _.id
            }
          }
      },
      cursor
    )

    combo.selectionProperty.toSeq shouldBe Seq(first)
    combo.selectItem(second)
    model.owner.get shouldBe second

    val replacement = first.copy(name = "Alice from model")
    model.owner.set(replacement)
    combo.selectionProperty.head should be theSameInstanceAs first
    combo.valueProperty.get should be theSameInstanceAs replacement
    model.owner.get should be theSameInstanceAs replacement

    Runtime.unmount(root)
  }

  private val members = Seq(
    Member(1, "Alice"),
    Member(2, "Bob")
  )
}

private abstract class ComboRoot extends AbstractComponent {
  override val tagName: String = "main"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}

private final case class Member(id: Int, name: String)

private final case class SelectionModel(var owner: Property[Member])
