/**
 * The chrome around every routed page: a header (brand + theme toggle) and a
 * per-package nav built from the catalog. Rendered by `router(...)` before
 * the matched page, as a sibling to it -- not a wrapper around it, see
 * jfx-router's `router()` doc comment -- so this owns no shared layout the
 * page itself would need to know about.
 *
 * The package-grouped sidebar this leaves room for (CLAUDE_DEMO_PLAN.md E-5)
 * is exactly this: shell logic, not routing logic, until nested parent
 * routes land in the library and `routes.ts` can build a real outlet tree.
 */
import { anchor, attr, button, classes, div, disposeWith, locale, nav, onClick, property, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { catalog, packages } from "./catalog.js";
import { themeProperty, toggleTheme } from "./theme.js";
import { switchLocale, translated } from "./i18n.js";

export function appShell(): void {
  const activeLocale = locale();
  const theme = themeProperty();
  const themeLabel = property("");
  const updateThemeLabel = (mode = theme.get): void => {
    themeLabel.set(translated(mode === "dark" ? "Light mode" : "Dark mode").get);
  };
  updateThemeLabel();
  disposeWith(theme.observeWithoutInitial(updateThemeLabel));
  disposeWith(activeLocale.observeWithoutInitial(() => updateThemeLabel()));
  div(() => {
    classes("app-shell__header");
    routerLink("/", translated("JFX 3 · TypeScript"), {});
    div(() => {
      classes("app-shell__project-links");
      externalLink("Quick Start", "https://github.com/anjunar/scalajs-jfx#quick-start");
      externalLink("Scala API", "../scala/");
      externalLink("Source", "https://github.com/anjunar/scalajs-jfx");
      externalLink("v3.0.0", "https://www.npmjs.com/package/@anjunar/jfx-core/v/3.0.0");
    });
    div(() => {
      classes("app-shell__controls");
      routerLink("/search", translated("Search"));
      button(
        themeLabel,
        {},
        () => {
          classes("app-shell__theme-toggle");
          onClick(toggleTheme);
        }
      );
      button(activeLocale.map((code) => (code === "de" ? "EN" : "DE")), {}, () => {
        classes("app-shell__locale-toggle");
        onClick(() => switchLocale(activeLocale.get === "de" ? "en" : "de"));
      });
    });
  });

  nav(() => {
    classes("app-shell__nav");
    for (const pkg of packages) {
      div(() => {
        classes("app-shell__nav-group");
        div(() => {
          classes("app-shell__nav-group-title");
          text(translated(pkg.name));
        });
        for (const entry of catalog.filter((candidate) => candidate.pkg === pkg.id)) {
          routerLink(entry.path, translated(entry.title), { activeClass: "app-shell__nav-link--active" });
        }
      });
    }
  });
}

function externalLink(label: string, href: string): void {
  anchor(() => {
    attr("href", href);
    if (href.startsWith("https://")) {
      attr("target", "_blank");
      attr("rel", "noopener noreferrer");
    }
    text(label);
  });
}
