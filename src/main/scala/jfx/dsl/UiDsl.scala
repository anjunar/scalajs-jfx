package jfx.dsl

import jfx.action.Button
import jfx.core.component.{ChildrenComponent, CompositeComponent, ElementComponent, NodeComponent}
import jfx.core.state.{Disposable, ListProperty, ReadOnlyProperty}
import jfx.control.{TableCell, TableColumn, TableRow, TableView, TableViewSelectionModel}
import jfx.form.{Form, Formular, Input, Model, SubForm}
import jfx.layout.{Div, Drawer, HBox, HorizontalLine, Span, VBox}
import jfx.router.{Route, RouteContext, Router}
import jfx.statement.{Conditional, DynamicOutlet, ForEach}
import org.scalajs.dom.{Event, Node}

import scala.collection.mutable
import scala.compiletime.summonFrom
import scala.scalajs.js

private[dsl] final case class ComponentContext(
  parent: Option[ChildrenComponent[? <: Node]],
  enclosingForm: Option[Formular[?, ?]],
  attachOverride: Option[NodeComponent[? <: Node] => Unit] = None
)

private[dsl] object ComponentContext {
  val root: ComponentContext = ComponentContext(None, None)
}

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

inline def hbox(init: HBox ?=> Unit): HBox =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new HBox()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given HBox = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def hr()(init: HorizontalLine ?=> Unit): HorizontalLine =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new HorizontalLine()
    withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
      given Scope = currentScope
      given HorizontalLine = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def hr(): HorizontalLine =
    hr()({})

inline def span(init: Span ?=> Unit): Span =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Span()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given Span = component
      init
    }
    attach(component, currentContext)
    component
  }


inline def vbox(init: VBox ?=> Unit): VBox =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new VBox()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given VBox = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def drawer(init: Drawer ?=> Unit = {}): Drawer =
  composite(new Drawer(init))

def drawerNavigation(init: => Unit)(using drawer: Drawer): Unit =
  drawer.navigation(init)

def drawerContent(init: => Unit)(using drawer: Drawer): Unit =
  drawer.content(init)


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

inline def tableView[S](init: TableView[S] ?=> Unit): TableView[S] =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new TableView[S]()
    withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
      given Scope = currentScope
      given TableView[S] = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def tableColumn[S, T](text: String): TableColumn[S, T] =
  tableColumn(text)({})

inline def tableColumn[S, T](text: String)(init: TableColumn[S, T] ?=> Unit): TableColumn[S, T] =
  currentScope { currentScope =>
    val component = new TableColumn[S, T](text)
    given Scope = currentScope
    given TableColumn[S, T] = component
    init
    summonFrom {
      case given TableView[S] =>
        summon[TableView[S]].columnsProperty += component
      case _ =>
        ()
    }
    component
  }

inline def column[S, T](text: String): TableColumn[S, T] =
  tableColumn(text)

inline def column[S, T](text: String)(init: TableColumn[S, T] ?=> Unit): TableColumn[S, T] =
  tableColumn(text)(init)

inline def tableRow[S](init: TableRow[S] ?=> Unit): TableRow[S] =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new TableRow[S]()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given TableRow[S] = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def row[S](init: TableRow[S] ?=> Unit): TableRow[S] =
  tableRow(init)

inline def tableCell[S, T](init: TableCell[S, T] ?=> Unit): TableCell[S, T] =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new TableCell[S, T]()
    withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
      given Scope = currentScope
      given TableCell[S, T] = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def cell[S, T](init: TableCell[S, T] ?=> Unit): TableCell[S, T] =
  tableCell(init)

inline def composite[C <: CompositeComponent[? <: Node]](component: C): C =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    given CompositeComponent.DslContext =
      CompositeComponent.DslContext(currentScope, currentContext.enclosingForm)
    component.renderComposite
    attach(component, currentContext)
    component
  }

