package jfx.core.render

import org.scalajs.dom

/** Textarea owns one RCDATA value, not separately hydrated child components. */
trait TextAreaContent {
  def value: String
  def defaultValue: String
  def setValue(value: String): Unit
  def setDefaultValue(value: String): Unit
  /** Restores the current value, equivalent to setValue(defaultValue). Native form.reset()
    * additionally clears the browser's internal dirty-value flag.
    */
  def reset(): Unit
}

object TextAreaContent {
  def normalize(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')

  private[render] def attach(host: HostElement, initial: String, hydrating: Boolean): TextAreaContent = {
    require(host.tagName.equalsIgnoreCase("textarea"), "Textarea content requires a textarea host.")
    host match {
      case ssr: SsrHostElement =>
        require(ssr.childCount == 0, "Textarea content cannot share a host with child components.")
        HostMutationGuard.checkRemoval(host)
        val content = new SsrContent(ssr, normalize(initial))
        ssr.textAreaContent = Some(content)
        content
      case browser: DomHostElement =>
        val element = DomNodes.raw(browser).asInstanceOf[dom.HTMLTextAreaElement]
        val content = new DomContent(browser, element)
        if (!hydrating) {
          content.setDefaultValue(initial)
          content.setValue(initial)
        }
        content
      case _ => throw new IllegalArgumentException("Unsupported textarea host backend.")
    }
  }

  private final class DomContent(host: HostElement, node: dom.HTMLTextAreaElement) extends TextAreaContent {
    def value: String = node.value
    def defaultValue: String = normalize(node.defaultValue)
    def setValue(value: String): Unit = {
      val next = normalize(value)
      if (node.value != next) {
        HostMutationGuard.checkRemoval(host)
        node.value = next
      }
    }
    def setDefaultValue(value: String): Unit = {
      val next = normalize(value)
      if (defaultValue != next) {
        HostMutationGuard.checkRemoval(host)
        node.defaultValue = next
      }
    }
    def reset(): Unit = setValue(defaultValue)
  }

  private final class SsrContent(host: HostElement, initial: String) extends TextAreaContent {
    private var current = initial
    private var baseline = initial
    private var dirty = false
    def value: String = current
    def defaultValue: String = baseline
    def setValue(value: String): Unit = {
      val next = normalize(value)
      if (current != next) {
        HostMutationGuard.checkRemoval(host)
        current = next
        dirty = true
      }
    }
    def setDefaultValue(value: String): Unit = {
      HostMutationGuard.checkRemoval(host)
      baseline = normalize(value)
      if (!dirty) current = baseline
    }
    def reset(): Unit = setValue(defaultValue)
  }
}
