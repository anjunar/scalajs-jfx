package jfx.core.layout

import jfx.core.component.{AbstractComponent, DynamicMountPoint}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, VirtualHost}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class FetchComponent[A](
    load: () => Future[A]
)(
    renderLoaded: A => AbstractComponent ?=> Cursor ?=> Unit
)(
    renderFailed: Throwable => AbstractComponent ?=> Cursor ?=> Unit
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

    val rendering =
      Future
        .fromTry(Try(load()))
        .flatten
        .transform { result =>
          Try {
            try {
              if (isBound) {
                given AbstractComponent = this
                given Cursor            = mountPoint.appendCursor

                result.fold(renderFailed, renderLoaded)
              }
            } finally {
              mountPoint.finishInitialComposition()
            }
          }
        }(ec)

    cursor.asyncContext match {
      case Some(async) => async.add(rendering)
      case None        => rendering.failed.foreach(ec.reportFailure)(ec)
    }
  }
}

object FetchComponent {

  def fetch[A](
      load: () => Future[A]
  )(
      renderLoaded: A => AbstractComponent ?=> Cursor ?=> Unit
  )(
      renderFailed: Throwable => AbstractComponent ?=> Cursor ?=> Unit
  )(using
      parent: AbstractComponent,
      cursor: Cursor,
      ec: ExecutionContext
  ): FetchComponent[A] =
    DslLayer.child(new FetchComponent(load)(renderLoaded)(renderFailed)(ec)) {}
}
