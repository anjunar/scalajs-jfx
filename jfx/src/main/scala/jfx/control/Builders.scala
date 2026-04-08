package jfx.control

import jfx.core.component.NodeComponent
import jfx.core.state.ListProperty
import org.scalajs.dom.Node

def virtualList[T](
  items: ListProperty[T],
  estimateHeightPx: Int = 44,
  overscanPx: Int = 240,
  prefetchItems: Int = 80
)(renderer: (T | Null, Int) => NodeComponent[? <: Node] | Null): VirtualListView[T] =
  VirtualListView.virtualList(items, estimateHeightPx, overscanPx, prefetchItems)(renderer)
