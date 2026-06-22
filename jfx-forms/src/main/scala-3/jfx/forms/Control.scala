package jfx.forms

import jfx.core.state.Disposable

trait Control {

  val name : String

  def addDisposable(d: Disposable): Unit

}
