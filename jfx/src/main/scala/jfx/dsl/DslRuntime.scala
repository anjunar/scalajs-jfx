package jfx.dsl

import jfx.core.component.{CompositeComponent, ElementComponent, NodeComponent}
import jfx.form.Formular
import org.scalajs.dom.Node

import scala.collection.mutable
import scala.compiletime.summonFrom

private[jfx] final case class ComponentContext(
  parent: Option[NodeComponent[? <: Node]],
  enclosingForm: Option[Formular[?, ?]],
  attachOverride: Option[NodeComponent[? <: Node] => Unit] = None
)

private[jfx] object ComponentContext {
  val root: ComponentContext = ComponentContext(None, None)
}

object DslRuntime {

  private val componentContextStack: mutable.ArrayBuffer[ComponentContext] =
    mutable.ArrayBuffer(ComponentContext.root)
  private val scopeStack: mutable.ArrayBuffer[Scope] =
    mutable.ArrayBuffer.empty

  inline def currentScope[A](block: Scope => A): A =
    summonFrom {
      case given Scope =>
        block(summon[Scope])
      case _ =>
        if (scopeStack.nonEmpty) block(scopeStack.last)
        else {
          println("WARNING: No current scope found. Using root scope.")
          block(Scope.root())
        }
    }

  def activeScopeOption(): Option[Scope] =
    scopeStack.lastOption

  def currentComponentContext(): ComponentContext =
    componentContextStack.last

  def withScope[A](scope: Scope)(block: => A): A = {
    scopeStack += scope
    try block
    finally scopeStack.remove(scopeStack.length - 1)
  }

  def attach(component: NodeComponent[? <: Node], context: ComponentContext): Unit =
    context.attachOverride match {
      case Some(attachOverride) =>
        attachOverride(component)
      case None =>
        context.parent.foreach(_.attachChild(component))
    }

  def withComponentContext[A](context: ComponentContext)(block: => A): A = {
    componentContextStack += context
    try block
    finally componentContextStack.remove(componentContextStack.length - 1)
  }

  def branchContext(
    currentContext: ComponentContext,
    branchName: String,
    attachChild: ElementComponent[? <: Node] => Unit
  ): ComponentContext =
    ComponentContext(
      parent = None,
      enclosingForm = currentContext.enclosingForm,
      attachOverride = Some {
        case child: ElementComponent[?] =>
          attachChild(child.asInstanceOf[ElementComponent[? <: Node]])
        case child =>
          throw IllegalStateException(
            s"$branchName only accepts element components, but got ${child.getClass.getSimpleName}"
          )
      }
    )

  def withCompositeContext[A](
    parent: NodeComponent[? <: Node],
    context: CompositeComponent.DslContext
  )(block: => A): A =
    withScope(context.scope) {
      withComponentContext(ComponentContext(Some(parent), context.enclosingForm))(block)
    }
}
