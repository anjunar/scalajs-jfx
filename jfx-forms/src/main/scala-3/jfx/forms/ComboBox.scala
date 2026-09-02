package jfx.forms

import jfx.control.table.{TableColumn, TableView}
import jfx.control.table.TableColumn.*
import jfx.control.table.TableView.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Condition.when
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import jfx.core.statement.Foreach.foreach
import jfx.forms.Form.FormContext
import jfx.viewport.Overlay
import jfx.viewport.Overlay.*
import org.scalajs.dom

final class ComboBox[T] private (
    val name: String,
    val standalone: Boolean,
    configure: ComboBox[T] ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[T],
      Placeholder {

  import ComboBox.*

  override val tagName: String = "div"

  val valueProperty: Property[T]                      = Property(null.asInstanceOf[T])
  val selectionProperty: ListProperty[T]              = ListProperty()
  val itemsProperty: ListProperty[T]                  = ListProperty()
  val openProperty: Property[Boolean]                 = Property(false)
  val multipleSelectionProperty: Property[Boolean]    = Property(false)
  val dropdownWidthProperty: Property[Option[Double]] = Property(None)
  val dropdownHeightProperty: Property[Double]        = Property(240.0)
  val rowHeightProperty: Property[Double]             = Property(36.0)
  val converterProperty: Property[T => String]        =
    Property(value => if (value == null) "" else value.toString)
  val identityProperty: Property[T => Any]              = Property(value => value.asInstanceOf[Any])
  val selectionTextProperty: Property[Seq[T] => String] =
    Property(values => values.headOption.map(converterProperty.get).getOrElse(""))

  private val placeholderProperty                                       = Property("")
  private val itemRendererProperty: Property[Option[ItemRenderer[T]]]   = Property(None)
  private val valueRendererProperty: Property[Option[ValueRenderer[T]]] = Property(None)
  private val footerRendererProperty: Property[Option[FooterRenderer]]  = Property(None)
  private val displayRevisionProperty                                   = Property(0)
  private var syncingValueAndSelection                                  = false

  private val displayTextProperty: ReadOnlyProperty[String] =
    displayRevisionProperty.map { _ =>
      val selected = selectionProperty.toSeq
      if (selected.isEmpty) placeholderProperty.get
      else selectionTextProperty.get(selected)
    }

  private val placeholderVisibleProperty: ReadOnlyProperty[Boolean] =
    displayRevisionProperty.map(_ => selectionProperty.isEmpty)

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)
    installObservers()

    render(this, cursor) {
      addClass("jfx-combo-box")
      classIf("jfx-combo-box-open", openProperty)
      classIf("jfx-combo-box-readonly", editableProperty.map(!_))
      setAttribute("name", name)
      setAttribute("tabindex", "0")
      setAttribute("role", "combobox")
      addDisposable(
        openProperty.observe(value => setAttribute("aria-expanded", value.toString))
      )
      addDisposable(editableProperty.observe { editable =>
        setAttribute("aria-disabled", (!editable).toString)
      })

      onClick(_ => toggle())
      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }
      on("keydown")(handleKeyDown)

      div {
        classes = Seq("jfx-combo-box__value")

        valueRendererProperty.get match {
          case Some(renderer) =>
            foreach(selectionProperty) { item =>
              renderer(item)(using summon[AbstractComponent])(using summon[Cursor])
            }
          case None =>
            div {
              classes = Seq("jfx-combo-box__value-text")
              classIf("is-placeholder", placeholderVisibleProperty)
              text(displayTextProperty) {}
            }
        }
      }

      div {
        classes = Seq("jfx-combo-box__indicator", "material-icons")
        text("arrow_drop_down") {}
      }

      when(openProperty) {
        overlay(dropdownWidthProperty.get) {
          div {
            classes = Seq("jfx-combo-box__dropdown")
            val dropdownHeight = math.max(0.0, dropdownHeightProperty.get)
            style {
              height = s"${dropdownHeight}px"
              maxHeight = s"${dropdownHeight}px"
              display = "flex"
              flexDirection = "column"
            }

            tableView[T] {
              classes = Seq("jfx-combo-box__table")
              TableView.showHeader = false
              TableView.rowHeight = math.max(1.0, rowHeightProperty.get)
              TableView.tablePrefWidth =
                Overlay.effectiveWidth.map(width => math.max(0.0, width - 2.0))
              TableView.items = ComboBox.this.itemsProperty
              style {
                height = "auto"
                minHeight = "0"
                flex = "1 1 auto"
              }

              column[T, Any]("") {
                TableColumn.prefWidth =
                  Overlay.effectiveWidth.map(width => math.max(0.0, width - 4.0))
                TableColumn.cell { item =>
                  val selected = selectedProperty(item)
                  div {
                    classes = Seq("jfx-combo-box__item")
                    classIf("is-selected", selected)
                    onClick { event =>
                      event.stopPropagation()
                      selectItem(item)
                    }

                    itemRendererProperty.get match {
                      case Some(renderer) =>
                        renderer(item, selected)(using summon[AbstractComponent])(using
                          summon[Cursor]
                        )
                      case None =>
                        div {
                          classes = Seq("jfx-combo-box__item-text")
                          text(converterProperty.get(item)) {}
                        }
                    }
                  }
                }
              }
            }

            footerRendererProperty.get.foreach { renderer =>
              div {
                classes = Seq("jfx-combo-box__footer")
                renderer(using summon[AbstractComponent])(using summon[Cursor])
              }
            }
          }
        }
      }

      addDisposable(validators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(s"ComboBox '$name' requires a Form or FieldSet context.")
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
  }

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  def toggle(): Unit =
    if (editableProperty.get) openProperty.set(!openProperty.get)
    else openProperty.set(false)

  def selectItem(item: T): Unit =
    if (editableProperty.get) {
      if (multipleSelectionProperty.get) {
        val index = selectionProperty.indexWhere(isSame(_, item))
        if (index >= 0) selectionProperty.remove(index)
        else selectionProperty += item
      } else {
        selectionProperty.setAll(Seq(item))
        openProperty.set(false)
      }
      dirtyProperty.set(true)
    }

  private def installObservers(): Unit = {
    addDisposable(selectionProperty.observe { _ =>
      bumpDisplayRevision()
      if (!syncingValueAndSelection) {
        syncingValueAndSelection = true
        try valueProperty.set(selectionProperty.headOption.getOrElse(null.asInstanceOf[T]))
        finally syncingValueAndSelection = false
      }
    })
    addDisposable(valueProperty.observeWithoutInitial { value =>
      if (!syncingValueAndSelection) {
        syncingValueAndSelection = true
        try {
          if (value == null) selectionProperty.clear()
          else {
            val canonical = canonicalItem(value)
            selectionProperty.setAll(Seq(canonical))
          }
        } finally syncingValueAndSelection = false
      }
    })
    addDisposable(itemsProperty.observeChanges(_ => reconcileSelection()))
    addDisposable(converterProperty.observeWithoutInitial(_ => bumpDisplayRevision()))
    addDisposable(selectionTextProperty.observeWithoutInitial(_ => bumpDisplayRevision()))
    addDisposable(identityProperty.observeWithoutInitial(_ => {
      reconcileSelection()
      bumpDisplayRevision()
    }))
    addDisposable(placeholderProperty.observeWithoutInitial(_ => bumpDisplayRevision()))
    addDisposable(editableProperty.observe { editable =>
      if (!editable) openProperty.set(false)
    })
  }

  private def reconcileSelection(): Unit = {
    val reconciled = selectionProperty.toSeq.map(canonicalItem)
    val changed    = reconciled.length != selectionProperty.length ||
      reconciled.zip(selectionProperty.toSeq).exists { case (next, current) =>
        next.asInstanceOf[AnyRef] ne current.asInstanceOf[AnyRef]
      }
    if (changed) selectionProperty.setAll(reconciled)
  }

  private def canonicalItem(value: T): T =
    itemsProperty.find(isSame(_, value)).getOrElse(value)

  private def selectedProperty(item: T): ReadOnlyProperty[Boolean] =
    selectionProperty.map(values => values.toSeq.exists(isSame(_, item)))

  private def isSame(left: T, right: T): Boolean = {
    val leftValue  = left.asInstanceOf[Any]
    val rightValue = right.asInstanceOf[Any]
    if (leftValue == null || rightValue == null) leftValue == rightValue
    else identityProperty.get(left) == identityProperty.get(right)
  }

  private def handleKeyDown(event: jfx.core.render.UiEvent): Unit =
    event.raw match {
      case keyboard: dom.KeyboardEvent if editableProperty.get =>
        keyboard.key match {
          case "ArrowDown" | "ArrowUp" =>
            event.preventDefault()
            openProperty.set(true)
          case "Enter" | " " =>
            event.preventDefault()
            toggle()
          case "Escape" if openProperty.get =>
            event.preventDefault()
            openProperty.set(false)
          case _ => ()
        }
      case _ if !editableProperty.get => openProperty.set(false)
      case _                          => ()
    }

  private def bumpDisplayRevision(): Unit =
    displayRevisionProperty.setAlways(displayRevisionProperty.get + 1)
}

