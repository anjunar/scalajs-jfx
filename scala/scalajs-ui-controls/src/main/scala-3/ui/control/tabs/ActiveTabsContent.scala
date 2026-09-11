package ui.control.tabs

import ui.control.tabs.{EmptyTabsContent, Tabs}
import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.render.Cursor
import ui.core.state.Property
import ui.core.statement.DynamicComponentRenderer.dynamic
import ui.core.statement.Foreach.foreachIndexed

final class ActiveTabsContent(tabs: Tabs) extends AbstractCustomComponent {
  private val activePanelProperty: Property[AbstractComponent] =
    Property(createPanel())

  override def compose(cursor: Cursor): Unit = {
    DslLayer.render(this, cursor) {
      dynamic(activePanelProperty)
    }

    addDisposable(tabs.contentRevisionProperty.observeWithoutInitial { _ =>
      activePanelProperty.setAlways(createPanel())
    })
  }

  private def createPanel(): AbstractComponent =
    tabs.activeTab match {
      case Some((tab, index)) => new TabPanel(tabs, tab, index, keepMounted = false)
      case None               => new EmptyTabsContent
    }
}
