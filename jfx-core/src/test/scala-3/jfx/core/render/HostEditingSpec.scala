package jfx.core.render

import jfx.core.component.{AbstractComponent, HydrationBoundary, Runtime}
import jfx.core.di.Context
import jfx.core.layout.{TextArea, TextComponent}
import jfx.core.statement.KeyedChildren
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HostEditingSpec extends AnyFlatSpec with Matchers {
  private class Box(val tagName: String = "div") extends AbstractComponent
  private class Label(value: String) extends Box("p") {
    val text = new TextComponent(value)
    override def compose(cursor: Cursor): Unit = Runtime.mount(text, cursor, Some(this))
  }
  private def child[C <: AbstractComponent](parent: AbstractComponent, component: C): C =
    Runtime.mount(component, Runtime.contentCursor(parent), Some(parent))
  private def fixture(): (SsrCursor, Box) = {
    val cursor = new SsrCursor()
    (cursor, Runtime.mount(new Box(), cursor))
  }

  "Text splices" should "use UTF-16 offsets and retain the same host" in {
    val (cursor, root) = fixture()
    val text = child(root, new TextComponent("A😀B"))
    val original = text.firstPhysicalHost.get
    text.spliceText(1, 2, "e\u0301")
    text.getText shouldBe "Ae\u0301B"
    text.firstPhysicalHost.get should be theSameInstanceAs original
    cursor.collectHtml() shouldBe "<div>Ae\u0301B</div>"
  }

  it should "reject invalid ranges without changing mounted or pending text" in {
    val text = new TextComponent("abc")
    intercept[IllegalArgumentException](text.spliceText(2, Int.MaxValue, "x"))
    text.getText shouldBe "abc"
    val (_, root) = fixture()
    child(root, text)
    intercept[IllegalArgumentException](text.spliceText(-1, 1, "x"))
    text.getText shouldBe "abc"
  }

  "SSR insertion" should "move existing references instead of duplicating them" in {
    val parent = new SsrHostElement("p")
    val a = new SsrTextNode("a")
    val b = new SsrTextNode("b")
    parent.insertBefore(a, None)
    parent.insertBefore(b, None)
    parent.insertBefore(b, Some(a))
    parent.renderHtml() shouldBe "<p>ba</p>"
    parent.childCount shouldBe 2
    val other = new SsrHostElement("p")
    other.insertBefore(b, None)
    parent.renderHtml() shouldBe "<p>a</p>"
    other.renderHtml() shouldBe "<p>b</p>"
  }

  it should "validate cycles and anchors before removing the source" in {
    val parent = new SsrHostElement("div")
    val nested = new SsrHostElement("p")
    parent.insertBefore(nested, None)
    intercept[IllegalArgumentException](nested.insertBefore(parent, None))
    intercept[IllegalArgumentException](parent.insertBefore(nested, Some(new SsrTextNode("x"))))
    parent.renderHtml() shouldBe "<div><p></p></div>"
  }

  "Runtime.move" should "preserve physical children and later updates across parents" in {
    val (cursor, root) = fixture()
    val left = child(root, new Box("section"))
    val right = child(root, new Box("article"))
    val a = child(left, new Label("a"))
    val b = child(right, new Label("b"))
    val original = a.host
    Runtime.move(a, right, 0)
    a.host should be theSameInstanceAs original
    a.parent shouldBe Some(right)
    left.children shouldBe empty
    right.children shouldBe Seq(a, b)
    a.text.setText("updated")
    child(a, new TextComponent("!"))
    cursor.collectHtml() shouldBe "<div><section></section><article><p>updated!</p><p>b</p></article></div>"
    Runtime.move(a, right, 1)
    right.children shouldBe Seq(b, a)
    Runtime.unmount(a)
    right.children shouldBe Seq(b)
    a.isDisposed shouldBe true
  }

  it should "reject context changes, cycles, virtual roots and invalid positions" in {
    val (_, root) = fixture()
    val left = child(root, new Box())
    val right = child(root, new Box())
    val service = Context.create[String]("service")
    service.provide("left")(using left)
    service.provide("right")(using right)
    val label = child(left, new Label("a"))
    intercept[IllegalArgumentException](Runtime.move(label, right, 0))
    intercept[IllegalArgumentException](Runtime.move(left, label, 0))
    intercept[IllegalArgumentException](Runtime.move(label, left, 2))
    val group = child(left, new KeyedChildren[String, String, Label](Nil, identity, new Label(_),
      (c, value) => c.text.setText(value)))
    intercept[IllegalArgumentException](Runtime.move(group, left, 0))
    label.parent shouldBe Some(left)
    left.children shouldBe Seq(label, group)
  }

  "KeyedChildren" should "update values and reorder without recreating components" in {
    val (cursor, root) = fixture()
    var created = 0
    val items = child(root, new KeyedChildren[String, (String, String), Label](
      Seq("a" -> "one", "b" -> "two"), _._1,
      item => { created += 1; new Label(item._2) }, (c, item) => c.text.setText(item._2)))
    val a = items.componentFor("a").get
    val b = items.componentFor("b").get
    items.setItems(Seq("b" -> "TWO", "a" -> "one", "c" -> "three"))
    items.children.take(2) shouldBe Seq(b, a)
    items.componentFor("a").get should be theSameInstanceAs a
    b.text.getText shouldBe "TWO"
    created shouldBe 3
    items.setItems(Seq("b" -> "TWO"))
    a.isDisposed shouldBe true
    cursor.collectHtml() should include("<p>TWO</p>")
    intercept[IllegalArgumentException](items.setItems(Seq("b" -> "x", "b" -> "y")))
    b.text.getText shouldBe "TWO"
  }

  "Runtime.move context checks" should "reject equal but distinct service instances" in {
    final case class Service(value: String)
    val (_, root) = fixture()
    val left = child(root, new Box())
    val right = child(root, new Box())
    val context = Context.create[Service]("reference service")
    context.provide(Service("equal"))(using left)
    context.provide(Service("equal"))(using right)
    val label = child(left, new Label("a"))
    intercept[IllegalArgumentException](Runtime.move(label, right, 0))
    label.parent shouldBe Some(left)
  }

  it should "transfer keys across groups and keep subsequent updates in the destination" in {
    val (_, root) = fixture()
    def group(values: Seq[String]) = new KeyedChildren[String, String, Label](values, identity,
      new Label(_), (c, value) => c.text.setText(value))
    val left = child(root, group(Seq("a")))
    val right = child(root, group(Seq("b")))
    val a = left.componentFor("a").get
    left.transferTo("a", right, 1)
    left.componentFor("a") shouldBe None
    right.componentFor("a") shouldBe Some(a)
    right.setItems(Seq("a", "b"))
    right.children.head should be theSameInstanceAs a
    left.setItems(Nil)
    a.isDisposed shouldBe false
  }

  "Mutation guards" should "reject writes, ancestor removal and moves before ownership changes" in {
    val (cursor, root) = fixture()
    val label = child(root, new Label("a"))
    val target = child(root, new Box())
    val lease = HostMutationGuard.protect(label.host)
    try {
      val before = cursor.collectHtml()
      intercept[HostWriteBlocked](label.text.setText("b"))
      intercept[HostWriteBlocked](label.text.spliceText(0, 1, "b"))
      intercept[HostWriteBlocked](Runtime.move(label, target, 0))
      intercept[HostWriteBlocked](Runtime.unmount(root))
      intercept[HostWriteBlocked](root.dispose())
      intercept[HostWriteBlocked](label.setClasses(Seq("blocked")))
      label.getClasses shouldBe empty
      val pending = new Label("new")
      intercept[HostWriteBlocked](child(label, pending))
      pending.isBound shouldBe false
      pending.parent shouldBe None
      root.isDisposed shouldBe false
      label.text.getText shouldBe "a"
      label.parent shouldBe Some(root)
      cursor.collectHtml() shouldBe before
      label.text.setText("a") // no-op needs no write
    } finally lease.dispose()
    lease.dispose()
    label.text.setText("b")
    Runtime.unmount(root)
    root.isDisposed shouldBe true
  }

  it should "preflight an entire keyed update and allow a retry after release" in {
    val (_, root) = fixture()
    val items = child(root, new KeyedChildren[String, String, Label](Seq("a", "b"), identity,
      new Label(_), (c, value) => c.text.setText(value)))
    val a = items.componentFor("a").get
    val lease = HostMutationGuard.protect(a.host)
    try {
      intercept[HostWriteBlocked](items.setItems(Seq("b")))
      items.children.size shouldBe 2
      a.isDisposed shouldBe false
    } finally lease.dispose()
    items.setItems(Seq("b"))
    a.isDisposed shouldBe true
  }

  "Textarea SSR" should "emit safe RCDATA without comment anchors and preserve a leading LF" in {
    for ((value, expected) <- Seq("" -> "", "\nabc" -> "\n\nabc", "&</textarea>" -> "&amp;&lt;/textarea&gt;")) {
      val html = Runtime.renderToString(cursor => Runtime.mount(new TextArea(value), cursor))
      html shouldBe s"<textarea>$expected</textarea>"
    }
  }

  it should "separate current value and reset baseline and normalize line endings" in {
    val (cursor, root) = fixture()
    val field = child(root, new TextArea("a\r\nb"))
    field.setValue("draft")
    field.setDefaultValue("reset")
    field.value shouldBe "draft"
    field.defaultValue shouldBe "reset"
    field.reset()
    field.value shouldBe "reset"
    field.valueProperty.get shouldBe "reset"
    cursor.collectHtml() should include("<textarea>reset</textarea>")
  }

  "HydrationBoundary SSR" should "render normally without browser capture or recovery" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new HydrationBoundary[Unit]("section", _ => fail("capture on server"),
        (_, _) => fail("preflight on server"))({
        Runtime.mount(new Label("hello"), summon[Cursor], Some(summon[AbstractComponent]))
      }), cursor)
    }
    html shouldBe "<section><p>hello</p></section>"
  }

  "Textarea pending state" should "retain a value set before mounting and its independent baseline" in {
    val (_, root) = fixture()
    val field = new TextArea("baseline")
    field.setValue("draft")
    child(root, field)
    field.value shouldBe "draft"
    field.defaultValue shouldBe "baseline"
  }
}
