package jfx.router

trait PageInfo {

  def name: String

  def pageWidth: Int = -1
  def pageHeight: Int = -1

  def resizable: Boolean = true

  var close: () => Unit = () => ()
}
