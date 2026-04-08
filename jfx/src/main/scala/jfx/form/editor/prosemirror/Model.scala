package jfx.form.editor.prosemirror

import org.scalajs.dom.{Node as DomNode}

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation.{JSImport, JSName}

@js.native
@JSImport("prosemirror-model", "Schema")
class Schema(schemaSpec: SchemaSpec = js.native) extends js.Object {
  val nodes: js.Dynamic = js.native
  val marks: js.Dynamic = js.native
  val spec: SchemaSpec = js.native

  def nodeFromJSON(json: js.Any): PMNode = js.native
}

trait SchemaSpec extends js.Object {
  var nodes: js.Any
  var marks: js.Any
  var topNode: js.UndefOr[String]
}

@js.native
@JSImport("prosemirror-model", "Node")
class PMNode extends js.Object {
  @JSName("type")
  val nodeType: NodeType = js.native
  val attrs: js.Dynamic = js.native
  val marks: js.Array[Mark] = js.native
  val content: Fragment = js.native

  def nodeAt(pos: Int): PMNode | Null = js.native
  def rangeHasMark(from: Int, to: Int, markType: MarkType): Boolean = js.native

  def nodesBetween(
    from: Int,
    to: Int,
    f: js.Function4[PMNode, Int, PMNode, Int, Boolean],
    startPos: Int = js.native
  ): Unit = js.native

  def toJSON(): js.Any = js.native
}

@js.native
@JSImport("prosemirror-model", "NodeType")
class NodeType extends js.Object {
  val name: String = js.native

  def create(
    attrs: js.Any = js.native,
    content: Fragment = js.native,
    marks: js.Array[Mark] = js.native
  ): PMNode = js.native
}

@js.native
@JSImport("prosemirror-model", "Mark")
class Mark extends js.Object {
  @JSName("type")
  val markType: MarkType = js.native
  val attrs: js.Dynamic = js.native
}

@js.native
@JSImport("prosemirror-model", "MarkType")
class MarkType extends js.Object {
  val name: String = js.native

  def isInSet(set: js.Array[Mark]): Mark | Null = js.native
}

@js.native
@JSImport("prosemirror-model", "Fragment")
class Fragment extends js.Object

@js.native
@JSImport("prosemirror-model", "Fragment")
object Fragment extends js.Object {
  def from(node: PMNode): Fragment = js.native
}

@js.native
@JSImport("prosemirror-model", "Slice")
class Slice(content: Fragment, openStart: Int, openEnd: Int) extends js.Object

@js.native
@JSImport("prosemirror-model", "ResolvedPos")
class ResolvedPos extends js.Object {
  val pos: Int = js.native
  val parent: PMNode = js.native

  def marks(): js.Array[Mark] = js.native
}

@js.native
@JSImport("prosemirror-model", "DOMParser")
class DOMParser() extends js.Object {
  def parse(dom: DomNode, options: ParseOptions = js.native): PMNode = js.native
}

@js.native
@JSImport("prosemirror-model", "DOMParser")
object DOMParser extends js.Object {
  def fromSchema(schema: Schema): DOMParser = js.native
}

trait ParseOptions extends js.Object {
  var preserveWhitespace: js.UndefOr[Boolean | String]
}

@js.native
@JSImport("prosemirror-model", "DOMSerializer")
class DOMSerializer() extends js.Object {
  def serializeFragment(fragment: Fragment, options: js.Any = js.native): DomNode = js.native
}

@js.native
@JSImport("prosemirror-model", "DOMSerializer")
object DOMSerializer extends js.Object {
  def fromSchema(schema: Schema): DOMSerializer = js.native
}

trait ParseRule extends js.Object {
  var tag: js.UndefOr[String]
  var getAttrs: js.UndefOr[js.Function1[DomNode, js.Any]]
}

trait AttrSpec extends js.Object {
  var default: js.UndefOr[js.Any]
}

trait NodeSpec extends js.Object {
  var inline: js.UndefOr[Boolean]
  var group: js.UndefOr[String]
  var draggable: js.UndefOr[Boolean]
  var attrs: js.UndefOr[js.Any]
  var parseDOM: js.UndefOr[js.Array[ParseRule]]
  var toDOM: js.UndefOr[js.Function1[js.Dynamic, js.Any]]
}