def mount[C <: NodeComponent[? <: Node]](component: C): C =
  currentScope { _ =>
    val currentContext = currentComponentContext()
    attach(component, currentContext)
    component
  }

inline def router(routes: js.Array[Route]): Router =
  router(routes)({})

inline def router(routes: js.Array[Route])(init: Router ?=> Unit): Router =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    given Scope = currentScope
    val component = Router(routes)
    withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
      given Scope = currentScope
      given Router = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def when(condition: ReadOnlyProperty[Boolean])(init: Conditional ?=> Unit): Conditional =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    val component = new Conditional(condition)
    withComponentContext(branchContext(currentContext, "when", component.thenAdd)) {
      given Scope = currentScope
      given Conditional = component
      init
    }
    attach(component, currentContext)
    component
  }

inline def conditional(condition: ReadOnlyProperty[Boolean])(init: Conditional ?=> Unit): Conditional =
  when(condition)(init)

def otherwise(init: Conditional ?=> Unit)(using conditional: Conditional): Conditional =
  appendConditionalBranch(conditional, "otherwise", conditional.elseAdd)(init)

extension (conditional: Conditional)
  inline def otherwise(init: Conditional ?=> Unit): Conditional =
    appendConditionalBranch(conditional, "otherwise", conditional.elseAdd)(init)

inline def forEach[T](items: ListProperty[T])(renderItem: (T, Int) => NodeComponent[? <: Node]): ForEach[T] =
  currentScope { _ =>
    val currentContext = currentComponentContext()
    val component = new ForEach(items, renderItem)
    attach(component, currentContext)
    component
  }

inline def forEach[T](items: ListProperty[T])(renderItem: T => NodeComponent[? <: Node]): ForEach[T] =
  forEach(items) { (item, _) => renderItem(item) }

inline def outlet(content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]): DynamicOutlet =
  currentScope { _ =>
    val currentContext = currentComponentContext()
    val component = new DynamicOutlet(content)
    attach(component, currentContext)
    component
  }

inline def dynamicOutlet(content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]): DynamicOutlet =
  outlet(content)

def text(using component: ElementComponent[?]): String =
  component.textContent

def text_=(value: String)(using component: ElementComponent[?]): Unit =
  component.textContent = value

def classes(using component: ElementComponent[?]): ListProperty[String] =
  component.classProperty

def classes_=(value: String)(using component: ElementComponent[?]): Unit =
  classes_=(Seq(value))

def classes_=(value: IterableOnce[String])(using component: ElementComponent[?]): Unit =
  component.classProperty.setAll(ElementComponent.normalizeClassNames(value))

def addClass(value: String)(using component: ElementComponent[?]): Unit =
  addClasses(Seq(value))

def addClasses(values: IterableOnce[String])(using component: ElementComponent[?]): Unit = {
  val additions = ElementComponent.normalizeClassNames(values)
  if (additions.nonEmpty) {
    updateClasses(component) { current =>
      current ++ additions.filterNot(current.contains)
    }
  }
}

def removeClass(value: String)(using component: ElementComponent[?]): Unit =
  removeClasses(Seq(value))

def removeClasses(values: IterableOnce[String])(using component: ElementComponent[?]): Unit = {
  val removed = ElementComponent.normalizeClassNames(values).toSet
  if (removed.nonEmpty) {
    updateClasses(component) { current =>
      current.filterNot(removed.contains)
    }
  }
}

def header(using component: TableColumn[?, ?]): String =
  component.getText

def header_=(value: String)(using component: TableColumn[?, ?]): Unit =
  component.setText(value)

def placeholder(using component: Input): String =
  component.placeholder

def placeholder_=(value: String)(using component: Input): Unit =
  component.placeholder = value

def placeholderNode(using tableView: TableView[?]): NodeComponent[? <: Node] | Null =
  tableView.getPlaceholder

def placeholder_=(value: NodeComponent[? <: Node] | Null)(using tableView: TableView[?]): Unit =
  tableView.setPlaceholder(value)

