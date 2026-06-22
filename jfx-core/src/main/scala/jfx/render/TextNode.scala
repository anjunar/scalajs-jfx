package jfx.render

trait TextNode extends HostNode {
  def setText(value: String): Unit
  def getText: String
}
