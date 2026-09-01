package jfx.control.tabs

import jfx.core.component.AbstractCustomComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.statement.Foreach.foreachIndexed

final class MountedTabsContent(tabs: Tabs) extends AbstractCustomComponent {
  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      foreachIndexed(tabs.tabsProperty) { (tab, index) =>
        DslLayer.child(new TabPanel(tabs, tab, index, keepMounted = true)) {}
      }
    }
}

