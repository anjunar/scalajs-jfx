import { button, onClick, property, text, vbox } from "@anjunar/jfx-core";

export function counter() {
  const count = property(0);

  vbox(() => {
    text(count.map(n => "Count: " + n));
    button("Increment", {}, () => {
      onClick(() => count.set(count.get + 1));
    });
  });
}
