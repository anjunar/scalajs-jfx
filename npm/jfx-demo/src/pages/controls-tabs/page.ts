import { classes, div, text } from "@anjunar/jfx-core";
import { tab, tabs } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

export function controlsTabsPage(): void {
  div(() => {
    classes("h-56");
    tabs([
      tab(translated("Overview"), () => {
        div(() => {
          classes("p-4");
          text(translated("The active-only render mode (the default) mounts only this panel and disposes it when another tab becomes active."));
        });
      }),
      tab(translated("Keyboard"), () => {
        div(() => {
          classes("p-4");
          text(translated("Arrow keys move focus between tabs; Home/End jump to the first/last one."));
        });
      }),
      tab(translated("SSR"), () => {
        div(() => {
          classes("p-4");
          text(translated("The selected tab's panel is what the server renders -- view source to see it in the initial HTML."));
        });
      }),
    ]);
  });
}
