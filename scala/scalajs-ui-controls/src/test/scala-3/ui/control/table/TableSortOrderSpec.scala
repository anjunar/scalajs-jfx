package ui.control.table

import ui.core.remote.RemoteSort
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TableSortOrderSpec extends AnyFlatSpec with Matchers {
  private val author = RemoteSort("author", ascending = true)
  private val year   = RemoteSort("year", ascending = true)

  private def column(key: String): TableColumn[String, String] = {
    val result = new TableColumn[String, String](key)
    result.sortKeyProperty.set(Some(key))
    result.sortableProperty.set(true)
    result
  }

  "Explicit sort order" should "resolve a complete ordered snapshot with explicit directions" in {
    val a = column(" author "); val y = column("year")
    TableSortOrder.resolve(Seq(TableSort(y, false), TableSort(a)), Seq(a, y)) shouldBe
      Some(Vector(year.copy(ascending = false), author))
    TableSortOrder.resolve(Seq.empty[TableSort[String]], Seq(a, y)) shouldBe Some(Vector.empty)
  }

  it should "reject duplicate remote keys, even through different column instances" in {
    val a = column("author"); val duplicate = column(" author ")
    TableSortOrder.resolve(Seq(TableSort(a), TableSort(a, false)), Seq(a)) shouldBe None
    TableSortOrder.resolve(Seq(TableSort(a), TableSort(duplicate)), Seq(a, duplicate)) shouldBe None
  }

  it should "reject an entire request when one column is unavailable, locked, disposed or unkeyed" in {
    val a       = column("author"); val y = column("year")
    val request = Seq(TableSort(a), TableSort(y))
    TableSortOrder.resolve(request, Seq(a)) shouldBe None
    y.sortableProperty.set(false)
    TableSortOrder.resolve(request, Seq(a, y)) shouldBe None
    y.sortableProperty.set(true); y.sortKeyProperty.set(Some("  "))
    TableSortOrder.resolve(request, Seq(a, y)) shouldBe None
    y.sortKeyProperty.set(Some("year")); y.dispose()
    TableSortOrder.resolve(request, Seq(a, y)) shouldBe None
    TableSortOrder.resolve(null, Seq(a)) shouldBe None
    TableSortOrder.resolve(Seq(null), Seq(a)) shouldBe None
    TableSortOrder.resolve(Seq(TableSort[String](null)), Seq(a)) shouldBe None
  }

  "Remote sort descriptors" should "cycle a single column without preserving unrelated terms" in {
    TableSortOrder.toggle(Vector.empty, "author", false) shouldBe Vector(author)
    TableSortOrder.toggle(Vector(author, year), "author", false) shouldBe Vector(
      author.copy(ascending = false)
    )
    TableSortOrder.toggle(
      Vector(author.copy(ascending = false), year),
      "author",
      false
    ) shouldBe Vector.empty
  }

  it should "append, update in place and remove additive terms without reordering the others" in {
    TableSortOrder.toggle(Vector(author), "year", true) shouldBe Vector(author, year)
    val descending = Vector(author, year.copy(ascending = false))
    TableSortOrder.toggle(Vector(author, year), "year", true) shouldBe descending
    TableSortOrder.toggle(descending, "year", true) shouldBe Vector(author)
    TableSortOrder.toggle(
      Vector(author.copy(ascending = false), year),
      "author",
      true
    ) shouldBe Vector(year)
  }
}
