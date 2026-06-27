package jfx.core.dsl

import jfx.core.component.{AsyncSlot, Runtime}
import jfx.core.render.RenderScope

import scala.concurrent.{ExecutionContext, Future}

object AsyncDsl {

  def async(
      body: RenderScope ?=> Future[Unit]
  )(using scope: RenderScope, ec: ExecutionContext): Unit = {
    val slot = Runtime.mount(
      new AsyncSlot(),
      scope.cursor,
      Some(scope.parent)
    )

    val slotCursor = scope.cursor

    val asyncScope =
      RenderScope(
        cursor = slotCursor,
        parent = slot,
        async = scope.async
      )

    scope.async.add {
      body(using asyncScope)
    }
  }
}
