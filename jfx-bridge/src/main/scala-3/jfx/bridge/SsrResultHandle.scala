package jfx.bridge

import scala.scalajs.js

/** Mirrors `contract.ts`'s `SsrResult`. Every field is fixed at construction, so these are plain
  * `val`s -- not the live-reading `def`s [[PropertyHandle]] and friends need -- and still compile to
  * JS fields either way.
  */
final class SsrResultHandle(
    val html: String,
    val status: Int,
    val headers: js.Dictionary[String]
) extends js.Object

/** Mirrors `contract.ts`'s `SsrOptions`. Native: [[JfxRuntimeBridge.renderToString]] only ever reads
  * one, never builds one.
  */
@js.native
trait SsrOptionsFacade extends js.Object {
  val timeoutMs: js.UndefOr[Double] = js.native
}
