package jfx.core.statement

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

/** Skalierungsverhalten von [[Foreach]].
  *
  * Grundlage für CHANGE.md P4-2. Schritt 1 der Aufgabe verlangt ausdrücklich, erst zu messen -- und
  * das war gut so: die Vermutung dort (`domOffset`, `domNodeCount`, `physicalHosts`) traf nicht zu.
  * Die beiden erstgenannten rief niemand auf, und der Aufbau hängt gar nicht an ihnen. Gemessen lag
  * die quadratische Zeit an der linearen Suche nach der Einfügemarke in
  * `SsrHostElement.insertBefore`.
  *
  * Die Messung prüft nicht eine absolute Zeit, sondern die Form der Kurve: das Vierfache an
  * Elementen darf nicht das Sechzehnfache an Zeit kosten. Absolute Schwellen wären auf fremder
  * Hardware wertlos.
  */
class ForeachScalingSpec extends AnyFlatSpec with Matchers {

  "Foreach" should "build a large list in roughly linear time" in {
    // Einmal aufwärmen, damit die JIT-Kosten nicht in der ersten Messung landen.
    buildMillis(500)

    val small = buildMillis(1250)
    val large = buildMillis(5000)

    val factor = large / math.max(small, 0.01)

    info(f"Aufbau  1250 Elemente: $small%.1f ms")
    info(f"Aufbau  5000 Elemente: $large%.1f ms")
    info(f"Faktor bei vierfacher Menge: $factor%.1f (linear waere ~4, quadratisch ~16)")

    factor should be < 8.0
  }

  it should "insert into the middle of a large list without walking the whole subtree" in {
    val items = ListProperty[String](js.Array((0 until 2000).map(index => s"Item $index")*))
    val root  = Runtime.mount(new ScalingRoot(items), new SsrCursor())

    val startedAt = System.nanoTime()
    (0 until 200).foreach(step => items.insert(1000 + step, s"eingefuegt $step"))
    val elapsed = (System.nanoTime() - startedAt) / 1000000.0

    info(f"200 Einfuegungen in die Mitte von 2000: $elapsed%.1f ms")

    items.length shouldBe 2200
    elapsed should be < 4000.0
  }

  private def buildMillis(count: Int): Double = {
    val items = ListProperty[String](js.Array((0 until count).map(index => s"Item $index")*))

    val startedAt = System.nanoTime()
    Runtime.mount(new ScalingRoot(items), new SsrCursor())
    (System.nanoTime() - startedAt) / 1000000.0
  }
}

private final class ScalingRoot(items: ListProperty[String]) extends AbstractComponent {
  override val tagName: String = "ul"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      DslLayer.child(
        new Foreach[String](items, (value, _) => div { text(value) {} })
      ) {}
    }
}
