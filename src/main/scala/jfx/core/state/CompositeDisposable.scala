package jfx.core.state

import scala.scalajs.js

class CompositeDisposable extends Disposable {

  private val items = js.Array[Disposable]()
  
  def add(item : Disposable) : Unit = items += item

  def dispose() : Unit = {
    items.foreach(_.dispose())
    items.clear()
  }

}
