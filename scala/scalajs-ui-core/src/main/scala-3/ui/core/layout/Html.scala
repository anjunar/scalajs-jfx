package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

/** The document element.
  *
  * Its attributes -- `lang`, `dir` -- are not set here but through
  * [[ui.core.document.DocumentHead.htmlAttribute]], because the locale is known further down the
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
