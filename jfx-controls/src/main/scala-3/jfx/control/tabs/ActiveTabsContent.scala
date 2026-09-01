package jfx.control.tabs

import jfx.control.tabs.{EmptyTabsContent, Tabs}
import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.core.statement.Foreach.foreachIndexed

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

