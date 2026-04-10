package jfx.dsl

import jfx.core.component.CompositeComponent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalajs.dom.Node

class ScopeSpec extends AnyFlatSpec with Matchers {

  final class RootService
  final class NestedService

  "Scope" should "resolve a scoped service inside the same nested scope" in {
    given Scope = Scope.root()

    Scope.singleton[RootService] {
      new RootService()
    }

    val resolved =
      Scope.scope {
        Scope.scoped[NestedService] {
          new NestedService()
        }

        Scope.inject[NestedService]
      }

    resolved shouldBe a[NestedService]
  }

  it should "resolve nested scoped services inside composite components" in {
    final class ComponentService

    final class TestComposite extends CompositeComponent[Node] {
      override val element: Node = null.asInstanceOf[Node]

      override protected def compose(using CompositeComponent.DslContext): Unit = ()

      def resolveFromNestedScope()(using CompositeComponent.DslContext): ComponentService =
        Scope.scope {
          Scope.scoped[ComponentService] {
            new ComponentService()
          }

          inject[ComponentService]
        }
    }

    given Scope = Scope.root()
    given CompositeComponent.DslContext =
      CompositeComponent.DslContext(summon[Scope], None)

    val component = new TestComposite()
    val resolved = component.resolveFromNestedScope()

    resolved should not be null
  }

  it should "resolve services from composite callbacks without an active runtime scope" in {
    final class CallbackComposite extends CompositeComponent[Node] {
      override val element: Node = null.asInstanceOf[Node]

      override protected def compose(using CompositeComponent.DslContext): Unit = ()

      def callback()(using CompositeComponent.DslContext): () => RootService =
        () => inject[RootService]
    }

    given Scope = Scope.root()

    Scope.singleton[RootService] {
      new RootService()
    }

    given CompositeComponent.DslContext =
      CompositeComponent.DslContext(summon[Scope], None)

    val component = new CallbackComposite()
    val resolver = component.callback()

    resolver() shouldBe a[RootService]
  }
}
