package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo
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
    val slotCursor =
      _host match {
        case host: VirtualHost => host.cursor.getOrElse(cursor)
        case _                 => cursor
      }

    cursor.asyncContext match {
      case Some(async) =>
        async.add {
          load().map { value =>
            given AbstractComponent = this
            given Cursor            = slotCursor

            renderLoaded(value)
          }(ec)
        }

      case None =>
        ()
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
    DslLayerTwo.child(new FetchComponent(load)(renderLoaded)(ec)) {}
}
