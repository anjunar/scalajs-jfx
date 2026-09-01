package jfx.control.tabs

import jfx.control.Tabs
import jfx.control.Tabs.TabSpec
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classIf
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

private final class TabPanel(
                              tabs: Tabs,
                              tab: TabSpec,
                              index: Int,
                              keepMounted: Boolean
                            ) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-tabs__panel")
      host.setAttribute("role", "tabpanel")

      if (keepMounted) {
        val active = tabs.selectedIndexProperty.map(_ == index)
        classIf("jfx-tabs__panel--active", active)
        addDisposable(active.observe { selected =>
          host.setAttribute("aria-hidden", (!selected).toString)
          host.setStyle("display", if (selected) "" else "none")
        })
      } else {
        addClass("jfx-tabs__panel--active")
        host.setAttribute("aria-hidden", "false")
      }

      tab.render(using tabs)(using this)(using cursor)
    }
}