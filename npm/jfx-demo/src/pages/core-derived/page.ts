import { button, classes, disposeWith, div, i18n, locale, named, onClick, property, t, text } from "@anjunar/jfx-core";
import { translated } from "../../app/i18n.js";

export function coreDerivedPage(): void {
  const celsius = property(20);
  const fahrenheit = celsius.map((value) => Math.round((value * 9) / 5 + 32));

  const changeCount = property(0);
  const activeLocale = locale();
  const changeLabel = property("");
  const updateChangeLabel = (): void => {
    const count = changeCount.get;
    const message =
      count === 1
        ? i18n`observeWithoutInitial fired ${named("count", count)} time`
        : i18n`observeWithoutInitial fired ${named("count", count)} times`;
    changeLabel.set(t(message).get);
  };
  updateChangeLabel();

  div(() => {
    // The subscription belongs to the page root. Standalone Node runners call
    // this page body directly, without the documentation wrapper that would
    // otherwise provide the component scope.
    disposeWith(celsius.observeWithoutInitial(() => changeCount.set(changeCount.get + 1)));
    disposeWith(changeCount.observeWithoutInitial(updateChangeLabel));
    disposeWith(activeLocale.observeWithoutInitial(updateChangeLabel));
    classes("flex", "flex-col", "gap-3");

    div(() => {
      // Three separate wrapper elements, not three text() calls in a row:
      // adjacent text nodes merge into one when the HTML serializes, but
      // hydration claims exactly one DOM node per text() call -- see
      // src/docs/code-block.ts's note on the same pitfall.
      classes("flex", "gap-1", "text-lg", "font-semibold");
      div(() => text(celsius.map((value) => `${value}°C`)));
      div(() => text(translated("=")));
      div(() => text(fahrenheit.map((value) => `${value}°F`)));
    });

    div(() => {
      classes("flex", "gap-2");
      button(translated("+1°C"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => celsius.set(celsius.get + 1));
      });
      button(translated("-1°C"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => celsius.set(celsius.get - 1));
      });
    });

    div(() => {
      classes("text-ink-soft", "text-sm");
      text(changeLabel);
    });
  });
}
