import { button, classes, div, onClick, property, text, when } from "@anjunar/jfx-core";
import { notify, overlay } from "@anjunar/jfx-viewport";
import { translated } from "../../app/i18n.js";

export function viewportOverlayPage(): void {
  const menuOpen = property(false);

  div(() => {
    classes("relative", "inline-block");
    button(translated("Menu"), {}, () => {
      classes("px-3", "py-1.5");
      onClick(() => menuOpen.set(!menuOpen.get));
    });
    when(menuOpen, () => {
      overlay({ widthPx: 200 }, () => {
        div(() => {
          classes("px-3", "py-2", "cursor-pointer");
          onClick(() => {
            notify(translated("Menu item chosen.").get);
            menuOpen.set(false);
          });
          text(translated("Choose me"));
        });
      });
    });
  });
}
