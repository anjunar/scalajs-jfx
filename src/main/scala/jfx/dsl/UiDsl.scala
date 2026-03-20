package jfx.dsl

import jfx.action.Button
import jfx.core.component.{ChildrenComponent, CompositeComponent, ElementComponent, NodeComponent}
import jfx.core.state.ReadOnlyProperty
import jfx.form.{Form, Formular, Input, Model, SubForm}
import jfx.layout.Div
import org.scalajs.dom.{CSSStyleDeclaration, Event, Node}

import scala.Conversion
import scala.annotation.targetName
import scala.collection.mutable
import scala.compiletime.summonFrom

private[dsl] final case class ComponentContext(
  parent: Option[ChildrenComponent[? <: Node]],
  enclosingForm: Option[Formular[?, ?]]
)

private[dsl] object ComponentContext {
  val root: ComponentContext = ComponentContext(None, None)
}

private[jfx] final case class StyleTarget(
  component: ElementComponent[?],
  declaration: CSSStyleDeclaration
)

final class StyleProperty private[jfx] (
  private val currentValue: () => String,
  private val assignValue: String => Unit,
  private val bindValue: ReadOnlyProperty[String] => Unit
) {

  def value: String =
    currentValue()

  def apply(): String =
    currentValue()

  def :=(value: String): Unit =
    assignValue(value)

  @targetName("bindFromProperty")
  def <--(property: ReadOnlyProperty[String]): Unit =
    bindValue(property)

  override def toString: String =
    currentValue()
}

given Conversion[StyleProperty, String] with
  def apply(value: StyleProperty): String =
    value.value

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
  given StyleTarget = StyleTarget(component, component.css)
  init
}

def css(using component: ElementComponent[?]): CSSStyleDeclaration =
  component.css

def setProperty(name: String, value: String)(using target: StyleTarget): Unit = {
  target.component.clearStylePropertyBinding(name)
  target.declaration.setProperty(name, value)
}

def removeProperty(name: String)(using target: StyleTarget): String = {
  target.component.clearStylePropertyBinding(name)
  target.declaration.removeProperty(name)
}

def getPropertyValue(name: String)(using target: StyleTarget): String =
  target.declaration.getPropertyValue(name)

private def styleProperty(
  bindingKey: String,
  currentValue: => String,
  applyValue: String => Unit
)(using target: StyleTarget): StyleProperty =
  new StyleProperty(
    currentValue = () => currentValue,
    assignValue = value => {
      target.component.clearStylePropertyBinding(bindingKey)
      applyValue(value)
    },
    bindValue = property =>
      target.component.bindStyleProperty(bindingKey, property)(applyValue)
  )

def width(using target: StyleTarget): StyleProperty =
  styleProperty("width", target.declaration.width, target.declaration.width = _)

def width_=(value: String)(using target: StyleTarget): Unit =
  width := value

def maxWidth(using target: StyleTarget): StyleProperty =
  styleProperty("max-width", target.declaration.maxWidth, target.declaration.maxWidth = _)

def maxWidth_=(value: String)(using target: StyleTarget): Unit =
  maxWidth := value

def margin(using target: StyleTarget): StyleProperty =
  styleProperty("margin", target.declaration.margin, target.declaration.margin = _)

def margin_=(value: String)(using target: StyleTarget): Unit =
  margin := value

def padding(using target: StyleTarget): StyleProperty =
  styleProperty("padding", target.declaration.padding, target.declaration.padding = _)

def padding_=(value: String)(using target: StyleTarget): Unit =
  padding := value

def display(using target: StyleTarget): StyleProperty =
  styleProperty("display", target.declaration.display, target.declaration.display = _)

def display_=(value: String)(using target: StyleTarget): Unit =
  display := value

def fontFamily(using target: StyleTarget): StyleProperty =
  styleProperty("font-family", target.declaration.fontFamily, target.declaration.fontFamily = _)

def fontFamily_=(value: String)(using target: StyleTarget): Unit =
  fontFamily := value

def color(using target: StyleTarget): StyleProperty =
  styleProperty("color", target.declaration.color, target.declaration.color = _)

def color_=(value: String)(using target: StyleTarget): Unit =
  color := value

def fontSize(using target: StyleTarget): StyleProperty =
  styleProperty("font-size", target.declaration.fontSize, target.declaration.fontSize = _)

def fontSize_=(value: String)(using target: StyleTarget): Unit =
  fontSize := value

def fontWeight(using target: StyleTarget): StyleProperty =
  styleProperty("font-weight", target.declaration.fontWeight, target.declaration.fontWeight = _)

def fontWeight_=(value: String)(using target: StyleTarget): Unit =
  fontWeight := value

def lineHeight(using target: StyleTarget): StyleProperty =
  styleProperty("line-height", target.declaration.lineHeight, target.declaration.lineHeight = _)

def lineHeight_=(value: String)(using target: StyleTarget): Unit =
  lineHeight := value

def border(using target: StyleTarget): StyleProperty =
  styleProperty("border", target.declaration.border, target.declaration.border = _)

def border_=(value: String)(using target: StyleTarget): Unit =
  border := value

def borderRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-radius", target.declaration.borderRadius, target.declaration.borderRadius = _)

def borderRadius_=(value: String)(using target: StyleTarget): Unit =
  borderRadius := value

def backgroundColor(using target: StyleTarget): StyleProperty =
  styleProperty("background-color", target.declaration.backgroundColor, target.declaration.backgroundColor = _)

def backgroundColor_=(value: String)(using target: StyleTarget): Unit =
  backgroundColor := value

def boxShadow(using target: StyleTarget): StyleProperty =
  styleProperty("box-shadow", target.declaration.boxShadow, target.declaration.boxShadow = _)

def boxShadow_=(value: String)(using target: StyleTarget): Unit =
  boxShadow := value

def cursor(using target: StyleTarget): StyleProperty =
  styleProperty("cursor", target.declaration.cursor, target.declaration.cursor = _)

def cursor_=(value: String)(using target: StyleTarget): Unit =
  cursor := value

def opacity(using target: StyleTarget): StyleProperty =
  styleProperty("opacity", target.declaration.opacity, target.declaration.opacity = _)

def opacity_=(value: String)(using target: StyleTarget): Unit =
  opacity := value

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