def value(using input: Input): String | Boolean | Double =
  input.valueProperty.get

def value_=(nextValue: String | Boolean | Double)(using input: Input): Unit =
  input.valueProperty.set(nextValue)

def inputType(using input: Input): String =
  input.element.`type`

def inputType_=(value: String)(using input: Input): Unit =
  input.element.`type` = value

def buttonType(using button: Button): String =
  button.buttonType

def buttonType_=(value: String)(using button: Button): Unit =
  button.buttonType = value

def drawerOpen(using drawer: Drawer): Boolean =
  drawer.isOpen

def drawerOpen_=(value: Boolean)(using drawer: Drawer): Unit =
  drawer.isOpen = value

def drawerWidth(using drawer: Drawer): String =
  drawer.width

def drawerWidth_=(value: String)(using drawer: Drawer): Unit =
  drawer.width = value

def closeOnScrimClick(using drawer: Drawer): Boolean =
  drawer.closeOnScrimClick

def closeOnScrimClick_=(value: Boolean)(using drawer: Drawer): Unit =
  drawer.closeOnScrimClick = value

def openDrawer(using drawer: Drawer): Unit =
  drawer.open()

def closeDrawer(using drawer: Drawer): Unit =
  drawer.close()

def toggleDrawer(using drawer: Drawer): Unit =
  drawer.toggle()

def onClick(listener: Event => Unit)(using button: Button): Disposable =
  button.addClick(listener)

def items[S](using tableView: TableView[S]): ListProperty[S] =
  tableView.items

def items_=[S](value: ListProperty[S])(using tableView: TableView[S]): Unit =
  tableView.items = value

def fixedCellSize(using tableView: TableView[?]): Double =
  tableView.getFixedCellSize

def fixedCellSize_=(value: Double)(using tableView: TableView[?]): Unit =
  tableView.setFixedCellSize(value)

def rowFactory[S](using tableView: TableView[S]): TableView[S] => TableRow[S] =
  tableView.getRowFactory

def rowFactory_=[S](factory: TableView[S] => TableRow[S])(using tableView: TableView[S]): Unit =
  tableView.setRowFactory(factory)

def selectionModel[S](using tableView: TableView[S]): TableViewSelectionModel[S] =
  tableView.getSelectionModel

def refresh(using tableView: TableView[?]): Unit =
  tableView.refresh()

def scrollTo(index: Int)(using tableView: TableView[?]): Unit =
  tableView.scrollTo(index)

def prefWidth(using tableColumn: TableColumn[?, ?]): Double =
  tableColumn.getPrefWidth

def prefWidth_=(value: Double)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setPrefWidth(value)

def columnMaxWidth(using tableColumn: TableColumn[?, ?]): Double =
  tableColumn.getMaxWidth

def sortable(using tableColumn: TableColumn[?, ?]): Boolean =
  tableColumn.isSortable

def sortable_=(value: Boolean)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setSortable(value)

def sortKey(using tableColumn: TableColumn[?, ?]): String | Null =
  tableColumn.getSortKey

def sortKey_=(value: String | Null)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setSortKey(value)

def resizable(using tableColumn: TableColumn[?, ?]): Boolean =
  tableColumn.isResizable

def resizable_=(value: Boolean)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setResizable(value)

def cellValueFactory[S, T](using tableColumn: TableColumn[S, T]): TableColumn.CellDataFeatures[S, T] => ReadOnlyProperty[T] | Null =
  tableColumn.getCellValueFactory

def cellValueFactory_=[S, T](
  factory: TableColumn.CellDataFeatures[S, T] => ReadOnlyProperty[T] | Null
)(using tableColumn: TableColumn[S, T]): Unit =
  tableColumn.setCellValueFactory(factory)

def cellFactory[S, T](using tableColumn: TableColumn[S, T]): TableColumn[S, T] => TableCell[S, T] | Null =
  tableColumn.getCellFactory

