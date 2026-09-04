import { button, classes, div, onClick, property, text, when } from "@anjunar/jfx-core";
import { floatingWindow, notify } from "@anjunar/jfx-viewport";

export function viewportWindowPage(): void {
  const windowOpen = property(false);

  button("Open window", {}, () => {
    classes("px-3", "py-1.5");
    onClick(() => windowOpen.set(true));
  });

  when(windowOpen, () => {
    floatingWindow(
      { title: "A room for thoughts", widthPx: 400, heightPx: 260, onClose: () => windowOpen.set(false) },
      () => {
        div(() => {
          classes("p-5", "flex", "flex-col", "gap-3");
          text("This content is mounted into the shared viewport layer, not into the route subtree.");
          button("Confirm note", {}, () => {
            classes("px-3", "py-1.5");
            onClick(() => notify("The note in the window was confirmed.", { kind: "success" }));
          });
        });
      }
    );
  });
}
