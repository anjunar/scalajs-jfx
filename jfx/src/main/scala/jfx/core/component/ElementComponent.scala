package jfx.core.component

import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import jfx.dsl.StyleTarget
import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, Node, document}

import scala.annotation.targetName
import scala.collection.mutable

trait ElementComponent[E <: Node] extends NodeComponent[E] {

  given self: ElementComponent[E] = this

  val textContentProperty = new Property[String]("")

  val classProperty = new ListProperty[String]()

  private val managedClasses = mutable.LinkedHashSet.empty[String]
  private val styleBindings = mutable.LinkedHashMap.empty[String, jfx.core.state.Disposable]

  def newElement(tag: String): E = document.createElement(tag).asInstanceOf[E]

  protected final def htmlElement: HTMLElement =
    element match {
      case html: HTMLElement => html
      case _ =>
        throw IllegalStateException(s"${getClass.getSimpleName} does not wrap an HTMLElement")
    }

  def css: CSSStyleDeclaration = htmlElement.style

  addDisposable(() => {
    styleBindings.values.foreach(_.dispose())
    styleBindings.clear()
  })

  private[jfx] final def bindStyleProperty(
    name: String,
    property: ReadOnlyProperty[String]
  )(applyValue: String => Unit): Unit = {
    clearStylePropertyBinding(name)
    val binding = property.observe(applyValue)
    styleBindings.update(name, binding)
  }

  private[jfx] final def clearStylePropertyBinding(name: String): Unit =
    styleBindings.remove(name).foreach(_.dispose())

  private val textContentObserver = textContentProperty.observeWithoutInitial { text => element.textContent = text }
  addDisposable(textContentObserver)

  private val classObserver = classProperty.observe { classNames =>
    syncManagedClasses(ElementComponent.normalizeClassNames(classNames.toSeq))
  }
  addDisposable(classObserver)

  private def syncManagedClasses(nextClasses: Seq[String]): Unit = {
    val nextSet = nextClasses.toSet

    managedClasses.filterNot(nextSet.contains).toList.foreach { className =>
      htmlElement.classList.remove(className)
      managedClasses -= className
    }

    nextClasses.foreach { className =>
      if (!htmlElement.classList.contains(className)) {
        htmlElement.classList.add(className)
      }
    }

    managedClasses.clear()
    managedClasses ++= nextClasses
  }

  def textContent: String = textContentProperty.get
  def textContent_=(value: String): Unit = textContentProperty.set(value)

  def addStyle(init: StyleTarget ?=> Unit): Unit = {
    given StyleTarget = StyleTarget(this, this.css)
    init
  }

}

object ElementComponent {

  private[jfx] def normalizeClassNames(classNames: IterableOnce[String]): Vector[String] = {
    val normalized = mutable.LinkedHashSet.empty[String]

    classNames.iterator.foreach { className =>
      if (className != null) {
        className
          .split("\\s+")
          .iterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .foreach(normalized += _)
      }
    }

    normalized.toVector
  }

  def textProperty(using component: ElementComponent[?]): Property[String] =
    component.textContentProperty

  def text(using component: ElementComponent[?]): String =
    component.textContent

  def text_=(value: String)(using component: ElementComponent[?]): Unit =
    component.textContent = value

  def classes(using component: ElementComponent[?]): ListProperty[String] =
    component.classProperty

  def classes_=(value: String)(using component: ElementComponent[?]): Unit =
    component.classProperty.setAll(normalizeClassNames(Seq(value)))

  def classes_=(value: IterableOnce[String])(using component: ElementComponent[?]): Unit =
    component.classProperty.setAll(normalizeClassNames(value))

  def addClass(value: String)(using component: ElementComponent[?]): Unit =
    addClasses(Seq(value))

  def addClasses(values: IterableOnce[String])(using component: ElementComponent[?]): Unit = {
    val additions = normalizeClassNames(values)
    if (additions.nonEmpty) {
      updateClasses(component) { current =>
        current ++ additions.filterNot(current.contains)
      }
    }
  }

  def removeClass(value: String)(using component: ElementComponent[?]): Unit =
    removeClasses(Seq(value))

  def removeClasses(values: IterableOnce[String])(using component: ElementComponent[?]): Unit = {
    val removed = normalizeClassNames(values).toSet
    if (removed.nonEmpty) {
      updateClasses(component) { current =>
        current.filterNot(removed.contains)
      }
    }
  }

  private def updateClasses(
    component: ElementComponent[?]
  )(update: Vector[String] => Vector[String]): Unit = {
    val currentRaw = component.classProperty.iterator.toVector
    val current = normalizeClassNames(currentRaw)
    val next = normalizeClassNames(update(current))

    if (currentRaw != next) {
      component.classProperty.setAll(next)
    }
  }

}
