package ui.control.tabs

import ui.core.component.AbstractCustomComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor
import ui.core.statement.Foreach.foreachIndexed

final class MountedTabsContent(tabs: Tabs) extends AbstractCustomComponent {
  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      foreachIndexed(tabs.tabsProperty) { (tab, index) =>
        DslLayer.child(new TabPanel(tabs, tab, index, keepMounted = true)) {}
      }
    }
}
