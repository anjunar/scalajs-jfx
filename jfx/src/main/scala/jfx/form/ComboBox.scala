package jfx.form

import jfx.control.{TableCell, TableColumn, TableRow, TableView}
import jfx.core.component.{FormRegistrationBoundary, ManagedElementComponent, NodeComponent}
import jfx.core.state.{Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.form.Formular
import jfx.layout.Span
import jfx.layout.Viewport
import org.scalajs.dom.{Event, HTMLDivElement, HTMLElement, HTMLSpanElement, KeyboardEvent, Node, document}

import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

class ComboBox[S](val name: String, override val standalone: Boolean = false)
    extends ManagedElementComponent[HTMLDivElement], Control[js.Array[S], HTMLDivElement] {

  override val valueProperty: ListProperty[S] = ListProperty()
  initControlValidation()

  private val itemsRefProperty: Property[ListProperty[S]] = Property(ListProperty())
  private val openProperty: Property[Boolean] = Property(false)

  val itemRendererProperty: Property[ComboBox.ItemRenderer[S] | Null] =
    Property[ComboBox.ItemRenderer[S] | Null](null)

  val valueRendererProperty: Property[ComboBox.ValueRenderer[S] | Null] =
    Property[ComboBox.ValueRenderer[S] | Null](null)

  val dropdownFooterRendererProperty: Property[ComboBox.DropdownFooterRenderer[S] | Null] =
    Property[ComboBox.DropdownFooterRenderer[S] | Null](null)

  val identityByProperty: Property[ComboBox.IdentityBy[S] | Null] =
    Property[ComboBox.IdentityBy[S] | Null](null)

  val selectedItemProperty: Property[S | Null] = Property(null)
  val selectedIndexProperty: Property[Int] = Property(-1)
  val multipleSelectionProperty: Property[Boolean] = Property(false)
  val dropdownHeightPxProperty: Property[Double] = Property(240.0)
  val rowHeightPxProperty: Property[Double] = Property(36.0)

  private var valueHost: ComboBox.RenderHost | Null = null
  private var indicatorElement: HTMLSpanElement | Null = null
  private var structureInitialized = false
  private var itemsObserver: Disposable = ComboBox.noopDisposable
  private var overlayConf: Viewport.OverlayConf | Null = null
  private var popupListenerCleanup: Disposable = ComboBox.noopDisposable
  private var popupListenerInstallHandle: Option[SetTimeoutHandle] = None
  private var dropdownTable: TableView[S] | Null = null
  private var updatingValueProperty = false
  private var disposed = false
  private var renderScope: Scope = Scope.root()
  private var renderEnclosingForm: Option[Formular[?, ?]] = None

  override val element: HTMLDivElement = {
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

  override protected def mountContent(): Unit =
    ensureStructure()

  private[jfx] def captureRenderContext(scope: Scope, enclosingForm: Option[Formular[?, ?]]): Unit = {
    renderScope = scope
    renderEnclosingForm = enclosingForm
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

  private val multipleSelectionObserver =
    multipleSelectionProperty.observe { _ =>
      reconcileValue()
      Option(dropdownTable).foreach(_.refresh())
    }
  addDisposable(multipleSelectionObserver)

  private val openObserver =
    openProperty.observe(syncOpenState)
  addDisposable(openObserver)

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      val host = new ComboBox.RenderHost("jfx-combo-box__value")
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

  def getDropdownFooterRenderer: ComboBox.DropdownFooterRenderer[S] | Null =
    dropdownFooterRendererProperty.get

  def setDropdownFooterRenderer(renderer: ComboBox.DropdownFooterRenderer[S] | Null): Unit =
    dropdownFooterRendererProperty.set(renderer)

  def getIdentityBy: ComboBox.IdentityBy[S] | Null =
    identityByProperty.get

  def setIdentityBy(identityBy: ComboBox.IdentityBy[S] | Null): Unit =
    identityByProperty.set(identityBy)

  def getSelectedItem: S | Null =
    selectedItemProperty.get

  def setSelectedItem(item: S | Null): Unit =
    applySelection(Option(item).toSeq, preferredSelectedItem = item, markDirty = false, closeAfter = false)

  def getSelectedIndex: Int =
    selectedIndexProperty.get

  def clearSelection(): Unit =
    applySelection(Vector.empty, preferredSelectedItem = null, markDirty = false, closeAfter = false)

  def isMultipleSelection: Boolean =
    multipleSelectionProperty.get

  def setMultipleSelection(value: Boolean): Unit =
    multipleSelectionProperty.set(value)

  def open(): Unit =
    if (!isOpen) {
      ensureStructure()

      val table = buildDropdownTable()
      val footerContent = renderDropdownFooterContent()
      val panel = new ComboBox.DropdownPanel(this, table, footerContent)
      val anchorWidth = math.max(element.getBoundingClientRect().width, 120.0)
      val maxDropdownHeight =
        normalizedDropdownHeight + Option.when(footerContent != null)(54.0).getOrElse(2.0)
      val overlay = new Viewport.OverlayConf(
        anchor = element,
        content = () => panel,
        offsetYPx = 6.0,
        widthPx = Some(anchorWidth),
        minWidthPx = Some(anchorWidth),
        maxHeightPx = Some(maxDropdownHeight)
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
      } else if (document.activeElement == element) {
        element.blur()
        focusedProperty.set(false)
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

    val currentValues = valueProperty.iterator.map(_.asInstanceOf[S | Null]).toVector
    val normalizedValues = normalizeSelection(currentValues)

    if (!sameSelection(currentValues, normalizedValues)) {
      updatingValueProperty = true
      try valueProperty.setAll(normalizedValues)
      finally updatingValueProperty = false
    }

    val normalizedSelectedItem =
      Option(selectedItemProperty.get)
        .flatMap(item => normalizedValues.find(current => sameItem(current, item)))
        .orElse(normalizedValues.headOption)
        .orNull

    selectedItemProperty.set(normalizedSelectedItem)
    syncSelectedIndex()
    rerenderValueHost()
  }

  private def syncSelectedIndex(): Unit = {
    val selectedItem = selectedItemProperty.get
    val index =
      if (selectedItem == null) -1
      else getItems.indexWhere(item => sameItem(item, selectedItem))

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
      table.refresh()
    }

  private def updateSelection(item: S | Null, markDirty: Boolean, closeAfter: Boolean): Unit = {
    applySelection(Option(item).toSeq, preferredSelectedItem = item, markDirty = markDirty, closeAfter = closeAfter)
  }

  private def rerenderValueHost(): Unit =
    Option(valueHost).foreach { host =>
      host.setContent(renderValueContent())
    }

  private def renderValueContent(): NodeComponent[? <: Node] | Null = {
    withRenderContext {
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
  }

  private def renderDropdownFooterContent(): NodeComponent[? <: Node] | Null = {
    withRenderContext {
      given ComboBox.DropdownFooterRenderContext[S] =
        ComboBox.DropdownFooterRenderContext(
          comboBox = this,
          selectedItems = valueProperty,
          selectedItem = selectedItemProperty.get
        )

      val renderer = dropdownFooterRendererProperty.get
      if (renderer == null) {
        null
      } else {
        renderer.render
      }
    }
  }

  private def renderItemContent(
    item: S,
    index: Int,
    selected: Boolean
  ): NodeComponent[? <: Node] | Null = {
    withRenderContext {
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
  }

  private def withRenderContext[A](block: => A): A =
    DslRuntime.withScope(renderScope) {
      DslRuntime.withComponentContext(ComponentContext(None, renderEnclosingForm)) {
        block
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
    column.setCellFactory(_ => new ComboBox.ItemCell(this))

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

  private[form] def toggleSelection(item: S): Unit =
    if (isMultipleSelection) {
      val currentValues = normalizeSelection(valueProperty.iterator.map(_.asInstanceOf[S | Null]).toVector)
      val nextValues =
        if (currentValues.exists(current => sameItem(current, item))) {
          currentValues.filterNot(current => sameItem(current, item))
        }
        else currentValues :+ item

      val preferredSelectedItem =
        if (nextValues.exists(current => sameItem(current, item))) item
        else null

      applySelection(
        nextValues,
        preferredSelectedItem = preferredSelectedItem,
        markDirty = true,
        closeAfter = false
      )
    } else {
      updateSelection(item, markDirty = true, closeAfter = true)
    }

  private[form] def isItemSelected(item: S): Boolean =
    valueProperty.iterator.exists(current => sameItem(current, item))

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

  private def applySelection(
    values: IterableOnce[S | Null],
    preferredSelectedItem: S | Null,
    markDirty: Boolean,
    closeAfter: Boolean
  ): Unit = {
    val normalizedValues = normalizeSelection(values)

    updatingValueProperty = true
    try valueProperty.setAll(normalizedValues)
    finally updatingValueProperty = false

    val resolvedSelectedItem =
      Option(preferredSelectedItem)
        .flatMap(item => normalizedValues.find(current => sameItem(current, item)))
        .orElse(normalizedValues.headOption)
        .orNull

    selectedItemProperty.set(resolvedSelectedItem)
    syncSelectedIndex()
    rerenderValueHost()

    if (markDirty) {
      dirtyProperty.set(true)
    }

    if (closeAfter) {
      close(refocus = true)
    }
  }

  private def normalizeSelection(values: IterableOnce[S | Null]): Vector[S] = {
    val normalized = Vector.newBuilder[S]
    var collected = Vector.empty[S]

    values.iterator.foreach { value =>
      if (value != null) {
        val current = value.asInstanceOf[S]
        if (!collected.exists(existing => sameItem(existing, current))) {
          collected = collected :+ current
        }
      }
    }

    normalized ++= collected
    val result = normalized.result()
    if (isMultipleSelection) result else result.headOption.toVector
  }

  private def sameSelection(current: Seq[S | Null], normalized: Seq[S]): Boolean =
    current.lengthCompare(normalized.length) == 0 &&
      current.iterator.zip(normalized.iterator).forall { case (left, right) => sameItem(left, right) }

  private def sameItem(left: S | Null, right: S | Null): Boolean =
    if (left == null || right == null) left == right
    else identityOf(left) == identityOf(right)

  private def identityOf(item: S | Null): Any =
    if (item == null) null
    else {
      val current = item.asInstanceOf[S]
      val provider = identityByProperty.get
      if (provider == null) current.asInstanceOf[Any]
      else {
        val identity = provider(current)
        if (identity == null) current.asInstanceOf[Any]
        else identity
      }
    }
}

object ComboBox {

  type IdentityBy[S] = S => Any

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

  final case class DropdownFooterRenderContext[S](
    comboBox: ComboBox[S],
    selectedItems: ReadOnlyProperty[js.Array[S]],
    selectedItem: S | Null
  )

  final class ItemRenderer[S](
    private val run: ItemRenderContext[S] => NodeComponent[? <: Node] | Null
  ) {
    def render(using context: ItemRenderContext[S]): NodeComponent[? <: Node] | Null =
      run(context)
  }

  object ItemRenderer {
    def apply[S](renderer: ItemRenderContext[S] ?=> NodeComponent[? <: Node] | Null): ItemRenderer[S] =
      new ItemRenderer[S](context => renderer(using context))
  }

  final class ValueRenderer[S](
    private val run: ValueRenderContext[S] => NodeComponent[? <: Node] | Null
  ) {
    def render(using context: ValueRenderContext[S]): NodeComponent[? <: Node] | Null =
      run(context)
  }

  object ValueRenderer {
    def apply[S](renderer: ValueRenderContext[S] ?=> NodeComponent[? <: Node] | Null): ValueRenderer[S] =
      new ValueRenderer[S](context => renderer(using context))
  }

  final class DropdownFooterRenderer[S](
    private val run: DropdownFooterRenderContext[S] => NodeComponent[? <: Node] | Null
  ) {
    def render(using context: DropdownFooterRenderContext[S]): NodeComponent[? <: Node] | Null =
      run(context)
  }

  object DropdownFooterRenderer {
    def apply[S](
      renderer: DropdownFooterRenderContext[S] ?=> NodeComponent[? <: Node] | Null
    ): DropdownFooterRenderer[S] =
      new DropdownFooterRenderer[S](context => renderer(using context))
  }

  private val noopDisposable: Disposable = () => ()

  private final class RenderHost(className: String)
      extends ManagedElementComponent[HTMLDivElement], FormRegistrationBoundary {

    override val element: HTMLDivElement = {
      val divElement = newElement("div")
      divElement.className = className
      divElement
    }

    def setContent(content: NodeComponent[? <: Node] | Null): Unit = {
      clearChildren()
      if (content != null) {
        addChild(content)
      }
    }
  }

  private final class DropdownPanel[S](
    comboBox: ComboBox[S],
    table: TableView[S],
    footerContent: NodeComponent[? <: Node] | Null
  )
      extends ManagedElementComponent[HTMLDivElement], FormRegistrationBoundary {

    private var contentInitialized = false
    private val footerHost = new RenderHost("jfx-combo-box__footer")

    override val element: HTMLDivElement = {
      val divElement = newElement("div")
      divElement.className = "jfx-combo-box__dropdown"
      divElement
    }

    private val actionClickListener: Event => Unit = event => {
      if (containsComboBoxAction(event.target)) {
        comboBox.close(refocus = false)
      }
    }

    element.addEventListener("click", actionClickListener)
    addDisposable(() => element.removeEventListener("click", actionClickListener))

    override protected def mountContent(): Unit =
      if (!contentInitialized) {
        contentInitialized = true
        addChild(table)
        if (footerContent != null) {
          footerHost.setContent(footerContent)
          addChild(footerHost)
        }
      } else {
        ()
      }

    private def containsComboBoxAction(target: Any): Boolean = {
      var currentNode =
        target match {
          case element: HTMLElement => element.asInstanceOf[Node | Null]
          case node: Node           => node
          case _                    => null
        }

      while (currentNode != null && currentNode != element) {
        currentNode match {
          case htmlElement: HTMLElement if htmlElement.hasAttribute("data-jfx-combo-box-action") =>
            return true
          case _ =>
            currentNode = currentNode.parentNode
        }
      }

      false
    }
  }

  private final class ItemCell[S](comboBox: ComboBox[S]) extends TableCell[S, S] {

    private val host = new RenderHost("jfx-combo-box__item")
    addChild(host)

    override protected def updateItem(item: S | Null, empty: Boolean): Unit = {
      val isEmptyCell = empty || item == null
      setCellClass("jfx-table-cell-empty", isEmptyCell)

      if (isEmptyCell) {
        host.setContent(null)
      } else {
        val currentItem = item.asInstanceOf[S]
        host.setContent(comboBox.renderItemContent(currentItem, getIndex, comboBox.isItemSelected(currentItem)))
      }
    }

    override protected def updateSelected(selected: Boolean): Unit = {
      super.updateSelected(selected)

      val item = getItem
      if (!isEmpty && item != null) {
        val currentItem = item.asInstanceOf[S]
        host.setContent(comboBox.renderItemContent(currentItem, getIndex, comboBox.isItemSelected(currentItem)))
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
        comboBox.toggleSelection(item.asInstanceOf[S])
      }
    }

    override protected def updateSelected(selected: Boolean): Unit = {
      val item = getItem
      val effectiveSelected =
        if (isEmpty || item == null) false
        else comboBox.isItemSelected(item.asInstanceOf[S])

      super.updateSelected(effectiveSelected)
    }
  }

  private def displayText(value: Any): String =
    if (value == null) ""
    else value.toString

  private[form] def defaultItemRenderer[S]()(using context: ItemRenderContext[S]): NodeComponent[? <: Node] | Null = {
    val component = new Span()
    component.classProperty += "jfx-combo-box__item-text"
    component.textContent = displayText(context.item)
    component
  }

  private[form] def defaultValueRenderer[S]()(using context: ValueRenderContext[S]): NodeComponent[? <: Node] | Null = {
    val selectedItem = context.selectedItem
    val isEmpty = selectedItem == null
    val label =
      if (isEmpty) {
        Option(context.comboBox.placeholderProperty.get).filter(_.trim.nonEmpty).getOrElse("")
      } else {
        displayText(selectedItem)
      }

    val component = new Span()
    component.classProperty ++=
      (Vector("jfx-combo-box__value-text") ++ Option.when(isEmpty)("is-placeholder").toSeq)
    component.textContent = label
    component
  }

  def comboBox[S](name: String, standalone: Boolean = false)(init: ComboBox[S] ?=> Unit): ComboBox[S] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ComboBox[S](name, standalone)
      component.captureRenderContext(currentScope, currentContext.enclosingForm)

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

  def identityBy[S](using comboBox: ComboBox[S]): IdentityBy[S] | Null =
    comboBox.getIdentityBy

  def identityBy_=[S](value: IdentityBy[S] | Null)(using comboBox: ComboBox[S]): Unit =
    comboBox.setIdentityBy(value)

  def multipleSelection(using comboBox: ComboBox[?]): Boolean =
    comboBox.isMultipleSelection

  def multipleSelection_=(value: Boolean)(using comboBox: ComboBox[?]): Unit =
    comboBox.setMultipleSelection(value)

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

  def itemRenderer_=[S](
    renderer: ItemRenderContext[S] ?=> NodeComponent[? <: Node] | Null
  )(using comboBox: ComboBox[S]): Unit =
    DslRuntime.currentScope { currentScope =>
      comboBox.setItemRenderer(
        new ItemRenderer[S](context => {
          given ItemRenderContext[S] = context
          given Scope = currentScope
          renderer
        })
      )
    }

  def valueRenderer[S](using comboBox: ComboBox[S]): ValueRenderer[S] | Null =
    comboBox.getValueRenderer

  def valueRenderer_=[S](
    renderer: ValueRenderContext[S] ?=> NodeComponent[? <: Node] | Null
  )(using comboBox: ComboBox[S]): Unit =
    DslRuntime.currentScope { currentScope =>
      comboBox.setValueRenderer(
        new ValueRenderer[S](context => {
          given ValueRenderContext[S] = context
          given Scope = currentScope
          renderer
        })
      )
    }

  def dropdownFooterRenderer[S](using comboBox: ComboBox[S]): DropdownFooterRenderer[S] | Null =
    comboBox.getDropdownFooterRenderer

  def dropdownFooterRenderer_=[S](
    renderer: DropdownFooterRenderContext[S] ?=> NodeComponent[? <: Node] | Null
  )(using comboBox: ComboBox[S]): Unit =
    DslRuntime.currentScope { currentScope =>
      comboBox.setDropdownFooterRenderer(
        new DropdownFooterRenderer[S](context => {
          given DropdownFooterRenderContext[S] = context
          given Scope = currentScope
          renderer
        })
      )
    }

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

  def comboFooterSelectedItems[S](using context: DropdownFooterRenderContext[S]): js.Array[S] =
    context.selectedItems.get

  def comboFooterRenderedSelectedItem[S](using context: DropdownFooterRenderContext[S]): S | Null =
    context.selectedItem
}