def cellFactory_=[S, T](
  factory: TableColumn[S, T] => TableCell[S, T] | Null
)(using tableColumn: TableColumn[S, T]): Unit =
  tableColumn.setCellFactory(factory)

def rowItem[S](using tableRow: TableRow[S]): S | Null =
  tableRow.getItem

def cellItem[S, T](using tableCell: TableCell[S, T]): T | Null =
  tableCell.getItem

def rowIndex(using tableRow: TableRow[?]): Int =
  tableRow.getIndex

def cellIndex(using tableCell: TableCell[?, ?]): Int =
  tableCell.getIndex

def rowEmpty(using tableRow: TableRow[?]): Boolean =
  tableRow.isEmpty

def cellEmpty(using tableCell: TableCell[?, ?]): Boolean =
  tableCell.isEmpty

def rowSelected(using tableRow: TableRow[?]): Boolean =
  tableRow.isSelected

def cellSelected(using tableCell: TableCell[?, ?]): Boolean =
  tableCell.isSelected

def rowPlaceholder(using tableRow: TableRow[?]): Boolean =
  tableRow.isPlaceholder

def routeContext(using context: RouteContext): RouteContext =
  context

def routerContent(using router: Router): ReadOnlyProperty[NodeComponent[? <: Node] | Null] =
  router.contentProperty

def routerLoading(using router: Router): Boolean =
  router.loadingProperty.get

def routerError(using router: Router): Option[Throwable] =
  router.errorProperty.get

def routerState(using router: Router): jfx.router.RouterState =
  router.state

def navigate(path: String)(using router: Router): Unit =
  router.navigate(path)

def replace(path: String)(using router: Router): Unit =
  router.replace(path)

def reload(using router: Router): Unit =
  router.reload()

def conditionalCondition(using conditional: Conditional): ReadOnlyProperty[Boolean] =
  conditional.condition

def outletContent(using outlet: DynamicOutlet): ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null] =
  outlet.content

def onSubmit(using form: Form[?]): Event => Unit =
  form.onSubmit

def onSubmit_=(listener: Event => Unit)(using form: Form[?]): Unit =
  form.onSubmit = listener

private def updateClasses(
  component: ElementComponent[?]
)(update: Vector[String] => Vector[String]): Unit = {
  val currentRaw = component.classProperty.iterator.toVector
  val current = ElementComponent.normalizeClassNames(currentRaw)
  val next = ElementComponent.normalizeClassNames(update(current))

  if (currentRaw != next) {
    component.classProperty.setAll(next)
  }
}

private inline def currentScope[A](block: Scope => A): A =
  summonFrom {
    case given Scope =>
      block(summon[Scope])
    case _ =>
      block(Scope.root())
  }

private def attach(component: NodeComponent[? <: Node], context: ComponentContext): Unit =
  context.attachOverride match {
    case Some(attachOverride) =>
      attachOverride(component)
    case None =>
      context.parent.foreach(_.addChild(component))
  }

private def currentComponentContext(): ComponentContext =
  componentContextStack.last

private def withComponentContext[A](context: ComponentContext)(block: => A): A = {
  componentContextStack += context
  try block
  finally componentContextStack.remove(componentContextStack.length - 1)
}

private def branchContext(
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

private def appendConditionalBranch(
  conditional: Conditional,
  branchName: String,
  attachChild: ElementComponent[? <: Node] => Unit
)(init: Conditional ?=> Unit): Conditional =
  currentScope { currentScope =>
    val currentContext = currentComponentContext()
    withComponentContext(branchContext(currentContext, branchName, attachChild)) {
      given Scope = currentScope
      given Conditional = conditional
      init
    }
    conditional
  }

private[jfx] object DslRuntime {
  def withCompositeContext[A](
    parent: ChildrenComponent[? <: Node],
    context: CompositeComponent.DslContext
  )(block: => A): A =
    withComponentContext(ComponentContext(Some(parent), context.enclosingForm))(block)
}
