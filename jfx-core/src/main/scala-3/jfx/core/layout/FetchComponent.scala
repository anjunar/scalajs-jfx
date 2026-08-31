package jfx.core.layout

import jfx.core.component.{AbstractComponent, DynamicMountPoint}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, VirtualHost}

import scala.concurrent.{ExecutionContext, Future}

class FetchComponent[A](
    load: () => Future[A]
)(
    renderLoaded: A => AbstractComponent ?=> Cursor ?=> Unit
)(
    ec: ExecutionContext
) extends AbstractComponent {

  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit = {
    val initialCursor =
      _host match {
        case host: VirtualHost => host.cursor.getOrElse(cursor)
        case _                 => cursor
      }
    val mountPoint = new DynamicMountPoint(this, initialCursor)

    cursor.asyncContext match {
      case Some(async) =>
        async.add {
          load().map { value =>
            given AbstractComponent = this
            given Cursor            = mountPoint.appendCursor

            renderLoaded(value)
            mountPoint.finishInitialComposition()
          }(ec)
        }

      case None =>
        mountPoint.finishInitialComposition()
    }
  }
}

object FetchComponent {

  def fetch[A](
      load: () => Future[A]
  )(
      renderLoaded: A => AbstractComponent ?=> Cursor ?=> Unit
  )(using
      parent: AbstractComponent,
      cursor: Cursor,
      ec: ExecutionContext
  ): FetchComponent[A] =
    DslLayer.child(new FetchComponent(load)(renderLoaded)(ec)) {}
}
