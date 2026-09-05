package jfx.bridge

import jfx.core.document.{DocumentHead, HeadEntry}

import scala.scalajs.js

/** The JS projection of a `contract.ts` `HeadEntry` -- data only, exactly what `document.ts`'s
  * factories build. Attributes cross as an array of `[name, value]` pairs, the same shape used for
  * every other ordered-pairs value at this boundary.
  */
@js.native
trait HeadEntryFacade extends js.Object {
  val key: String                                        = js.native
  val tagName: String                                    = js.native
  val attributes: js.UndefOr[js.Array[js.Array[String]]] = js.native
  val text: js.UndefOr[String]                           = js.native
  val rawText: js.UndefOr[Boolean]                       = js.native
}

object HeadEntryFacade {
  def toScala(facade: HeadEntryFacade): HeadEntry =
    HeadEntry(
      key = facade.key,
      tagName = facade.tagName,
      attributes = facade.attributes.toOption
        .map(_.toSeq.map(pair => pair(0) -> pair(1)))
        .getOrElse(Nil),
      text = facade.text.toOption,
      rawText = facade.rawText.getOrElse(false)
    )
}

/** The JS projection of `jfx.core.document.DocumentHead`. Mirrors `contract.ts`'s
  * `DocumentHeadHandle`.
  */
final class DocumentHeadHandleBridge(private[bridge] final val underlying: DocumentHead)
    extends js.Object {

  def push(entry: HeadEntryFacade): DisposableHandle =
    new DisposableHandle(underlying.push(HeadEntryFacade.toScala(entry)))

  def htmlAttribute(name: String, value: String): Unit =
    underlying.htmlAttribute(name, value)

  def removeHtmlAttribute(name: String): Unit =
    underlying.removeHtmlAttribute(name)

  def handle(): HeadGroupHandleBridge =
    new HeadGroupHandleBridge(underlying.handle())
}
