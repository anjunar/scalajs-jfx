import { button, classes, disposeWith, div, i18n, locale, named, onClick, property, t, text } from "@anjunar/jfx-core";
import { translated } from "../../app/i18n.js";

export function coreStatePage(): void {
  const counter = property(0);
  const activeLocale = locale();
  const status = property("");
  const updateStatus = (): void => status.set(t(i18n`Current value: ${named("value", counter.get)}`).get);
  updateStatus();

  div(() => {
    classes("flex", "flex-col", "gap-3");
    disposeWith(counter.observeWithoutInitial(updateStatus));
    disposeWith(activeLocale.observeWithoutInitial(updateStatus));
    div(() => {
      classes("text-lg", "font-semibold");
      text(status);
    });
    div(() => {
      classes("flex", "gap-2");
      button(translated("Increment"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => counter.set(counter.get + 1));
      });
      button(translated("Reset"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => counter.set(0));
      });
    });
  });
}
