package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.document.{DocumentHead, HeadSink}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

/** The `<head>` element, filled by the [[DocumentHead]] of the surrounding tree.
  *
  * It composes no children of its own. What belongs in a head is decided by pages and components
  * that compose long after it, so the element only provides the place to write to: once it is
  * mounted it hands its host to the [[DocumentHead]], which writes into it from then on.
  *
  * Consequently it needs one. Rendering a head that quietly stays empty because nobody provided a
  * [[DocumentHead]] is the failure mode ARCHITECTURE.md §7 rules out, so this reports it.
  */
class Head extends AbstractComponent {
  val tagName = "head"

  override def afterCompose(cursor: Cursor): Unit = {
    val documentHead = DocumentHead.requireCurrent(using this)
    documentHead.connect(HeadSink(cursor, host, _mountParentHost))
  }
}

object Head {
  def head(body: Head ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Head =
    DslLayer.child(new Head()) {
      body
    }
}
