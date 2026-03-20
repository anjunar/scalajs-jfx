package jfx.dsl

import jfx.action.Button
import jfx.core.component.{ChildrenComponent, CompositeComponent, ElementComponent, NodeComponent}
import jfx.form.{Form, Formular, Input, Model, SubForm}
import jfx.layout.Div
import org.scalajs.dom.{CSSStyleDeclaration, Event, Node}

import scala.collection.mutable
import scala.compiletime.summonFrom

private[dsl] final case class ComponentContext(
  parent: Option[ChildrenComponent[? <: Node]],
  enclosingForm: Option[Formular[?, ?]]
)

private[dsl] object ComponentContext {
  val root: ComponentContext = ComponentContext(None, None)
}

private[jfx] final case class StyleTarget(declaration: CSSStyleDeclaration)

private val componentContextStack: mutable.ArrayBuffer[ComponentContext] =
  mutable.ArrayBuffer(ComponentContext.root)

inline def scope[A](block: Scope ?=> A): A =
  summonFrom {
    case given Scope =>
      val childScope = summon[Scope].child()
      block(using childScope)
    case _ =>
      val rootScope = Scope.root()
      block(using rootScope)
  }

def singleton[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
  scope.singleton(provider)

def scoped[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
  scope.scoped(provider)

def transient[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
  scope.transient(provider)

def inject[T](using scope: Scope, key: Scope.ServiceKey[T]): T =
  scope.inject[T]

inline def div(init: Div ?=> Unit): Div =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Div()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given Div = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def form[M <: Model[M]](model: M)(init: Form[M] ?=> Unit): Form[M] =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Form(model)
    withComponentContext(ComponentContext(Some(component), Some(component))) {
      given Scope = currentScope
      given Form[M] = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def subForm[M <: Model[M]](name: String)(init: SubForm[M] ?=> Unit): SubForm[M] =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new SubForm[M](name)
    withComponentContext(ComponentContext(Some(component), Some(component))) {
      given Scope = currentScope
      given SubForm[M] = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def input(name: String): Input =
  input(name)({})

inline def input(name: String)(init: Input ?=> Unit): Input =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Input(name)
    withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
      given Scope = currentScope
      given Input = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def button(label: String): Button =
  button(label)({})

inline def button(label: String)(init: Button ?=> Unit): Button =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Button()
    component.textContent = label
    component.buttonType =
      if (currentContext.enclosingForm.nonEmpty) "submit"
      else "button"

    withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
      given Scope = currentScope
      given Button = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def composite[C <: CompositeComponent[? <: Node]](component: C): C =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    given CompositeComponent.DslContext =
      CompositeComponent.DslContext(currentScope, currentContext.enclosingForm)
    component.renderComposite
    attach(component, currentContext)
    component
  }

def text(using component: ElementComponent[?]): String =
  component.textContent

def text_=(value: String)(using component: ElementComponent[?]): Unit =
  component.textContent = value

def placeholder(using component: Input): String =
  component.placeholder

def placeholder_=(value: String)(using component: Input): Unit =
  component.placeholder = value

def style(init: StyleTarget ?=> Unit)(using component: ElementComponent[?]): Unit = {
  given StyleTarget = StyleTarget(component.css)
  init
}

def css(using component: ElementComponent[?]): CSSStyleDeclaration =
  component.css

def setProperty(name: String, value: String)(using target: StyleTarget): Unit =
  target.declaration.setProperty(name, value)

def removeProperty(name: String)(using target: StyleTarget): String =
  target.declaration.removeProperty(name)

def getPropertyValue(name: String)(using target: StyleTarget): String =
  target.declaration.getPropertyValue(name)

def maxWidth(using target: StyleTarget): String =
  target.declaration.maxWidth

def maxWidth_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.maxWidth = value

def margin(using target: StyleTarget): String =
  target.declaration.margin

def margin_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.margin = value

def padding(using target: StyleTarget): String =
  target.declaration.padding

def padding_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.padding = value

def display(using target: StyleTarget): String =
  target.declaration.display

def display_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.display = value

def fontFamily(using target: StyleTarget): String =
  target.declaration.fontFamily

def fontFamily_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.fontFamily = value

def color(using target: StyleTarget): String =
  target.declaration.color

def color_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.color = value

def fontSize(using target: StyleTarget): String =
  target.declaration.fontSize

def fontSize_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.fontSize = value

def fontWeight(using target: StyleTarget): String =
  target.declaration.fontWeight

def fontWeight_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.fontWeight = value

def lineHeight(using target: StyleTarget): String =
  target.declaration.lineHeight

def lineHeight_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.lineHeight = value

def border(using target: StyleTarget): String =
  target.declaration.border

def border_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.border = value

def borderRadius(using target: StyleTarget): String =
  target.declaration.borderRadius

def borderRadius_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.borderRadius = value

def backgroundColor(using target: StyleTarget): String =
  target.declaration.backgroundColor

def backgroundColor_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.backgroundColor = value

def boxShadow(using target: StyleTarget): String =
  target.declaration.boxShadow

def boxShadow_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.boxShadow = value

def cursor(using target: StyleTarget): String =
  target.declaration.cursor

def cursor_=(value: String)(using target: StyleTarget): Unit =
  target.declaration.cursor = value

def onSubmit(using form: Form[?]): Event => Unit =
  form.onSubmit

def onSubmit_=(listener: Event => Unit)(using form: Form[?]): Unit =
  form.onSubmit = listener

private inline def currentScope[A](block: Scope => A): A =
  summonFrom {
    case given Scope =>
      block(summon[Scope])
    case _ =>
      block(Scope.root())
  }

private def attach(component: NodeComponent[? <: Node], context: ComponentContext): Unit =
  context.parent.foreach(_.addChild(component))

private def currentComponentContext(): ComponentContext =
  componentContextStack.last

private def withComponentContext[A](context: ComponentContext)(block: => A): A = {
  componentContextStack += context
  try block
  finally componentContextStack.remove(componentContextStack.length - 1)
}

private[jfx] object DslRuntime {
  def withCompositeContext[A](
    parent: ChildrenComponent[? <: Node],
    context: CompositeComponent.DslContext
  )(block: => A): A =
    withComponentContext(ComponentContext(Some(parent), context.enclosingForm))(block)
}
