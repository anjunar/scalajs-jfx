import { button, classes, classIf, div, forEach, listProperty, onClick, property, text, when } from "@anjunar/jfx-core";
import { translated } from "../../app/i18n.js";

export function coreControlFlowPage(): void {
  const items = listProperty<string>(["Alpha", "Beta"]);
  const isEmpty = items.map((values) => values.length === 0);
  const highlight = property(false);

  div(() => {
    classes("flex", "flex-col", "gap-3");

    div(() => {
      classes("flex", "gap-2");
      button(translated("Add item"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => items.add(`Item ${items.get.length + 1}`));
      });
      button(translated("Toggle highlight"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => highlight.set(!highlight.get));
      });
    });

    when(isEmpty, () => {
      div(() => {
        classes("text-ink-muted", "italic");
        text(translated("No items -- add one above."));
      });
    });

    div(() => {
      classes("flex", "flex-col", "gap-1");
      forEach(items, (item, index) => {
        div(() => {
          classes("px-2", "py-1", "rounded-control");
          classIf("bg-accent-muted", highlight);
          text(`${index + 1}. ${item}`);
        });
      });
    });
  });
}
