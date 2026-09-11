package ui.control.tabs

import ui.control.tabs.Tabs
import ui.control.tabs.Tabs.{RenderMode, TabSpec}
import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div.div
import ui.core.render.{Cursor, UiEvent}
import ui.core.state.{ListProperty, Property, ReadOnlyProperty}
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.core.statement.Foreach.foreachIndexed
import ui.core.text.TextValue

import scala.scalajs.js

final class Tabs(
    initialSelectedIndex: Int = 0,
    initialRenderMode: RenderMode = RenderMode.ActiveOnly,
    configure: Tabs ?=> Unit = Tabs.emptyConfiguration
) extends AbstractComponent {

  override val tagName: String = "section"

  val tabsProperty: ListProperty[TabSpec]      = ListProperty[TabSpec]()
  val selectedIndexProperty: Property[Int]     = Property(math.max(0, initialSelectedIndex))
  val renderModeProperty: Property[RenderMode] = Property(initialRenderMode)
  private[control] val contentRevisionProperty: Property[Int]       = Property(0)
  private val contentComponentProperty: Property[AbstractComponent] =
    Property(new EmptyTabsContent)

  def addTab(tab: TabSpec): Unit =
    tabsProperty += tab

  def setSelectedIndex(index: Int): Unit =
    selectedIndexProperty.set(normalizedIndex(index))

  def getSelectedIndex: Int =
    normalizedIndex(selectedIndexProperty.get)

  override def compose(cursor: Cursor): Unit = {
    // Tabs, render mode and initial selection are structural configuration and
    // must be known before header and panel mount points claim SSR nodes.
    configure(using this)
    normalizeSelectedIndex()
    contentComponentProperty.setAlways(contentComponent(renderModeProperty.get))

    DslLayer.render(this, cursor) {
      addClass("ui-tabs")
      classIf("ui-tabs--empty", tabsProperty.map(_.isEmpty))
      on("keydown")(handleKeyDown)

      div {
        classes = Seq("ui-tabs__header")
        setDslAttribute("role", "tablist")

        foreachIndexed(tabsProperty) { (tab, index) =>
          val active = selectedIndexProperty.map(_ == index)

          button(tab.titleProperty) { trigger ?=>
            classes = Seq("ui-tabs__trigger")
            classIf("ui-tabs__trigger--active", active)
            buttonType("button")
            setDslAttribute("role", "tab")
            trigger.addDisposable(active.observe { selected =>
              setDslAttribute("aria-selected", selected.toString)
              setDslAttribute("tabindex", if (selected) "0" else "-1")
            })
            onClick(_ => setSelectedIndex(index))
          }
        }
      }

      div {
        classes = Seq("ui-tabs__content")
        dynamic(contentComponentProperty)
      }
    }

    // Rebuild listeners run after the trigger and panel listeners. A selection
    // change can therefore finish updating the current tree before ActiveOnly
    // replaces and disposes that tree during the same property notification.
    installObservers()
  }

  private def installObservers(): Unit = {
    addDisposable(tabsProperty.observeChanges(_ => handleTabsChanged()))
    addDisposable(selectedIndexProperty.observeWithoutInitial(handleSelectedIndexChanged))
    addDisposable(renderModeProperty.observeWithoutInitial { mode =>
      contentComponentProperty.setAlways(contentComponent(mode))
    })
  }

  private def handleTabsChanged(): Unit = {
    val current    = selectedIndexProperty.get
    val normalized = normalizedIndex(current)

    if (current == normalized) bumpContentRevision()
    else selectedIndexProperty.set(normalized)
  }

  private def handleSelectedIndexChanged(index: Int): Unit = {
    val normalized = normalizedIndex(index)

    if (index == normalized) bumpContentRevision()
    else selectedIndexProperty.set(normalized)
  }

  private def normalizeSelectedIndex(): Unit =
    selectedIndexProperty.set(normalizedIndex(selectedIndexProperty.get))

  private def normalizedIndex(index: Int): Int =
    if (tabsProperty.isEmpty) 0
    else math.max(0, math.min(tabsProperty.length - 1, index))

  private def bumpContentRevision(): Unit =
    contentRevisionProperty.setAlways(contentRevisionProperty.get + 1)

  private def contentComponent(mode: RenderMode): AbstractComponent =
    mode match {
      case RenderMode.ActiveOnly        => new ActiveTabsContent(this)
      case RenderMode.KeepMountedHidden => new MountedTabsContent(this)
    }

  private[control] def activeTab: Option[(TabSpec, Int)] =
    if (tabsProperty.isEmpty) None
    else {
      val index = getSelectedIndex
      Some(tabsProperty(index) -> index)
    }

  private def handleKeyDown(event: UiEvent): Unit =
    keyboardKey(event).foreach {
      case "ArrowRight" | "ArrowDown" =>
        event.preventDefault()
        setSelectedIndex(getSelectedIndex + 1)
      case "ArrowLeft" | "ArrowUp" =>
        event.preventDefault()
        setSelectedIndex(getSelectedIndex - 1)
      case "Home" =>
        event.preventDefault()
        setSelectedIndex(0)
      case "End" =>
        event.preventDefault()
        setSelectedIndex(tabsProperty.length - 1)
      case _ => ()
    }

  private def keyboardKey(event: UiEvent): Option[String] =
    event.raw match {
      case raw: js.Object =>
        val key = raw.asInstanceOf[js.Dynamic].selectDynamic("key")
        Option.when(js.typeOf(key) == "string")(key.asInstanceOf[String])
      case _ => None
    }
}

object Tabs {
  type Renderer = Tabs ?=> AbstractComponent ?=> Cursor ?=> Unit

  enum RenderMode {
    case ActiveOnly
    case KeepMountedHidden
  }

  final class TabSpec(
      val titleProperty: ReadOnlyProperty[String],
      val render: Renderer
  ) {
    def title: String = titleProperty.get
  }

  private val emptyConfiguration: Tabs ?=> Unit =
    (_: Tabs) ?=> ()

  def tabs(
      body: Tabs ?=> Unit
  )(using AbstractComponent, Cursor): Tabs =
    DslLayer.child(new Tabs(configure = body)) {}

  def tab[T](
      title: T
  )(
      content: Renderer
  )(using tabs: Tabs, textValue: TextValue[T]): Unit =
    tabs.addTab(
      new TabSpec(
        textValue.asReadOnlyProperty(title)(using tabs),
        (currentTabs: Tabs) ?=>
          (component: AbstractComponent) ?=>
            (cursor: Cursor) ?=> content(using currentTabs)(using component)(using cursor)
      )
    )

  def selectedIndex(using tabs: Tabs): Int =
    tabs.getSelectedIndex

  def selectedIndex_=(value: Int)(using tabs: Tabs): Unit =
    tabs.setSelectedIndex(value)

  def selectedIndex_=(value: ReadOnlyProperty[Int])(using tabs: Tabs): Unit =
    tabs.addDisposable(value.observe(tabs.setSelectedIndex))

  def renderMode(using tabs: Tabs): RenderMode =
    tabs.renderModeProperty.get

  def renderMode_=(value: RenderMode)(using tabs: Tabs): Unit =
    tabs.renderModeProperty.set(value)
}
