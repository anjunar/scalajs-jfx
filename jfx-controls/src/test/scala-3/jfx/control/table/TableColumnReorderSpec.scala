package jfx.control.table

import jfx.control.table.TableView.*
import jfx.control.table.TableColumn.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, SsrCursor, HostMutationGuard, HostWriteBlocked}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

class TableColumnReorderSpec extends AnyFlatSpec with Matchers {
  "Column projection" should "retain cells, ownership and requested widths across permutations" in {
    var table: TableView[String] = null
    val created                  = ArrayBuffer.empty[TableCell[String, String]]
    val root                     = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(ListProperty(js.Array("Ada", "Grace"))) {
            columnResizePolicy = ColumnResizePolicy.Unconstrained
            Seq("A", "B", "C").foreach { title =>
              column[String, String](title) {
                cellFactory = _ => {
                  val cell = new TableCell[String, String]
                  created += cell
                  cell
                }
              }
            }
          }
        }
      },
      new SsrCursor()
    )
    try {
      val original = table.columns.toVector
      table.resizeColumn(original.head, 30) shouldBe true
      table.select(1)
      table.columns.setAll(original.reverse)
      table.visibleLeafColumns.get shouldBe original.reverse
      created.size shouldBe 6
      all(created.map(_.isDisposed)) shouldBe false
      original.head.width shouldBe 190
      all(original.map(_.tableViewProperty.get)) shouldBe table
      table.selectedIndexProperty.get shouldBe 1
      // Protected descendants reject the source permutation before any observer sees it.
      val lease = HostMutationGuard.protect(created.head.host)
      try {
        intercept[HostWriteBlocked](table.columns.setAll(original))
        table.columns.toVector shouldBe original.reverse
      } finally lease.dispose()
      table.columns.setAll(original)
      created.size shouldBe 6
      original(1).visible = false
      created.count(_.isDisposed) shouldBe 2
      table.columns.setAll(original.reverse)
      created.size shouldBe 6
      original(1).visible = true
      created.size shouldBe 8
      // Browser commands do not mutate the server declaration.
      table.moveColumn(original.head, 0) shouldBe false
    } finally Runtime.unmount(root)
    all(created.map(_.isDisposed)) shouldBe true
  }
}
