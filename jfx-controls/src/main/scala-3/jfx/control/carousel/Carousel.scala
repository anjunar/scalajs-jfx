package jfx.control.carousel

import jfx.control.carousel.Carousel.Renderer
import jfx.control.carousel.CarouselSlide.carouselSlide
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.{button, buttonType}
import jfx.core.layout.Condition.when
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, UiEvent}
import jfx.core.state.{Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.core.statement.Foreach.foreachIndexed

import scala.scalajs.js

final class Carousel[T] private[carousel] (
    configure: Carousel[T] ?=> Cursor ?=> Unit,
    intervalScheduler: IntervalScheduler = IntervalScheduler.browser
) extends AbstractComponent {

  override val tagName: String = "section"

  private val itemsRefProperty: Property[ListProperty[T]] = Property(ListProperty[T]())

  val activeIndexProperty: Property[Int]          = Property(0)
  val autoAdvanceMsProperty: Property[Int]        = Property(0)
  val wrapAroundProperty: Property[Boolean]       = Property(true)
  val ssrShowAllStatesProperty: Property[Boolean] = Property(true)

  private val itemStateRevisionProperty = Property(0)
  private val contentRevisionProperty   = Property(0)
  private val navigationRevisionProperty = Property(0)
  private val activeSlideProperty: Property[AbstractComponent] =
    Property(new EmptyCarouselContent)

  private var slideRenderer: Renderer[T] = Carousel.emptyRenderer[T]
  private var itemsObserver: Disposable  = Disposable.empty
  private var autoAdvance: Disposable    = Disposable.empty
  private var browserRendering           = false
  private var compositionReady           = false

  def itemsProperty: Property[ListProperty[T]]  = itemsRefProperty
  def getItems: ListProperty[T]                  = itemsRefProperty.get
  def items: ListProperty[T]                     = getItems
  def items_=(value: ListProperty[T]): Unit      = setItems(value)

  def setItems(value: ListProperty[T] | Null): Unit = {
    val normalized = Option(value).getOrElse(ListProperty[T]())
    if (!itemsRefProperty.get.eq(normalized)) itemsRefProperty.setAlways(normalized)
  }

  def setRenderer(renderer: Renderer[T] | Null): Unit = {
    slideRenderer = Option(renderer).getOrElse(Carousel.emptyRenderer[T])
    if (compositionReady) {
      bumpContentRevision()
      rebuildActiveSlide()
    }
  }

  def getRenderer: Renderer[T] = slideRenderer

  def currentItem: Option[T] =
    if (slideCount == 0) None else Some(getItems(normalizedActiveIndex))

  def goTo(index: Int): Unit =
    activeIndexProperty.set(normalizeIndex(index))

  def next(): Unit =
    if (slideCount > 1) goTo(normalizedActiveIndex + 1)

  def previous(): Unit =
    if (slideCount > 1) goTo(normalizedActiveIndex - 1)

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser

    // Items, renderer and initial state are structural configuration and must
    // be known before the dynamic ranges claim SSR or hydration nodes.
    configure(using this)(using cursor)
    activeIndexProperty.set(normalizedActiveIndex)
    rewireItemsObserver()
    rebuildActiveSlide()

    val renderedItemsProperty: ReadOnlyProperty[Seq[T]] =
      contentRevisionProperty.map(_ => getItems.toSeq)
    val indicatorItemsProperty: ReadOnlyProperty[Seq[T]] =
      itemStateRevisionProperty.map(_ => getItems.toSeq)
    val multipleItemsProperty =
      itemStateRevisionProperty.map(_ => slideCount > 1)
    val statusTextProperty = navigationRevisionProperty.map(_ => statusText)
    val trackTransformProperty =
      activeIndexProperty.flatMap { index =>
        ssrShowAllStatesProperty.map { showAll =>
          if (!browserRendering || !showAll) "none"
          else s"translateX(-${normalizeIndex(index) * 100}%)"
        }
      }

    DslLayer.render(this, cursor) {
      addClass("jfx-carousel")
      classIf(
        "jfx-carousel--ssr-all-states",
        ssrShowAllStatesProperty.map(showAll => !browserRendering && showAll)
      )
      classIf("jfx-carousel--empty", itemStateRevisionProperty.map(_ => slideCount == 0))
      classIf("jfx-carousel--single", itemStateRevisionProperty.map(_ => slideCount <= 1))
      setAttribute("role", "region")
      setAttribute("aria-roledescription", "carousel")
      setAttribute("tabindex", "0")
      on("keydown")(handleKeyDown)

      div {
        classes = Seq("jfx-carousel__viewport")

        div {
          classes = Seq("jfx-carousel__track")
          style { transform = trackTransformProperty }

          when(ssrShowAllStatesProperty) {
            foreachIndexed(renderedItemsProperty) { (item, index) =>
                carouselSlide(
                  this,
                  item,
                  index,
                  slideCount,
                  slideRenderer,
                  observeActiveIndex = true
                ) {}
            }
          }

          when(ssrShowAllStatesProperty.map(!_)) {
            dynamic(activeSlideProperty)
          }
        }
      }

      when(multipleItemsProperty) {
        div {
          classes = Seq("jfx-carousel__controls")

          button("Previous") {
            val previousButton = summon[AbstractComponent]
            classes = Seq("jfx-carousel__nav")
            buttonType("button")
            previousButton.setAttribute("aria-label", "Previous slide")
            bindDisabled(previousButton, atStartProperty)
            onClick(_ => previous())
          }

          div {
            classes = Seq("jfx-carousel__status")
            setAttribute("aria-live", "polite")
            text(statusTextProperty) {}
          }

          div {
            classes = Seq("jfx-carousel__indicators")
            setAttribute("aria-label", "Choose slide")

            foreachIndexed(indicatorItemsProperty) { (_, index) =>
              val active = activeIndexProperty.map(_ == index)

              button((index + 1).toString) {
                val indicator = summon[AbstractComponent]
                classes = Seq("jfx-carousel__indicator")
                classIf("is-active", active)
                buttonType("button")
                indicator.setAttribute("aria-label", s"Go to slide ${index + 1}")
                indicator.addDisposable(active.observe { selected =>
                  if (selected) indicator.setAttribute("aria-current", "true")
                  else indicator.removeAttribute("aria-current")
                })
                onClick(_ => goTo(index))
              }
            }
          }

          button("Next") {
            val nextButton = summon[AbstractComponent]
            classes = Seq("jfx-carousel__nav")
            buttonType("button")
            nextButton.setAttribute("aria-label", "Next slide")
            bindDisabled(nextButton, atEndProperty)
            onClick(_ => next())
          }
        }
      }
    }

    installObservers()
    compositionReady = true
  }

  override def afterCompose(cursor: Cursor): Unit =
    restartAutoAdvance()

  private def installObservers(): Unit = {
    addDisposable(itemsRefProperty.observeWithoutInitial { _ =>
      rewireItemsObserver()
      handleItemsChanged()
    })
    addDisposable(activeIndexProperty.observeWithoutInitial(handleActiveIndexChanged))
    addDisposable(autoAdvanceMsProperty.observeWithoutInitial(_ => restartAutoAdvance()))
    addDisposable(wrapAroundProperty.observeWithoutInitial { _ =>
      normalizeActiveIndex()
      bumpNavigationRevision()
      restartAutoAdvance()
    })
    addDisposable(Disposable {
      itemsObserver.dispose()
      autoAdvance.dispose()
    })
  }

  private def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    itemsObserver = getItems.observeChanges(_ => handleItemsChanged())
  }

  private def handleItemsChanged(): Unit = {
    // Active listeners still belong to the currently mounted slides and
    // controls here. Normalize them before a following revision can unmount
    // those components during the same synchronous notification chain.
    normalizeActiveIndex()
    bumpItemStateRevision()
    bumpContentRevision()
    rebuildActiveSlide()
    bumpNavigationRevision()
    restartAutoAdvance()
  }

  private def handleActiveIndexChanged(index: Int): Unit = {
    val normalized = normalizeIndex(index)
    if (index == normalized) {
      rebuildActiveSlide()
      bumpNavigationRevision()
    }
    else activeIndexProperty.set(normalized)
  }

  private def normalizeActiveIndex(): Unit = {
    val current    = activeIndexProperty.get
    val normalized = normalizeIndex(current)
    if (current != normalized) activeIndexProperty.set(normalized)
  }

  private def rebuildActiveSlide(): Unit =
    activeSlideProperty.setAlways(
      currentItem match {
        case Some(item) =>
          new CarouselSlide(
            this,
            item,
            normalizedActiveIndex,
            slideCount,
            slideRenderer,
            observeActiveIndex = false
          )
        case None => new EmptyCarouselContent
      }
    )

  private def restartAutoAdvance(): Unit = {
    autoAdvance.dispose()
    autoAdvance = Disposable.empty

    val intervalMs = math.max(0, autoAdvanceMsProperty.get)
    if (browserRendering && compositionReady && slideCount > 1 && intervalMs > 0) {
      autoAdvance = intervalScheduler.schedule(intervalMs)(() => next())
    }
  }

  private def bindDisabled(
      component: AbstractComponent,
      disabled: ReadOnlyProperty[Boolean]
  ): Unit =
    component.addDisposable(disabled.observe { value =>
      if (value) {
        component.setAttribute("disabled", "")
        component.setAttribute("aria-disabled", "true")
      } else {
        component.removeAttribute("disabled")
        component.setAttribute("aria-disabled", "false")
      }
    })

  private def atStartProperty: ReadOnlyProperty[Boolean] =
    navigationRevisionProperty.map(_ =>
      !wrapAroundProperty.get && normalizedActiveIndex == 0
    )

  private def atEndProperty: ReadOnlyProperty[Boolean] =
    navigationRevisionProperty.map(_ =>
      !wrapAroundProperty.get && normalizedActiveIndex >= slideCount - 1
    )

  private def normalizeIndex(index: Int): Int =
    if (slideCount <= 0) 0
    else if (wrapAroundProperty.get) math.floorMod(index, slideCount)
    else math.max(0, math.min(slideCount - 1, index))

  private def normalizedActiveIndex: Int = normalizeIndex(activeIndexProperty.get)
  private[carousel] def slideCount: Int   = getItems.length

  private def statusText: String =
    if (slideCount <= 0) "0 / 0"
    else s"${normalizedActiveIndex + 1} / $slideCount"

  private def bumpItemStateRevision(): Unit =
    itemStateRevisionProperty.setAlways(itemStateRevisionProperty.get + 1)

  private def bumpContentRevision(): Unit =
    contentRevisionProperty.setAlways(contentRevisionProperty.get + 1)

  private def bumpNavigationRevision(): Unit =
    navigationRevisionProperty.setAlways(navigationRevisionProperty.get + 1)

  private def handleKeyDown(event: UiEvent): Unit =
    keyboardKey(event).foreach {
      case "ArrowRight" =>
        event.preventDefault()
        next()
      case "ArrowLeft" =>
        event.preventDefault()
        previous()
      case "Home" =>
        event.preventDefault()
        goTo(0)
      case "End" =>
        event.preventDefault()
        goTo(slideCount - 1)
      case _ => ()
    }

  private def keyboardKey(event: UiEvent): Option[String] =
    event.raw match {
      case raw: js.Object =>
        val key = raw.asInstanceOf[js.Dynamic].selectDynamic("key")
        Option.when(js.typeOf(key) == "string")(key.asInstanceOf[String])
      case _ => None
    }
}

