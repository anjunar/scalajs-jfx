import { button, classes, div, onClick, property, text, when } from "@anjunar/jfx-core";
import { notify, overlay } from "@anjunar/jfx-viewport";

export function viewportOverlayPage(): void {
  const menuOpen = property(false);

  div(() => {
    classes("relative", "inline-block");
    button("Menu", {}, () => {
      classes("px-3", "py-1.5");
      onClick(() => menuOpen.set(!menuOpen.get));
    });
    when(menuOpen, () => {
      overlay({ widthPx: 200 }, () => {
        div(() => {
          classes("px-3", "py-2", "cursor-pointer");
          onClick(() => {
            notify("Menu item chosen.");
            menuOpen.set(false);
          });
          text("Choose me");
        });
      });
    });
  });
}
