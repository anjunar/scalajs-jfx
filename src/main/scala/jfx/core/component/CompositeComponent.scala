package jfx.core.component

import jfx.dsl.Scope
import jfx.dsl.DslRuntime
import jfx.dsl.StyleTarget
import jfx.form.Formular
import org.scalajs.dom.Node

trait CompositeComponent[N <: Node] extends NativeComponent[N] {

  import CompositeComponent.DslContext

  protected def compose(using DslContext): Unit

  private[jfx] final def renderComposite(using context: DslContext): Unit =
    compose

  // Runs nested DSL code with this composite as the current parent and the current DI scope available.
  protected final def withDslContext[A](block: => A)(using context: DslContext): A =
    DslRuntime.withCompositeContext(this, context) {
      given Scope = context.scope
      given DslContext = context
      block
    }

  protected final def style(init: StyleTarget ?=> Unit)(using DslContext): Unit =
    jfx.dsl.style(init)(using this)

  protected final def dslContext(using context: DslContext): DslContext =
    context

  protected final def injectFromDsl[T](using context: DslContext, key: Scope.ServiceKey[T]): T =
    context.scope.inject[T]
}

object CompositeComponent {

  final case class DslContext(
    scope: Scope,
    enclosingForm: Option[Formular[?, ?]]
  )

}
