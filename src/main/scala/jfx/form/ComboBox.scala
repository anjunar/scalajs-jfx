package jfx.form

import jfx.control.{TableCell, TableColumn, TableRow, TableView}
import jfx.core.component.{CompositeComponent, FormRegistrationBoundary, NativeComponent}
import jfx.core.component.ElementComponent.{classes_=, text_=}
import jfx.core.component.NodeComponent.mount
import jfx.core.state.{Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.layout.Span.span
import jfx.layout.Viewport
import org.scalajs.dom.{Event, HTMLDivElement, HTMLSpanElement, KeyboardEvent, Node, document}

import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

class ComboBox[S](
  val name: String,
  private val renderScope: Scope
) extends CompositeComponent[HTMLDivElement], Control[js.Array[S], HTMLDivElement] {

  override val valueProperty: ListProperty[S] = ListProperty()

  private val itemsRefProperty: Property[ListProperty[S]] = Property(ListProperty())
  private val openProperty: Property[Boolean] = Property(false)

  val itemRendererProperty: Property[ComboBox.ItemRenderer[S] | Null] =
    Property[ComboBox.ItemRenderer[S] | Null](null)

  val valueRendererProperty: Property[ComboBox.ValueRenderer[S] | Null] =
    Property[ComboBox.ValueRenderer[S] | Null](null)

  val selectedItemProperty: Property[S | Null] = Property(null)
  val selectedIndexProperty: Property[Int] = Property(-1)
  val dropdownHeightPxProperty: Property[Double] = Property(240.0)
  val rowHeightPxProperty: Property[Double] = Property(36.0)

  private var valueHost: ComboBox.RenderHost | Null = null
  private var indicatorElement: HTMLSpanElement | Null = null
  private var itemsObserver: Disposable = ComboBox.noopDisposable
  private var overlayConf: Viewport.OverlayConf | Null = null
  private var popupListenerCleanup: Disposable = ComboBox.noopDisposable
  private var popupListenerInstallHandle: Option[SetTimeoutHandle] = None
  private var dropdownTable: TableView[S] | Null = null
  private var updatingValueProperty = false
  private var disposed = false

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-combo-box")
    divElement.tabIndex = 0
    divElement.setAttribute("role", "combobox")
    divElement.setAttribute("aria-haspopup", "listbox")
    divElement.setAttribute("aria-expanded", "false")
    divElement.onclick = (event: Event) => {
      event.preventDefault()
      event.stopPropagation()
      toggleOverlay()
    }
    divElement.onfocus = _ => focusedProperty.set(true)
    divElement.onblur = _ => {
      if (!isOpen) {
        focusedProperty.set(false)
      }
    }
    divElement.onkeydown = event => handleKeyDown(event)
    divElement
  }

  private val valueObserver =
    valueProperty.observe(_ => reconcileValue())
  addDisposable(valueObserver)

  private val itemsRefObserver =
    itemsRefProperty.observe { _ =>
      rewireItemsObserver()
      syncSelectedIndex()
      Option(dropdownTable).foreach(_.setItems(getItems))
    }
  addDisposable(itemsRefObserver)

  private val itemRendererObserver =
    itemRendererProperty.observe { _ =>
      Option(dropdownTable).foreach(_.refresh())
    }
  addDisposable(itemRendererObserver)

  private val valueRendererObserver =
    valueRendererProperty.observe { _ =>
      rerenderValueHost()
    }
  addDisposable(valueRendererObserver)

  private val placeholderObserver =
    placeholderProperty.observe { _ =>
      rerenderValueHost()
    }
  addDisposable(placeholderObserver)

  private val openObserver =
    openProperty.observe(syncOpenState)
  addDisposable(openObserver)

  override protected def compose(using CompositeComponent.DslContext): Unit = {
    val host = new ComboBox.RenderHost(renderScope, "jfx-combo-box__value")
    valueHost = host
    addChild(host)

    val icon = document.createElement("span").asInstanceOf[HTMLSpanElement]
    icon.classList.add("material-icons")
    icon.classList.add("jfx-combo-box__indicator")
    icon.textContent = "expand_more"
    indicatorElement = icon
    element.appendChild(icon)

    rerenderValueHost()
    syncOpenState(openProperty.get)
  }

  def itemsProperty: Property[ListProperty[S]] = itemsRefProperty

  def getItems: ListProperty[S] = itemsRefProperty.get

  def setItems(items: ListProperty[S]): Unit = {
    val normalizedItems =
      if (items == null) ListProperty()
      else items
    if (!itemsRefProperty.get.eq(normalizedItems)) {
      itemsRefProperty.setAlways(normalizedItems)
    }
  }

  def getItemRenderer: ComboBox.ItemRenderer[S] | Null =
    itemRendererProperty.get

  def setItemRenderer(renderer: ComboBox.ItemRenderer[S] | Null): Unit =
    itemRendererProperty.set(renderer)

  def getValueRenderer: ComboBox.ValueRenderer[S] | Null =
    valueRendererProperty.get

  def setValueRenderer(renderer: ComboBox.ValueRenderer[S] | Null): Unit =
    valueRendererProperty.set(renderer)

  def getSelectedItem: S | Null =
    selectedItemProperty.get

  def setSelectedItem(item: S | Null): Unit =
    updateSelection(item, markDirty = false, closeAfter = false)

  def getSelectedIndex: Int =
    selectedIndexProperty.get

  def clearSelection(): Unit =
    updateSelection(null, markDirty = false, closeAfter = false)

  def open(): Unit =
    if (!isOpen) {
      val table = buildDropdownTable()
      val anchorWidth = math.max(element.getBoundingClientRect().width, 120.0)
      val overlay = new Viewport.OverlayConf(
        anchor = element,
        content = {
          mount(new ComboBox.DropdownPanel(table))
        },
        offsetYPx = 6.0,
        widthPx = Some(anchorWidth),
        minWidthPx = Some(anchorWidth),
        maxHeightPx = Some(normalizedDropdownHeight + 2.0)
      )

      dropdownTable = table
      overlayConf = overlay
      Viewport.addOverlay(overlay)
      openProperty.set(true)
      installPopupAutoClose()
      syncDropdownSelection()
    }

  def close(refocus: Boolean = false): Unit =
    if (isOpen) {
      popupListenerCleanup.dispose()
      popupListenerCleanup = ComboBox.noopDisposable
      popupListenerInstallHandle.foreach(clearTimeout)
      popupListenerInstallHandle = None

      Option(overlayConf).foreach(Viewport.closeOverlay)
      overlayConf = null
      dropdownTable = null
      openProperty.set(false)

      if (refocus && element.isConnected) {
        element.focus()
      }
    }

  def isOpen: Boolean =
    openProperty.get

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true
    close(refocus = false)
    itemsObserver.dispose()
    super.dispose()
  }

  private def handleKeyDown(event: KeyboardEvent): Unit =
    event.key match {
      case "Enter" | " " | "Spacebar" =>
        event.preventDefault()
        toggleOverlay()
      case "ArrowDown" | "ArrowUp" =>
        event.preventDefault()
        if (!isOpen) {
          open()
        }
      case "Escape" =>
        if (isOpen) {
          event.preventDefault()
          close(refocus = true)
        }
      case "Backspace" | "Delete" =>
        event.preventDefault()
        updateSelection(null, markDirty = true, closeAfter = false)
      case _ =>
        ()
    }

  private def toggleOverlay(): Unit =
    if (isOpen) close(refocus = true)
    else open()

  private def installPopupAutoClose(): Unit = {
    popupListenerCleanup.dispose()
    popupListenerCleanup = ComboBox.noopDisposable
    popupListenerInstallHandle.foreach(clearTimeout)

    popupListenerInstallHandle = Some(setTimeout(0) {
      popupListenerInstallHandle = None

      if (isOpen && !disposed) {
        val clickListener: js.Function1[Event, Unit] = event => {
          val targetNode =
            event.target match {
              case node: Node => node
              case _ => null
            }

          if (targetNode != null && !element.contains(targetNode)) {
            close(refocus = false)
          }
        }

        val keyListener: js.Function1[KeyboardEvent, Unit] = event => {
          if (event.key == "Escape") {
            event.preventDefault()
            close(refocus = true)
          }
        }

        document.addEventListener("click", clickListener)
        document.addEventListener("keydown", keyListener)

        popupListenerCleanup = () => {
          document.removeEventListener("click", clickListener)
          document.removeEventListener("keydown", keyListener)
        }
      }
    })
  }

  private def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    itemsObserver = getItems.observeChanges { _ =>
      syncSelectedIndex()
      Option(dropdownTable).foreach(_.refresh())
    }
  }

  private def reconcileValue(): Unit = {
    if (updatingValueProperty) return

    val normalizedSelectedItem =
      if (valueProperty.isEmpty) {
        null
      } else {
        val head = valueProperty(0).asInstanceOf[S | Null]
        val normalizedValues =
          if (head == null) {
            Vector.empty
          } else {
            Vector(head.asInstanceOf[S])
          }

        if (head == null || valueProperty.lengthCompare(1) != 0) {
          updatingValueProperty = true
          try valueProperty.setAll(normalizedValues)
          finally updatingValueProperty = false
        }

        head
      }

    selectedItemProperty.set(normalizedSelectedItem)
    syncSelectedIndex()
    rerenderValueHost()
  }

  private def syncSelectedIndex(): Unit = {
    val selectedItem = selectedItemProperty.get
    val index =
      if (selectedItem == null) -1
      else getItems.indexOf(selectedItem.asInstanceOf[S])

    selectedIndexProperty.set(index)
    syncDropdownSelection()
    syncEmptyState()
  }

  private def syncDropdownSelection(): Unit =
    Option(dropdownTable).foreach { table =>
      val selectedIndex = selectedIndexProperty.get
      if (selectedIndex < 0) {
        table.getSelectionModel.clearSelection()
      } else {
        table.getSelectionModel.select(selectedIndex)
        table.scrollTo(selectedIndex)
      }
    }

  private def updateSelection(item: S | Null, markDirty: Boolean, closeAfter: Boolean): Unit = {
    val normalizedValues =
      if (item == null) Vector.empty
      else Vector(item.asInstanceOf[S])

    updatingValueProperty = true
    try valueProperty.setAll(normalizedValues)
    finally updatingValueProperty = false

    selectedItemProperty.set(item)
    syncSelectedIndex()
    rerenderValueHost()

    if (markDirty) {
      dirtyProperty.set(true)
    }

    if (closeAfter) {
      close(refocus = true)
    }
  }

  private def rerenderValueHost(): Unit =
    Option(valueHost).foreach { host =>
      host.rerender {
        renderValueContent()
      }
    }

  private def renderValueContent()(using Scope): Unit = {
    given ComboBox.ValueRenderContext[S] =
      ComboBox.ValueRenderContext(
        comboBox = this,
        selectedItems = valueProperty,
        selectedItem = selectedItemProperty.get
      )

    val renderer = valueRendererProperty.get
    if (renderer == null) {
      ComboBox.defaultValueRenderer()
    } else {
      renderer.render
    }
  }

  private def renderItemContent(
    item: S,
    index: Int,
    selected: Boolean
  )(using Scope): Unit = {
    given ComboBox.ItemRenderContext[S] =
      ComboBox.ItemRenderContext(
        comboBox = this,
        item = item,
        index = index,
        selected = selected
      )

    val renderer = itemRendererProperty.get
    if (renderer == null) {
      ComboBox.defaultItemRenderer()
    } else {
      renderer.render
    }
  }

  private def buildDropdownTable(): TableView[S] = {
    val table = new TableView[S]()
    val column = new TableColumn[S, S]("")
    val dropdownWidth = math.max(element.getBoundingClientRect().width, 120.0)

    column.prefWidth = dropdownWidth
    column.setMinWidth(dropdownWidth)
    column.setMaxWidth(dropdownWidth)
    column.setResizable(false)
    column.setCellValueFactory(features => Property(features.getValue))
    column.setCellFactory(_ => new ComboBox.ItemCell(this, renderScope))

    table.element.classList.add("jfx-combo-box__table")
    table.element.setAttribute("role", "listbox")
    table.setItems(getItems)
    table.setFixedCellSize(normalizedRowHeight)
    table.setShowHeader(false)
    table.setRowFactory(_ => new ComboBox.DropdownRow(this))
    table.css.height = s"${normalizedDropdownHeight}px"
    table.columnsProperty += column
    table
  }

  private def syncOpenState(open: Boolean): Unit = {
    element.setAttribute("aria-expanded", open.toString)
    toggleClass("jfx-combo-box-open", open)
    Option(indicatorElement).foreach { indicator =>
      if (open) indicator.classList.add("is-open")
      else indicator.classList.remove("is-open")
    }

    if (open) {
      focusedProperty.set(true)
    } else if (document.activeElement != element) {
      focusedProperty.set(false)
    }
  }

  private def syncEmptyState(): Unit =
    toggleClass("jfx-combo-box-empty", valueProperty.isEmpty)

  private def toggleClass(className: String, enabled: Boolean): Unit =
    if (enabled) element.classList.add(className)
    else element.classList.remove(className)

  private def normalizedDropdownHeight: Double =
    math.max(normalizedRowHeight, dropdownHeightPxProperty.get)

  private def normalizedRowHeight: Double =
    math.max(28.0, rowHeightPxProperty.get)
}

