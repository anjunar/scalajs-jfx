package jfx.core.layout

import jfx.core.async.AsyncRenderContext
import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, VirtualHost}
import jfx.core.component.Runtime
import jfx.core.dsl.DslLayerTwo.render

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FetchComponent[A](load: () => Future[A])(renderLoaded: A => AbstractComponent ?=> Cursor ?=> Unit)(ec: ExecutionContext) extends AbstractComponent {

  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit = {
    
    render(this, cursor) {
      val slotCursor =
        _host match {
          case host: VirtualHost => host.cursor.getOrElse(cursor)
          case _ => cursor
        }

      val async = Runtime.AsyncContext.inject.get

      async.add {
        load().map { value =>
          given AbstractComponent = this

          given Cursor = slotCursor

          renderLoaded(value)
        }
      }
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