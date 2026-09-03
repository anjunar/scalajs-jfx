package jfx.core.state

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class ListPropertySpec extends AnyFlatSpec with Matchers {

  "ListProperty" should "isolate its state from the constructor array" in {
    val source   = js.Array("first")
    val property = ListProperty(source)

    source.push("external")

    property.toSeq shouldBe Seq("first")
  }

  it should "return snapshots that cannot mutate the property without notification" in {
    val property      = ListProperty(js.Array("first"))
    var notifications = 0
    property.observeWithoutInitial(_ => notifications += 1)

    val snapshot = property.get
    snapshot.push("external")

    property.toSeq shouldBe Seq("first")
    notifications shouldBe 0

    property.addOne("second")

    property.toSeq shouldBe Seq("first", "second")
    notifications shouldBe 1
  }
}