object ComboBox {
  type ItemRenderer[T] =
    (T, ReadOnlyProperty[Boolean]) => AbstractComponent ?=> Cursor ?=> Unit
  type ValueRenderer[T] = T => AbstractComponent ?=> Cursor ?=> Unit
  type FooterRenderer   = AbstractComponent ?=> Cursor ?=> Unit

  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  def comboBox[T](
      name: String,
      standalone: Boolean = false
  )(body: ComboBox[T] ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): ComboBox[T] =
    DslLayer.child(new ComboBox[T](name, standalone, body)) {}

  def items[T](using comboBox: ComboBox[T]): ListProperty[T] = comboBox.itemsProperty

  def items_=[T](using comboBox: ComboBox[T])(value: IterableOnce[T]): Unit =
    comboBox.itemsProperty.setAll(value)

  def selection[T](using comboBox: ComboBox[T]): ListProperty[T] = comboBox.selectionProperty

  def converter[T](using comboBox: ComboBox[T]): T => String = comboBox.converterProperty.get

  def converter_=[T](using comboBox: ComboBox[T])(value: T => String): Unit =
    comboBox.converterProperty.set(value)

  def identityBy[T](using comboBox: ComboBox[T]): T => Any = comboBox.identityProperty.get

  def identityBy_=[T](using comboBox: ComboBox[T])(value: T => Any): Unit =
    comboBox.identityProperty.set(value)

