package jfx.core.state

import scala.collection.mutable

/**
 * Die geladenen Ausschnitte einer Remote-Liste, als zusammenhaengende Bereiche.
 *
 * Vorher lag das als `mutable.Map[Int, V]` von absolutem Index auf Wert vor. Das
 * ist die falsche Struktur fuer den Zweck: die Map traegt keine Ordnung, also
 * musste jede Operation, die Ordnung braucht, erst sortieren -- `O(n log n)` pro
 * Einzel-Update in einer Klasse, deren Zweck grosse Datenmengen sind.
 *
 * Geladene Daten sind aber nie verstreute Einzelindizes, sondern Seiten: wenige
 * zusammenhaengende Bereiche mit Luecken dazwischen. Genau das bildet diese
 * Struktur ab. Alle Operationen laufen in `O(r)` oder `O(log r)`, wobei `r` die
 * Anzahl der Bereiche ist -- typischerweise eine Handvoll, unabhaengig davon, wie
 * viele Eintraege geladen sind.
 *
 * Invarianten:
 *   - `ranges` ist nach `start` aufsteigend sortiert.
 *   - Bereiche ueberlappen nicht und grenzen nicht aneinander; beruehrende
 *     Bereiche werden verschmolzen. Zwischen zwei Bereichen liegt also immer
 *     eine echte Luecke.
 *   - Kein Bereich ist leer.
 *
 * "Absolut" meint den Index in der vollstaendigen Remote-Liste, "dicht" den Index
 * in der ListProperty darunter, die nur die geladenen Eintraege haelt.
 */
