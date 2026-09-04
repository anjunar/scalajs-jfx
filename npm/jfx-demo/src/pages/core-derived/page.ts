import { button, classes, disposeWith, div, onClick, property, text } from "@anjunar/jfx-core";

export function coreDerivedPage(): void {
  const celsius = property(20);
  const fahrenheit = celsius.map((value) => Math.round((value * 9) / 5 + 32));

  const changeCount = property(0);

  div(() => {
    // The subscription belongs to the page root. Standalone Node runners call
    // this page body directly, without the documentation wrapper that would
    // otherwise provide the component scope.
    disposeWith(celsius.observeWithoutInitial(() => changeCount.set(changeCount.get + 1)));
    classes("flex", "flex-col", "gap-3");

    div(() => {
      // Three separate wrapper elements, not three text() calls in a row:
      // adjacent text nodes merge into one when the HTML serializes, but
      // hydration claims exactly one DOM node per text() call -- see
      // src/docs/code-block.ts's note on the same pitfall.
      classes("flex", "gap-1", "text-lg", "font-semibold");
      div(() => text(celsius.map((value) => `${value}°C`)));
      div(() => text("="));
      div(() => text(fahrenheit.map((value) => `${value}°F`)));
    });

    div(() => {
      classes("flex", "gap-2");
      button("+1°C", {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => celsius.set(celsius.get + 1));
      });
      button("-1°C", {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => celsius.set(celsius.get - 1));
      });
    });

    div(() => {
      classes("text-ink-soft", "text-sm");
      text(changeCount.map((count) => `observeWithoutInitial fired ${count} time${count === 1 ? "" : "s"}`));
    });
  });
}