  def selectionText[T](using comboBox: ComboBox[T]): Seq[T] => String =
    comboBox.selectionTextProperty.get

  def selectionText_=[T](using comboBox: ComboBox[T])(value: Seq[T] => String): Unit =
    comboBox.selectionTextProperty.set(value)

  def rowHeight(using comboBox: ComboBox[?]): Double = comboBox.rowHeightProperty.get

  def rowHeight_=(value: Double)(using comboBox: ComboBox[?]): Unit =
    comboBox.rowHeightProperty.set(value)

  def dropdownHeight(using comboBox: ComboBox[?]): Double = comboBox.dropdownHeightProperty.get

  def dropdownHeight_=(value: Double)(using comboBox: ComboBox[?]): Unit =
    comboBox.dropdownHeightProperty.set(value)

  def dropdownWidth(using comboBox: ComboBox[?]): Option[Double] =
    comboBox.dropdownWidthProperty.get

  def dropdownWidth_=(value: Double)(using comboBox: ComboBox[?]): Unit =
    comboBox.dropdownWidthProperty.set(Some(value))

  def multiSelect(using comboBox: ComboBox[?]): Boolean =
    comboBox.multipleSelectionProperty.get

  def multiSelect_=(value: Boolean)(using comboBox: ComboBox[?]): Unit =
    comboBox.multipleSelectionProperty.set(value)

  def itemRenderer[T](using comboBox: ComboBox[T])(renderer: ItemRenderer[T]): Unit =
    comboBox.itemRendererProperty.set(Some(renderer))

  def valueRenderer[T](using comboBox: ComboBox[T])(renderer: ValueRenderer[T]): Unit =
    comboBox.valueRendererProperty.set(Some(renderer))

  def footerRenderer(using comboBox: ComboBox[?])(renderer: FooterRenderer): Unit =
    comboBox.footerRendererProperty.set(Some(renderer))
}