object ComboBox {

  final case class ItemRenderContext[S](
    comboBox: ComboBox[S],
    item: S,
    index: Int,
    selected: Boolean
  )

  final case class ValueRenderContext[S](
    comboBox: ComboBox[S],
    selectedItems: ReadOnlyProperty[js.Array[S]],
    selectedItem: S | Null
  )

  final class ItemRenderer[S] private (private val run: ItemRenderContext[S] => Unit) {
    def render(using context: ItemRenderContext[S]): Unit =
      run(context)
  }

  object ItemRenderer {
    def apply[S](renderer: ItemRenderContext[S] ?=> Unit): ItemRenderer[S] =
      new ItemRenderer[S](context => renderer(using context))
  }

  final class ValueRenderer[S] private (private val run: ValueRenderContext[S] => Unit) {
    def render(using context: ValueRenderContext[S]): Unit =
      run(context)
  }

  object ValueRenderer {
    def apply[S](renderer: ValueRenderContext[S] ?=> Unit): ValueRenderer[S] =
      new ValueRenderer[S](context => renderer(using context))
  }

  private val noopDisposable: Disposable = () => ()

  private final class RenderHost(
    renderScope: Scope,
    className: String
  ) extends NativeComponent[HTMLDivElement], FormRegistrationBoundary {

    override lazy val element: HTMLDivElement = {
      val divElement = newElement("div")
      divElement.className = className
      divElement
    }

    def rerender(content: Scope ?=> Unit): Unit = {
      clearChildren()
      DslRuntime.withComponentContext(ComponentContext(Some(this), None)) {
        given Scope = renderScope
        content
      }
    }
  }

