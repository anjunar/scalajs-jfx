package jfx.core.component

import jfx.dsl.Scope
import jfx.dsl.DslRuntime
import jfx.form.Formular
import org.scalajs.dom.{Node, console}

import scala.collection.mutable

trait CompositeComponent[N <: Node]
    extends ElementComponent[N], FormSubtreeRegistration {

  import CompositeComponent.DslContext

  private val compositeChildren = mutable.ArrayBuffer.empty[NodeComponent[? <: Node]]

  protected def compose(using DslContext): Unit

  private[jfx] final def renderComposite(using context: DslContext): Unit =
    compose

  protected given dslScope(using context: DslContext): Scope =
    context.scope

  protected final def withDslContext[A](block: => A)(using context: DslContext): A =
    DslRuntime.withCompositeContext(this, context) {
      given Scope = context.scope
      given DslContext = context
      block
    }

  protected final def dslContext(using context: DslContext): DslContext =
    context

  protected final def inject[T](using context: DslContext, key: Scope.ServiceKey[T]): T = {
    val resolvedScope = DslRuntime.activeScopeOption().getOrElse(context.scope)
    resolvedScope.inject[T]
  }

  protected final def addChild(child: NodeComponent[? <: Node]): Unit = {
    if (compositeChildren.exists(_ eq child)) {
      console.warn("Child already in list")
      return
    }

    compositeChildren += child
    element.appendChild(child.element)
    child.parent = Some(this)
    child.onMount()
    registerSubtree(child)
  }

  protected final def removeChild(child: NodeComponent[? <: Node]): Unit = {
    val index = compositeChildren.indexWhere(_ eq child)

    if (index >= 0) {
      compositeChildren.remove(index)

      val parent = child.element.parentNode
      if (parent == element) {
        element.removeChild(child.element)
      } else if (parent != null) {
        parent.removeChild(child.element)
      }

      if (child.isMounted) {
        unregisterSubtree(child)
        child.onUnmount()
      }

      child.parent = None
      child.dispose()
    }
  }

  protected final def clearChildren(): Unit =
    compositeChildren.toVector.foreach(removeChild)

  override def dispose(): Unit = {
    clearChildren()
    super.dispose()
  }

  override private[jfx] def attachChild(child: NodeComponent[? <: Node]): Unit =
    addChild(child)

  override private[jfx] def detachChild(child: NodeComponent[? <: Node]): Boolean = {
    val containsChild = compositeChildren.exists(_ eq child)
    if (containsChild) {
      removeChild(child)
    }
    containsChild
  }

  override private[jfx] def childComponentsIterator: Iterator[NodeComponent[? <: Node]] =
    compositeChildren.iterator
}

object CompositeComponent {

  final case class DslContext(
    scope: Scope,
    enclosingForm: Option[Formular[?, ?]]
  )

  inline def composite[C <: CompositeComponent[? <: Node]](component: C): C =
    DslRuntime.currentScope { currentScope =>
      compositeInternal(component, currentScope)
    }

  private[jfx] def compositeInternal[C <: CompositeComponent[? <: Node]](component: C, currentScope: Scope): C = {
    val currentContext = DslRuntime.currentComponentContext()
    given DslContext =
      DslContext(currentScope, currentContext.enclosingForm)
    component.renderComposite
    DslRuntime.attach(component, currentContext)
    component
  }

}
