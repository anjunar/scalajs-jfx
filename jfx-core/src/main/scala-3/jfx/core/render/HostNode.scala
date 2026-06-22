package jfx.core.render

import jfx.core.state.Disposable

trait HostNode {
  def renderHtml(): String
}