  private final class DropdownPanel[S](table: TableView[S])
      extends NativeComponent[HTMLDivElement], FormRegistrationBoundary {

    override lazy val element: HTMLDivElement = {
      val divElement = newElement("div")
      divElement.className = "jfx-combo-box__dropdown"
      divElement
    }

    addChild(table)
  }

  private final class ItemCell[S](
    comboBox: ComboBox[S],
    renderScope: Scope
  ) extends TableCell[S, S] {

    private val host = new RenderHost(renderScope, "jfx-combo-box__item")
    addChild(host)

    override protected def updateItem(item: S | Null, empty: Boolean): Unit = {
      val isEmptyCell = empty || item == null
      setCellClass("jfx-table-cell-empty", isEmptyCell)

      if (isEmptyCell) {
        host.rerender {
          ()
        }
      } else {
        host.rerender {
          comboBox.renderItemContent(item.asInstanceOf[S], getIndex, isSelected)
        }
      }
    }

    override protected def updateSelected(selected: Boolean): Unit = {
      super.updateSelected(selected)

      val item = getItem
      if (!isEmpty && item != null) {
        host.rerender {
          comboBox.renderItemContent(item.asInstanceOf[S], getIndex, selected)
        }
      }
    }

    private def setCellClass(className: String, enabled: Boolean): Unit =
      if (enabled) element.classList.add(className)
      else element.classList.remove(className)
  }

