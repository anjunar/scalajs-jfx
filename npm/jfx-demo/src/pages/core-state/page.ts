import { button, classes, div, onClick, property, text } from "@anjunar/jfx-core";

export function coreStatePage(): void {
  const counter = property(0);
  const status = counter.map((value) => `Current value: ${value}`);

  div(() => {
    classes("flex", "flex-col", "gap-3");
    div(() => {
      classes("text-lg", "font-semibold");
      text(status);
    });
    div(() => {
      classes("flex", "gap-2");
      button("Increment", {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => counter.set(counter.get + 1));
      });
      button("Reset", {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => counter.set(0));
      });
    });
  });
}
