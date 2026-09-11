import { button, classes, onClick, property, text, vbox } from "@anjunar/scalajs-ui-core";

export function homeCounter(): void {
  const count = property(0);

  vbox(() => {
    classes("home-counter");
    text(count.map((value) => `Count: ${value}`));
    button("Increment", {}, () => {
      onClick(() => count.set(count.get + 1));
    });
  });
}
