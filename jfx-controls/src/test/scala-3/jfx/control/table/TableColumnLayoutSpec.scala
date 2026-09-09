package jfx.control.table

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import TableColumnLayout.*
import ColumnResizePolicy.*

class TableColumnLayoutSpec extends AnyFlatSpec with Matchers {
  private def col(
      width: Double = 100,
      min: Double = 40,
      max: Double = 300,
      resizable: Boolean = true
  ): Column = Column(min, width, max, resizable)

  "Column layout" should "respect bounds and leave unconstrained preferred widths alone" in {
    layout(Vector(col(10), col(500)), 800, Unconstrained) shouldBe Vector(40, 300)
    layout(Vector(col(), col()), 500, FlexLastColumn) shouldBe Vector(250, 250)
  }

  it should "redistribute until all space or all capacity is consumed" in {
    layout(Vector(col(max = 110), col(max = 150), col()), 500, AllColumns) shouldBe Vector(
      110,
      150,
      240
    )
    layout(Vector(col(), col()), 10, AllColumns) shouldBe Vector(40, 40)
    layout(Vector(col(), col()), 1000, AllColumns) shouldBe Vector(300, 300)
  }

  it should "keep non-resizable columns fixed and sanitize invalid constraints" in {
    layout(Vector(col(resizable = false), col()), 400, NextColumn) shouldBe Vector(100, 300)
    Column(Double.NaN, Double.NaN, Double.NaN, true).initial shouldBe 160
    Column(80, 10, 30, true).initial shouldBe 80
    Column(-30, 10, 30, true).minimum shouldBe 0
  }

  "Column resizing" should "apply unconstrained deltas without changing neighbors" in {
    val columns = Vector.fill(3)(col())
    resize(columns, Vector(100, 100, 100), 1, 500, Unconstrained) shouldBe Vector(100, 300, 100)
    resize(columns, Vector(100, 100, 100), 1, -500, Unconstrained) shouldBe Vector(100, 40, 100)
  }

  it should "compensate only the requested next or last column" in {
    val columns = Vector.fill(4)(col())
    val widths  = Vector.fill(4)(100.0)
    resize(columns, widths, 0, 100, NextColumn) shouldBe Vector(160, 40, 100, 100)
    resize(columns, widths, 0, 100, LastColumn) shouldBe Vector(160, 100, 100, 40)
    resize(columns, widths, 3, 10, LastColumn) shouldBe widths
  }

  it should "continue across saturated columns in flex direction" in {
    val columns = Vector.fill(4)(col())
    val widths  = Vector.fill(4)(100.0)
    resize(columns, widths, 0, 100, FlexNextColumn) shouldBe Vector(200, 40, 60, 100)
    resize(columns, widths, 0, 100, FlexLastColumn) shouldBe Vector(200, 100, 60, 40)
    resize(columns, widths, 0, -50, FlexLastColumn) shouldBe Vector(50, 100, 100, 150)
  }

  it should "proportionally compensate all or subsequent columns" in {
    val columns = Vector(col(120), col(100), col(240))
    val widths  = Vector(120.0, 100.0, 240.0)
    resize(columns, widths, 1, 60, AllColumns) shouldBe Vector(100, 160, 200)
    resize(columns, widths, 1, 60, SubsequentColumns) shouldBe Vector(120, 160, 180)
  }

  it should "ignore invalid indices, deltas and locked columns" in {
    val columns = Vector(col(resizable = false), col())
    val widths  = Vector(100.0, 100.0)
    resize(columns, widths, 0, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 1, 10, AllColumns) shouldBe widths
    resize(columns, widths, -1, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 2, 10, Unconstrained) shouldBe widths
    resize(columns, widths, 1, Double.NaN, Unconstrained) shouldBe widths
  }

  it should "preserve the constrained sum and bounds through repeated adjustments" in {
    ColumnResizePolicy.values.filterNot(_ == Unconstrained).foreach { policy =>
      val columns = Vector(col(), col(max = 120), col(resizable = false), col(max = 500))
      var widths  = layout(columns, 650, policy)
      val sum     = widths.sum
      (0 until 100).foreach { step =>
        widths = resize(columns, widths, step % 4, if (step % 3 == 0) -117 else 89, policy)
        widths.sum shouldBe sum +- 0.0001
        widths.zip(columns).foreach { (width, column) =>
          width should be >= column.minimum
          width should be <= column.maximum
        }
      }
    }
  }
}
