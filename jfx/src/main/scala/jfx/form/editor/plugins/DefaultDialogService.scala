package jfx.form.editor.plugins

import lexical.DialogService
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

final class DefaultDialogService extends DialogService:

  override def show(title: String, contentProvider: () => HTMLElement, onConfirm: HTMLElement => Unit): Unit =
    val backdrop = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    backdrop.className = "jfx-dialog-backdrop"

    val modal = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    modal.className = "jfx-dialog"

    val titleEl = dom.document.createElement("h3").asInstanceOf[dom.HTMLElement]
    titleEl.className = "jfx-dialog__title"
    titleEl.textContent = title
    modal.appendChild(titleEl)

    val content = contentProvider()
    content.asInstanceOf[dom.HTMLElement].classList.add("jfx-dialog__content")
    modal.appendChild(content)

    val actions = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    actions.className = "jfx-dialog__actions"

    def close(): Unit =
      if (dom.document.body.contains(backdrop)) {
        dom.document.body.removeChild(backdrop)
      }

    val cancelBtn = dom.document.createElement("button").asInstanceOf[dom.HTMLButtonElement]
    cancelBtn.className = "jfx-dialog__button jfx-dialog__button--secondary"
    cancelBtn.textContent = "Cancel"
    cancelBtn.onclick = (_: dom.MouseEvent) => close()

    val confirmBtn = dom.document.createElement("button").asInstanceOf[dom.HTMLButtonElement]
    confirmBtn.className = "jfx-dialog__button jfx-dialog__button--primary"
    confirmBtn.textContent = "Confirm"
    confirmBtn.onclick = (_: dom.MouseEvent) =>
      onConfirm(content)
      close()

    actions.appendChild(cancelBtn)
    actions.appendChild(confirmBtn)
    modal.appendChild(actions)
    backdrop.appendChild(modal)
    dom.document.body.appendChild(backdrop)

    backdrop.onclick = (e: dom.MouseEvent) =>
      if (e.target == backdrop) close()
