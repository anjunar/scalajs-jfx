package jfx.control.table

import jfx.core.remote.RemoteSort
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TableSortOrderSpec extends AnyFlatSpec with Matchers {
  private val author = RemoteSort("author", ascending = true)
  private val year   = RemoteSort("year", ascending = true)

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
