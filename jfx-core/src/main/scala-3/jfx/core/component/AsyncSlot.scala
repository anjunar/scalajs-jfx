package jfx.core.component

class AsyncSlot extends AbstractComponent {
  override val tagName: String = "#async"

  override def isVirtual: Boolean =
    true

}
