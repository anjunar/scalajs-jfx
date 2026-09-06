import { button, classes, div, onClick, property, text } from "@anjunar/jfx-core";
import { tab, tabs } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

function panel(title: string, summary: string): void {
  div(() => {
    classes("docs-card");
    div(() => {
      classes("docs-card__title");
      text(translated(title));
    });
    div(() => {
      classes("docs-card__summary");
      text(translated(summary));
    });
  });
}

function statefulPanel(title: string): void {
  const count = property(0);
  div(() => {
    classes("docs-card");
    div(() => {
      classes("docs-card__title");
      text(translated(title));
    });
    div(() => {
      classes("docs-card__summary");
      text(count.map((value) => `${translated("Local counter").get}: ${value}`));
    });
    div(() => {
      classes("showcase-action-row");
      button(translated("Increment"), {}, () => onClick(() => count.set(count.get + 1)));
    });
  });
}

export function controlsTabsPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-6");

    div(() => {
      div(() => {
        classes("showcase-note__title");
        text(translated("Active-only panels"));
      });
      tabs([
        tab(translated("Overview"), () => panel("One active lifecycle", "Only the selected panel is mounted and disposed on change.")),
        tab(translated("Keyboard"), () => panel("Keyboard navigation", "Arrow keys move focus; Home and End jump to the edges.")),
        tab(translated("SSR"), () => panel("Server output", "The selected panel is present in the initial HTML.")),
      ]);
    });

    div(() => {
      div(() => {
        classes("showcase-note__title");
        text(translated("Keep-mounted panels"));
      });
      tabs(
        [
          tab(translated("Draft"), () => statefulPanel("Draft state survives tab changes")),
          tab(translated("Preview"), () => statefulPanel("Preview state survives tab changes")),
        ],
        { renderMode: "keep-mounted" }
      );
    });
  });
}
