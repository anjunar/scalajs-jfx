package jfx.bridge

import jfx.core.document.DocumentHead

import scala.scalajs.js

/** The JS projection of `DocumentHead.Handle`. Mirrors `contract.ts`'s `HeadGroupHandle`: a group
  * of head entries replaced as a whole on every [[set]], for an entry that changes without its
  * owning component going away.
  */
final class HeadGroupHandleBridge(private[bridge] final val underlying: DocumentHead.Handle)
    extends js.Object {

  def set(entries: js.Array[HeadEntryFacade]): Unit =
    underlying.set(entries.toSeq.map(HeadEntryFacade.toScala)*)

  def clear(): Unit = underlying.clear()

  def dispose(): Unit = underlying.dispose()
}
