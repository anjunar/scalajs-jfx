package ui.control.carousel

import ui.control.carousel.Carousel.Renderer
import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.ClassDsl.classIf
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

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
      addClass("ui-carousel__slide")
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
  )(
      body: CarouselSlide[?] ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): CarouselSlide[T] =
    DslLayer.child(
      new CarouselSlide[T](carousel, item, index, count, renderer, observeActiveIndex)
    ) {
      body
    }
}
