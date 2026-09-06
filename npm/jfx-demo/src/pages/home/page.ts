/** The tile grid on `/`, also used by the catalog for package navigation. */
import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

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

    div(() => {
      classes("home-hero");
      div(() => {
        classes("home-hero__content");
        div(() => {
          classes("home-eyebrow");
          text(translated("One runtime. Two APIs."));
        });
        heading(2, () => {
          classes("home-hero__title");
          text(translated("Build with TypeScript. Run on JFX."));
        });
        paragraph(() => {
          classes("home-hero__copy");
          text(translated("The TypeScript API is a language-level entrance to the same reactive, SSR-capable runtime as the Scala.js DSL."));
        });
        div(() => {
          classes("home-hero__actions");
          routerLink("/core/state", "", {}, () => {
            classes("calm-action", "calm-action--primary");
            text(translated("Explore the runtime"));
          });
        });
      });

      div(() => {
        classes("home-hero__metrics");
        metric("01", "Shared runtime", "The bridge installs the Scala.js runtime once.");
        metric("02", "TypeScript API", "Typed functions expose the same component model.");
        metric("03", "SSR + hydration", "One deterministic tree runs on server and client.");
      });
    });

    sectionHeading(
      "Capabilities",
      "Start with the product model",
      "Navigation follows interaction, architecture, runtime, forms, data and editor capabilities. Package ownership stays visible on every page."
    );

    div(() => {
      classes("home-demo-grid");
      capability("Runtime", "Reactive state, control flow and hydration boundaries.", "/core/state");
      capability("Interaction", "Tabs, carousel and extensible DOM composition.", "/controls/tabs");
      capability("Forms", "Typed models, validation and composed fields.", "/forms/basics");
      capability("Data", "Tables, grids, lists and remote ranges.", "/controls/table");
    });

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

function metric(index: string, title: string, body: string): void {
  div(() => {
    classes("home-metric");
    div(() => { classes("home-metric__index"); text(index); });
    div(() => { classes("home-metric__title"); text(translated(title)); });
    div(() => { classes("home-metric__body"); text(translated(body)); });
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

function capability(title: string, body: string, path: string): void {
  div(() => {
    classes("home-demo-card");
    div(() => { classes("home-demo-card__meta"); text(translated("Capability")); });
    div(() => { classes("home-demo-card__title"); text(translated(title)); });
    div(() => { classes("home-demo-card__body"); text(translated(body)); });
    routerLink(path, "", {}, () => {
      classes("calm-action", "calm-action--secondary");
      text(translated("Open showcase"));
    });
  });
}