  private final class DropdownRow[S](comboBox: ComboBox[S]) extends TableRow[S] {

    private val previousClick = element.onclick

    element.setAttribute("role", "option")
    element.onclick = event => {
      if (previousClick != null) {
        previousClick(event)
      }

      val item = getItem
      if (!isEmpty && item != null) {
        comboBox.updateSelection(item.asInstanceOf[S], markDirty = true, closeAfter = true)
      }
    }
  }

  private def displayText(value: Any): String =
    if (value == null) ""
    else value.toString

  private[form] def defaultItemRenderer[S]()(using context: ItemRenderContext[S], scope: Scope): Unit =
    span {
      classes_=("jfx-combo-box__item-text")
      text_=(displayText(context.item))
    }

  private[form] def defaultValueRenderer[S]()(using context: ValueRenderContext[S], scope: Scope): Unit = {
    val selectedItem = context.selectedItem
    val isEmpty = selectedItem == null
    val label =
      if (isEmpty) {
        Option(context.comboBox.placeholderProperty.get).filter(_.trim.nonEmpty).getOrElse("")
      } else {
        displayText(selectedItem)
      }

    span {
      classes_=(Vector("jfx-combo-box__value-text") ++ Option.when(isEmpty)("is-placeholder").toSeq)
      text_=(label)
    }
  }

