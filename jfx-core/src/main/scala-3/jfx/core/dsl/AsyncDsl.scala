package jfx.core.dsl

import jfx.core.component.{AsyncSlot, DynamicMountPoint, Runtime}
import jfx.core.render.{RenderScope, VirtualHost}

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

    val initialCursor =
      slot._host match {
        case host: VirtualHost => host.cursor.getOrElse(scope.cursor)
        case _                 => scope.cursor
      }
    val mountPoint = new DynamicMountPoint(slot, initialCursor)

    val asyncScope =
      RenderScope(
        cursor = mountPoint.appendCursor,
        parent = slot,
        async = scope.async
      )

    scope.async.add {
      body(using asyncScope).map { _ =>
        mountPoint.finishInitialComposition()
      }
    }
  }
}
