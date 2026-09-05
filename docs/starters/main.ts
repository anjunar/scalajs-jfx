import "@anjunar/scalajs-jfx-bridge";
import "@anjunar/scalajs-jfx/index.css";
import { mount } from "@anjunar/jfx-core";
import { button, onClick, property, text, vbox } from "@anjunar/jfx-core";

function counter() {
  const count = property(0);

  vbox(() => {
    text(count.map(n => "Count: " + n));
    button("Increment", {}, () => {
      onClick(() => count.set(count.get + 1));
    });
  });
}

mount(document.getElementById("root")!, counter);
