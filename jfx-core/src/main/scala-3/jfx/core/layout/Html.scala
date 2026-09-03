package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

/** The document element.
  *
  * Its attributes -- `lang`, `dir` -- are not set here but through
  * [[jfx.core.document.DocumentHead.htmlAttribute]], because the locale is known further down the
  * tree than the document element is composed.
  */
class Html extends AbstractComponent {
  val tagName = "html"
}

object Html {
  def html(body: Html ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Html =
    DslLayer.child(new Html()) {
      body
    }
}
