package jfx.core.render

trait TextNode extends HostNode {
  def setText(value: String): Unit
  def getText: String
}
