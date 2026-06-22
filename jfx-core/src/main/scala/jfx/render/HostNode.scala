package jfx.render

import jfx.state.Disposable

trait HostNode {
  def renderHtml(): String
}

