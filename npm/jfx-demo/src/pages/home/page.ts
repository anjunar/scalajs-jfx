/** The tile grid on `/`, also used by the catalog for package navigation. */
import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";

export interface PackageTile {
  readonly id: "core" | "controls" | "forms" | "viewport" | "router";
  readonly name: string;
  readonly blurb: string;
  readonly entryPath: string;
}

export const packageTiles: readonly PackageTile[] = [
  {
    id: "core",
    name: "@anjunar/jfx-core",
    blurb: "The ambient-scope DSL: reactive state, control flow, elements, lifecycle.",
    entryPath: "/core/state",
  },
  {
    id: "controls",
    name: "@anjunar/jfx-controls",
    blurb: "Tabs, tables, carousels, a virtualized grid and list, remote data sources.",
    entryPath: "/controls/tabs",
  },
  {
    id: "forms",
    name: "@anjunar/jfx-forms",
    blurb: "Validated forms, sub-forms, repeating fields, a combo box, an image cropper.",
    entryPath: "/forms/basics",
  },
  {
    id: "viewport",
    name: "@anjunar/jfx-viewport",
    blurb: "Notifications, floating windows and overlays, mounted above the routed page.",
    entryPath: "/viewport/notification",
  },
  {
    id: "router",
    name: "@anjunar/jfx-router",
    blurb: "Client-side routing: links, a nested outlet, params, constraints, async loaders.",
    entryPath: "/router/links",
  },
];

export function homePage(): void {
  div(() => {
    classes("home-page");
    heading(1, () => text("@anjunar/jfx"));
    paragraph(() => {
      classes("home-page__intro");
      text("A TypeScript facade over JFX3 -- one route per capability, the running component next to the source that produced it.");
    });
    div(() => {
      classes("home-page__grid");
      for (const pkg of packageTiles) {
        div(() => {
          classes("home-page__tile");
          heading(2, () => text(pkg.name));
          paragraph(() => text(pkg.blurb));
          routerLink(pkg.entryPath, "Explore →");
        });
      }
    });
  });
}
