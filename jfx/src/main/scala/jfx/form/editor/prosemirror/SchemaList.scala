package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("prosemirror-schema-list", JSImport.Namespace)
object SchemaList extends js.Object {
  def addListNodes(nodes: js.Any, itemContent: String, listGroup: String = js.native): js.Any = js.native

  def wrapInList(listType: NodeType, attrs: js.Any = js.native): Command = js.native
  def splitListItem(itemType: NodeType): Command = js.native
  def liftListItem(itemType: NodeType): Command = js.native
  def sinkListItem(itemType: NodeType): Command = js.native

  val orderedList: js.Any = js.native
  val bulletList: js.Any = js.native
  val listItem: js.Any = js.native
}
