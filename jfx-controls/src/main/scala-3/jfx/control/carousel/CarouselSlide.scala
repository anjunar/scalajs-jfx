package jfx.control.carousel

import jfx.control.carousel.Carousel.Renderer
import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.ClassDsl.classIf
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

private final class CarouselSlide[T](
    carousel: Carousel[T],
    item: T,
    index: Int,
    count: Int,
    renderer: Renderer[T],
    observeActiveIndex: Boolean
) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-carousel__slide")
      setAttribute("role", "group")
      setAttribute("aria-roledescription", "slide")
      setAttribute("aria-label", s"${index + 1} of $count")
      setAttribute("data-slide-index", index.toString)

      if (observeActiveIndex) {
        val active = carousel.activeIndexProperty.map(_ == index)
        classIf("is-active", active)
        addDisposable(active.observe { selected =>
          setAttribute("aria-hidden", (!selected).toString)
        })
      } else {
        addClass("is-active")
        setAttribute("aria-hidden", "false")
      }

      renderer(item, index)(using this)(using cursor)
    }
}

object CarouselSlide {
  def carouselSlide[T](
      carousel: Carousel[T],
      item: T,
      index: Int,
      count: Int,
      renderer: Renderer[T],
      observeActiveIndex: Boolean
  )(body: CarouselSlide[?] ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): CarouselSlide[T] =
    DslLayer.child(new CarouselSlide[T](carousel, item, index, count, renderer, observeActiveIndex)) {
      body
    }
}
