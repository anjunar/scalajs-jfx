/** The tile grid on `/`, also used by the catalog for package navigation. */
import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";
import { example } from "../../docs/example.js";
import { homeCounter } from "./counter.js";
import counterSnippet from "./counter.ts?jfx-code";

export interface PackageTile {
  readonly id: "core" | "controls" | "forms" | "editor" | "viewport" | "router" | "json";
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
    id: "editor",
    name: "@anjunar/jfx-editor",
    blurb: "A Lexical-backed rich-text field, bound by name like input.",
    entryPath: "/editor/basics",
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
  {
    id: "json",
    name: "@anjunar/jfx-json",
    blurb: "Schema-driven JSON mapping for TypeScript models, including IDs and nested state.",
    entryPath: "/json/mapper",
  },
];

export function homePage(): void {
  div(() => {
    classes("home-page", "clarity-page", "clarity-page--home");

    example({ title: "Start with working code", code: counterSnippet }, () => {
      homeCounter();
    });

    div(() => {
      classes("showcase-metric-strip");
      metric("Typed API", "Typed functions expose the same component model.");
      metric("Same runtime", "The bridge installs the Scala.js runtime once.");
      metric("SSR + hydration", "One deterministic tree runs on server and client.");
    });

    sectionHeading(
      "One component model",
      "Server HTML and browser interaction share one tree",
      "Properties, components and lifecycle ownership stay the same across SSR and hydration. TypeScript calls the Scala.js runtime through the typed bridge."
    );

    sectionHeading(
      "Packages",
      "Packages support the product structure",
      "Each capability still identifies its npm package and exact import; packages are reference metadata rather than the primary navigation."
    );

    div(() => {
      classes("home-page__package-grid");
      for (const pkg of packageTiles) {
        div(() => {
          classes("home-page__package-tile");
          heading(2, () => text(pkg.name));
          paragraph(() => text(translated(pkg.blurb)));
          routerLink(pkg.entryPath, "", {}, () => {
            classes("calm-action", "calm-action--quiet");
            text(translated("Explore →"));
          });
        });
      }
    });
  });
}

function metric(title: string, body: string): void {
  div(() => {
    classes("showcase-metric");
    div(() => { classes("showcase-metric__value"); text(translated(title)); });
    div(() => { classes("showcase-metric__label"); text(translated(body)); });
  });
}

function sectionHeading(label: string, title: string, copy: string): void {
  div(() => {
    classes("home-section", "home-section--intro");
    div(() => {
      classes("home-section-heading");
      div(() => { classes("home-eyebrow"); text(translated(label)); });
      heading(2, () => { classes("home-section-heading__title"); text(translated(title)); });
      paragraph(() => { classes("home-section-heading__copy"); text(translated(copy)); });
    });
  });
}
