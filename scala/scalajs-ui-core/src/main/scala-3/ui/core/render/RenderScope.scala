package ui.core.render

import ui.core.async.AsyncRenderContext
import ui.core.component.AbstractComponent

final case class RenderScope(cursor: Cursor, parent: AbstractComponent, async: AsyncRenderContext)
