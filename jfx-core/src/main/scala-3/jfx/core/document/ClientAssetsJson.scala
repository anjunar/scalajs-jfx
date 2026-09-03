package jfx.core.document

import scala.scalajs.js
import scala.scalajs.js.JSON

/** The bundler's script and stylesheet tags, as [[HeadEntry]]s.
  *
  * One thing in the document cannot come from Scala: the names of the built assets. They carry a
  * content hash that only the bundler knows, and in development they do not exist as files at all.
  * So they arrive the same way the request headers do -- as an argument to the render -- and become
  * ordinary head entries from there. That keeps the document itself entirely rendered rather than
  * assembled from string fragments afterwards.
  *
  * Expected shape:
  * {{{
  * [ { "tag": "script", "attributes": { "type": "module", "src": "/assets/main-a1b2.js" } },
  *   { "tag": "link",   "attributes": { "rel": "stylesheet", "href": "/assets/main-a1b2.css" } } ]
  * }}}
  *
  * Keys are derived from the position, because a build emits its assets in a fixed order and
  * nothing else in the head addresses them. In the browser they are never re-registered: the sink
  * leaves a server-rendered node alone until something claims its key -- see [[BrowserHeadSink]].
  */
object ClientAssetsJson {

  def parse(json: String): Seq[HeadEntry] =
    if (json == null || json.trim.isEmpty) Nil
    else {
      val parsed = JSON.parse(json)

      if (!js.Array.isArray(parsed)) {
        throw new IllegalArgumentException(
          s"Client assets must be a JSON array, got: ${json.take(80)}"
        )
      }

      parsed
        .asInstanceOf[js.Array[js.Dynamic]]
        .toSeq
        .zipWithIndex
        .map { case (asset, index) => entryFor(asset, index) }
    }

  private def entryFor(asset: js.Dynamic, index: Int): HeadEntry = {
    val tag = asset.tag

    if (js.isUndefined(tag) || tag == null) {
      throw new IllegalArgumentException(s"Client asset $index has no \"tag\".")
    }

    val attributes =
      Option(asset.attributes.asInstanceOf[js.Dictionary[js.Any]])
        .filterNot(js.isUndefined(_))
        .map(_.toSeq.map { case (name, value) => name -> value.toString })
        .getOrElse(Nil)

    HeadEntry(s"asset:$index", tag.toString, attributes)
  }
}