private[state] final class LoadedRanges[V] {

  private final class Range(var start: Int, val items: mutable.ArrayBuffer[V]) {
    def length: Int                      = items.length
    def untilExclusive: Int              = start + items.length
    def contains(absolute: Int): Boolean = absolute >= start && absolute < untilExclusive
  }

  private val ranges = mutable.ArrayBuffer.empty[Range]

  def isEmpty: Boolean = ranges.isEmpty

  /** Anzahl geladener Eintraege insgesamt, also die Laenge der dichten Liste. */
  def size: Int = {
    var total = 0
    var index = 0
    while (index < ranges.length) {
      total += ranges(index).length
      index += 1
    }
    total
  }

  def clear(): Unit = ranges.clear()

  /**
   * Index des Bereichs, der `absolute` enthaelt. Ist er in keinem Bereich, wird
   * `-(Einfuegeposition) - 1` geliefert -- dieselbe Konvention wie
   * `java.util.Arrays.binarySearch`.
   */
  private def search(absolute: Int): Int = {
    var low  = 0
    var high = ranges.length - 1

    while (low <= high) {
      val mid   = (low + high) >>> 1
      val range = ranges(mid)

      if (absolute < range.start) high = mid - 1
      else if (absolute >= range.untilExclusive) low = mid + 1
      else return mid
    }

    -(low + 1)
  }

  def isLoaded(absolute: Int): Boolean =
    search(absolute) >= 0

  def get(absolute: Int): Option[V] = {
    val index = search(absolute)
    if (index < 0) None
    else {
      val range = ranges(index)
      Some(range.items(absolute - range.start))
    }
  }

  /** Ist `[from, untilExclusive)` vollstaendig geladen? Ein leerer Bereich gilt als geladen. */
  def isRangeLoaded(from: Int, untilExclusive: Int): Boolean =
    if (untilExclusive <= from) true
    else {
      val index = search(from)
      index >= 0 && ranges(index).untilExclusive >= untilExclusive
    }

  /** Hoechster geladener absoluter Index + 1, oder 0 wenn nichts geladen ist. */
  def nextSequentialAbsolute: Int =
    if (ranges.isEmpty) 0 else ranges.last.untilExclusive

  /** Anzahl geladener Eintraege mit absolutem Index kleiner als `absolute`. */
  def countBefore(absolute: Int): Int = {
    var count = 0
    var index = 0

    while (index < ranges.length && ranges(index).start < absolute) {
      val range = ranges(index)
      count += math.min(range.length, absolute - range.start)
      index += 1
    }

    count
  }

  /** Anzahl geladener Eintraege im Bereich `[from, untilExclusive)`. */
  def countIn(from: Int, untilExclusive: Int): Int =
    countBefore(untilExclusive) - countBefore(from)

  /** Absoluter Index an der dichten Position `position`. */
  def absoluteAt(position: Int): Int = {
    if (position < 0) throw IndexOutOfBoundsException(s"$position")

    var remaining = position
    var index     = 0

    while (index < ranges.length) {
      val range = ranges(index)
      if (remaining < range.length) return range.start + remaining
      remaining -= range.length
      index += 1
    }

    throw IndexOutOfBoundsException(s"$position")
  }

  /** Alle geladenen Werte in aufsteigender absoluter Reihenfolge. */
  def denseItems: Seq[V] =
    ranges.iterator.flatMap(_.items).toSeq

  /** Setzt einen einzelnen bereits geladenen Wert; ist er nicht geladen, wird er eingetragen. */
  def update(absolute: Int, value: V): Unit = {
    val index = search(absolute)
    if (index >= 0) {
      val range = ranges(index)
      range.items(absolute - range.start) = value
    } else {
      put(absolute, Seq(value))
    }
  }

  /**
   * Traegt eine zusammenhaengende Seite ein. Ueberlappende und angrenzende
   * Bereiche werden mit ihr verschmolzen; wo sich alte und neue Daten
   * ueberschneiden, gewinnen die neuen.
   */
  def put(startAbsolute: Int, items: Seq[V]): Unit = {
    if (items.isEmpty) return

    val newUntil = startAbsolute + items.length
    val result   = mutable.ArrayBuffer.empty[Range]

    var index = 0

    // Bereiche, die komplett davor liegen und nicht angrenzen, bleiben unberuehrt.
    while (index < ranges.length && ranges(index).untilExclusive < startAbsolute) {
      result += ranges(index)
      index += 1
    }

    // Ueberlappende und angrenzende Bereiche einsammeln.
    val touching = mutable.ArrayBuffer.empty[Range]
    while (index < ranges.length && ranges(index).start <= newUntil) {
      touching += ranges(index)
      index += 1
    }

    if (touching.isEmpty) {
      result += new Range(startAbsolute, mutable.ArrayBuffer.from(items))
    } else {
      // Der verschmolzene Bereich hat keine Luecken: die eingesammelten Bereiche
      // beruehren [startAbsolute, newUntil) jeweils mindestens, und Luecken
      // zwischen ihnen liegen vollstaendig innerhalb der neuen Seite.
      val first  = touching.head
      val last   = touching.last
      val buffer = mutable.ArrayBuffer.empty[V]

      if (first.start < startAbsolute) {
        buffer ++= first.items.view.slice(0, startAbsolute - first.start)
      }

      buffer ++= items

      if (last.untilExclusive > newUntil) {
        buffer ++= last.items.view.slice(newUntil - last.start, last.length)
      }

      result += new Range(math.min(startAbsolute, first.start), buffer)
    }

    while (index < ranges.length) {
      result += ranges(index)
      index += 1
    }

    ranges.clear()
    ranges ++= result
  }

  /**
   * Entfernt den Eintrag am absoluten Index und schiebt alles danach um eins
   * nach unten -- die Liste wird kuerzer, nicht luecken-behafteter.
   */
  def removeAt(absolute: Int): Unit = {
    val index = search(absolute)

    if (index >= 0) {
      val range = ranges(index)
      range.items.remove(absolute - range.start)
      if (range.items.isEmpty) ranges.remove(index)
    }

    var shiftFrom = 0
    while (shiftFrom < ranges.length && ranges(shiftFrom).start <= absolute) shiftFrom += 1
    while (shiftFrom < ranges.length) {
      ranges(shiftFrom).start -= 1
      shiftFrom += 1
    }

    mergeAdjacent()
  }

  /** Stellt die Invariante wieder her, dass Bereiche nicht aneinandergrenzen. */
  private def mergeAdjacent(): Unit = {
    var index = 0
    while (index < ranges.length - 1) {
      val current = ranges(index)
      val next    = ranges(index + 1)

      if (current.untilExclusive >= next.start) {
        val overlap = current.untilExclusive - next.start
        current.items ++= next.items.view.slice(overlap, next.length)
        ranges.remove(index + 1)
      } else {
        index += 1
      }
    }
  }
}
