import "@anjunar/scalajs-ui-bridge";
import "@anjunar/scalajs-ui/index.css";
import { mount } from "@anjunar/scalajs-ui-core";
import { button, onClick, property, text, vbox } from "@anjunar/scalajs-ui-core";

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