object Carousel {
  type Renderer[T] = (T, Int) => AbstractComponent ?=> Cursor ?=> Unit

  private def emptyRenderer[T]: Renderer[T] =
    (_: T, _: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=> ()

  def carousel[T](
      body: Carousel[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Carousel[T] =
    DslLayer.child(new Carousel[T](body)) {}

  def items[T](using carousel: Carousel[T]): ListProperty[T] = carousel.getItems

  def items_=[T](value: ListProperty[T])(using carousel: Carousel[T]): Unit =
    carousel.setItems(value)

  def items_=[T](value: IterableOnce[T])(using carousel: Carousel[T]): Unit =
    value match {
      case property: ListProperty[?] =>
        carousel.setItems(property.asInstanceOf[ListProperty[T]])
      case _ => carousel.getItems.setAll(value)
    }

  def activeIndex(using carousel: Carousel[?]): Int = carousel.activeIndexProperty.get

  def activeIndexProperty(using carousel: Carousel[?]): Property[Int] =
    carousel.activeIndexProperty

  def activeIndex_=(value: Int)(using carousel: Carousel[?]): Unit = carousel.goTo(value)

  def activeIndex_=(value: ReadOnlyProperty[Int])(using carousel: Carousel[?]): Unit =
    carousel.addDisposable(value.observe(carousel.goTo))

  def autoAdvanceMs(using carousel: Carousel[?]): Int = carousel.autoAdvanceMsProperty.get

  def autoAdvanceMs_=(value: Int)(using carousel: Carousel[?]): Unit =
    carousel.autoAdvanceMsProperty.set(math.max(0, value))

  def autoAdvanceMs_=(value: ReadOnlyProperty[Int])(using carousel: Carousel[?]): Unit =
    carousel.addDisposable(value.observe(next => carousel.autoAdvanceMsProperty.set(math.max(0, next))))

  def wrapAround(using carousel: Carousel[?]): Boolean = carousel.wrapAroundProperty.get

  def wrapAround_=(value: Boolean)(using carousel: Carousel[?]): Unit =
    carousel.wrapAroundProperty.set(value)

  def wrapAround_=(value: ReadOnlyProperty[Boolean])(using carousel: Carousel[?]): Unit =
    carousel.addDisposable(value.observe(carousel.wrapAroundProperty.set))

  def ssrShowAllStates(using carousel: Carousel[?]): Boolean =
    carousel.ssrShowAllStatesProperty.get

  def ssrShowAllStates_=(value: Boolean)(using carousel: Carousel[?]): Unit =
    carousel.ssrShowAllStatesProperty.set(value)

  def ssrShowAllStates_=(value: ReadOnlyProperty[Boolean])(using carousel: Carousel[?]): Unit =
    carousel.addDisposable(value.observe(carousel.ssrShowAllStatesProperty.set))

  def slideRenderer[T](using carousel: Carousel[T]): Renderer[T] = carousel.getRenderer

  def slideRenderer_=[T](value: Renderer[T])(using carousel: Carousel[T]): Unit =
    carousel.setRenderer(value)

  def next(using carousel: Carousel[?]): Unit = carousel.next()
  def previous(using carousel: Carousel[?]): Unit = carousel.previous()
  def goTo(index: Int)(using carousel: Carousel[?]): Unit = carousel.goTo(index)
}
