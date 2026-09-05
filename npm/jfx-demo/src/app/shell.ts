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
import { button, classes, div, disposeWith, locale, nav, onClick, property, text } from "@anjunar/jfx-core";
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
    routerLink("/", translated("@anjunar/jfx"), {});
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
