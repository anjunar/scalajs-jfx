package jfx.core.render

import jfx.core.async.AsyncRenderContext
import jfx.core.component.AbstractComponent

final case class RenderScope(cursor: Cursor,
                             parent: AbstractComponent,
                             async: AsyncRenderContext)