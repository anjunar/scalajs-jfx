package jfx.core.component

import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, Node, document}

import scala.collection.mutable

trait ElementComponent[E <: Node] extends NodeComponent[E] {

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

  private val textContentObserver = textContentProperty.observe { text => element.textContent = text }
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

}
