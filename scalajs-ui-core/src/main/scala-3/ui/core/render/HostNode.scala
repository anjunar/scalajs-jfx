package ui.core.render

import ui.core.state.Disposable

trait HostNode {
  def renderHtml(): String
}
