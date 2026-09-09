package jfx.control.table

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TableScrollPositionSpec extends AnyFlatSpec with Matchers {
  "Row navigation" should "leave fully visible rows in place" in {
    TableScrollPosition.reveal(120, 40, 100, 200, 1000) shouldBe 100
    TableScrollPosition.reveal(260, 40, 100, 200, 1000) shouldBe 100
  }

  it should "reveal partially hidden rows with minimal movement" in {
    TableScrollPosition.reveal(80, 40, 100, 200, 1000) shouldBe 80
    TableScrollPosition.reveal(280, 40, 100, 200, 1000) shouldBe 120
  }

  it should "include headers and clamp the last row to the end of the content" in {
    TableScrollPosition.reveal(80 + 99 * 40, 40, 0, 200, 4080) shouldBe 3880
    TableScrollPosition.reveal(80, 40, 200, 200, 4080) shouldBe 80
    TableScrollPosition.reveal(40, 40, 500, 200, 80) shouldBe 0
  }

  it should "align rows taller than the viewport at their start" in {
    TableScrollPosition.reveal(200, 300, 100, 200, 1000) shouldBe 200
  }
}
