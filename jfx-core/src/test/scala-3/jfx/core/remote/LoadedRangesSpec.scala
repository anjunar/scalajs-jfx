package jfx.core.remote

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Das Verhalten bei Luecken war vorher implizit -- es ergab sich daraus, dass eine
 * Map keine Ordnung hat und jede Operation neu sortierte. Hier ist es
 * festgeschrieben.
 */
class LoadedRangesSpec extends AnyFlatSpec with Matchers {

  "LoadedRanges" should "start empty" in {
    val ranges = new LoadedRanges[String]

    ranges.isEmpty shouldBe true
    ranges.size shouldBe 0
    ranges.nextSequentialAbsolute shouldBe 0
    ranges.denseItems shouldBe empty
    ranges.isLoaded(0) shouldBe false
    ranges.get(0) shouldBe None
  }

  it should "hold a single page" in {
    val ranges = new LoadedRanges[String]
    ranges.put(10, Seq("a", "b", "c"))

    ranges.size shouldBe 3
    ranges.isLoaded(9) shouldBe false
    ranges.isLoaded(10) shouldBe true
    ranges.isLoaded(12) shouldBe true
    ranges.isLoaded(13) shouldBe false
    ranges.get(11) shouldBe Some("b")
    ranges.nextSequentialAbsolute shouldBe 13
    ranges.denseItems shouldBe Seq("a", "b", "c")
  }

  it should "keep gaps between pages" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(10, Seq("k", "l"))

    ranges.size shouldBe 4
    ranges.isLoaded(2) shouldBe false
    ranges.isLoaded(9) shouldBe false
    ranges.denseItems shouldBe Seq("a", "b", "k", "l")
  }

  it should "map dense positions across a gap" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(10, Seq("k", "l"))

    ranges.absoluteAt(0) shouldBe 0
    ranges.absoluteAt(1) shouldBe 1
    ranges.absoluteAt(2) shouldBe 10
    ranges.absoluteAt(3) shouldBe 11
    an[IndexOutOfBoundsException] should be thrownBy ranges.absoluteAt(4)
    an[IndexOutOfBoundsException] should be thrownBy ranges.absoluteAt(-1)
  }

  it should "count entries before an absolute index and inside a range" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(10, Seq("k", "l", "m"))

    ranges.countBefore(0) shouldBe 0
    ranges.countBefore(1) shouldBe 1
    ranges.countBefore(2) shouldBe 2
    ranges.countBefore(10) shouldBe 2
    ranges.countBefore(11) shouldBe 3
    ranges.countBefore(100) shouldBe 5

    ranges.countIn(0, 2) shouldBe 2
    ranges.countIn(2, 10) shouldBe 0
    ranges.countIn(10, 13) shouldBe 3
    ranges.countIn(1, 11) shouldBe 2
  }

  it should "answer isRangeLoaded across and inside gaps" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(10, Seq("k", "l"))

    ranges.isRangeLoaded(0, 2) shouldBe true
    ranges.isRangeLoaded(0, 3) shouldBe false
    ranges.isRangeLoaded(2, 10) shouldBe false
    ranges.isRangeLoaded(10, 12) shouldBe true
    ranges.isRangeLoaded(5, 5) shouldBe true
  }

  it should "merge an adjacent page" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(2, Seq("c", "d"))

    ranges.denseItems shouldBe Seq("a", "b", "c", "d")
    ranges.isRangeLoaded(0, 4) shouldBe true
    ranges.nextSequentialAbsolute shouldBe 4
  }

  it should "let a new page win where it overlaps an old one" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b", "c", "d"))
    ranges.put(2, Seq("X", "Y"))

    ranges.denseItems shouldBe Seq("a", "b", "X", "Y")
  }

  it should "bridge a gap when a page spans two existing ranges" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(6, Seq("g", "h"))
    ranges.put(2, Seq("c", "d", "e", "f"))

    ranges.denseItems shouldBe Seq("a", "b", "c", "d", "e", "f", "g", "h")
    ranges.isRangeLoaded(0, 8) shouldBe true
  }

  it should "keep the tail of an overlapped range" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b", "c", "d", "e"))
    ranges.put(1, Seq("X", "Y"))

    ranges.denseItems shouldBe Seq("a", "X", "Y", "d", "e")
  }

  it should "update a loaded value in place" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b", "c"))
    ranges.update(1, "B")

    ranges.denseItems shouldBe Seq("a", "B", "c")
    ranges.size shouldBe 3
  }

  it should "insert when updating an index that is not loaded" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a"))
    ranges.update(5, "f")

    ranges.get(5) shouldBe Some("f")
    ranges.denseItems shouldBe Seq("a", "f")
  }

  it should "shift later indices down on removal" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(10, Seq("k", "l"))

    ranges.removeAt(0)

    ranges.denseItems shouldBe Seq("b", "k", "l")
    ranges.get(0) shouldBe Some("b")
    // Der hintere Bereich ist um eins nach unten gerueckt.
    ranges.get(9) shouldBe Some("k")
    ranges.get(10) shouldBe Some("l")
  }

  it should "drop a range that becomes empty" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a"))
    ranges.put(10, Seq("k"))

    ranges.removeAt(0)

    ranges.size shouldBe 1
    ranges.denseItems shouldBe Seq("k")
    ranges.get(9) shouldBe Some("k")
  }

  it should "keep the gap width when a loaded entry is removed" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(3, Seq("d"))

    // Luecke ist genau ein Index (2). Entfernt man einen geladenen Eintrag davor,
    // schrumpfen Bereich und Folge-Start gleichermassen -- die Luecke wandert,
    // sie schliesst sich nicht.
    ranges.removeAt(0)

    ranges.denseItems shouldBe Seq("b", "d")
    ranges.get(0) shouldBe Some("b")
    ranges.isLoaded(1) shouldBe false
    ranges.get(2) shouldBe Some("d")
  }

  it should "merge ranges when the removed index sits in the gap" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a", "b"))
    ranges.put(3, Seq("d"))

    // Index 2 ist nicht geladen. Er faellt trotzdem weg, alles danach rueckt auf
    // -- damit grenzen die beiden Bereiche aneinander und werden verschmolzen.
    ranges.removeAt(2)

    ranges.denseItems shouldBe Seq("a", "b", "d")
    ranges.isRangeLoaded(0, 3) shouldBe true
    ranges.get(2) shouldBe Some("d")
  }

  it should "ignore an empty page" in {
    val ranges = new LoadedRanges[String]
    ranges.put(0, Seq("a"))
    ranges.put(5, Seq.empty)

    ranges.denseItems shouldBe Seq("a")
  }
}