  def comboBox[S](name: String): ComboBox[S] =
    comboBox(name)({})

  def comboBox[S](name: String)(init: ComboBox[S] ?=> Unit): ComboBox[S] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ComboBox[S](name, currentScope)
      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given ComboBox[S] = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def items[S](using comboBox: ComboBox[S]): ListProperty[S] =
    comboBox.getItems

  def items_=[S](value: ListProperty[S])(using comboBox: ComboBox[S]): Unit =
    comboBox.setItems(value)

  def value[S](using comboBox: ComboBox[S]): js.Array[S] =
    comboBox.valueProperty.get

  def selectedItem[S](using comboBox: ComboBox[S]): S | Null =
    comboBox.getSelectedItem

  def selectedItem_=[S](value: S | Null)(using comboBox: ComboBox[S]): Unit =
    comboBox.setSelectedItem(value)

  def selectedIndex(using comboBox: ComboBox[?]): Int =
    comboBox.getSelectedIndex

  def dropdownHeightPx(using comboBox: ComboBox[?]): Double =
    comboBox.dropdownHeightPxProperty.get

  def dropdownHeightPx_=(value: Double)(using comboBox: ComboBox[?]): Unit =
    comboBox.dropdownHeightPxProperty.set(value)

  def rowHeightPx(using comboBox: ComboBox[?]): Double =
    comboBox.rowHeightPxProperty.get

  def rowHeightPx_=(value: Double)(using comboBox: ComboBox[?]): Unit =
    comboBox.rowHeightPxProperty.set(value)

  def itemRenderer[S](using comboBox: ComboBox[S]): ItemRenderer[S] | Null =
    comboBox.getItemRenderer

  def itemRenderer_=[S](renderer: ItemRenderContext[S] ?=> Unit)(using comboBox: ComboBox[S]): Unit =
    comboBox.setItemRenderer(ItemRenderer(renderer))

  def valueRenderer[S](using comboBox: ComboBox[S]): ValueRenderer[S] | Null =
    comboBox.getValueRenderer

  def valueRenderer_=[S](renderer: ValueRenderContext[S] ?=> Unit)(using comboBox: ComboBox[S]): Unit =
    comboBox.setValueRenderer(ValueRenderer(renderer))

  def open(using comboBox: ComboBox[?]): Unit =
    comboBox.open()

  def close(using comboBox: ComboBox[?]): Unit =
    comboBox.close(refocus = false)

  def comboItem[S](using context: ItemRenderContext[S]): S =
    context.item

  def comboItemIndex(using context: ItemRenderContext[?]): Int =
    context.index

  def comboItemSelected(using context: ItemRenderContext[?]): Boolean =
    context.selected

  def comboSelectedItems[S](using context: ValueRenderContext[S]): js.Array[S] =
    context.selectedItems.get

  def comboRenderedSelectedItem[S](using context: ValueRenderContext[S]): S | Null =
    context.selectedItem
}
