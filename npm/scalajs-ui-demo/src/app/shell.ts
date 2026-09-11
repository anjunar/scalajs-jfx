/** Semantic shell shared with the Scala demo; designs own its visual layout. */
import { anchor, attr, button, classes, div, element, isBrowser, locale, onClick, span, text } from "@anjunar/scalajs-ui-core";
import { router, routerLink, type RouteDefinition, type RouterConfig } from "@anjunar/scalajs-ui-router";
import { viewport } from "@anjunar/scalajs-ui-viewport";
import { catalog } from "./catalog.js";
import { showcaseSections } from "./presentation.js";
import { preferenceControls } from "./theme.js";
import { switchLocale, translated } from "./i18n.js";
const header = element("header"), nav = element("nav"), details = element("details"), summary = element("summary"), main = element("main"), footer = element("footer");

export function appShell(routes: readonly RouteDefinition[], config: RouterConfig = {}): void {
  const activeLocale = locale();
  const url = isBrowser() ? window.location.href : config.url ?? "/";
  router(routes, config, outlet => {
    div(() => {
      classes("app-shell");
      header(() => {
        classes("site-header");
        routerLink("/", "", {}, () => { classes("brand"); text("Scala JS UI 1.0 API"); });
        div(() => {
          classes("app-toolbar__api-switch");
          externalLink("Scala", "../scala/");
          span(() => { classes("is-active"); text("TypeScript"); });
        });
        button(activeLocale.map(code => code === "de" ? "EN" : "DE"), {}, () => {
          classes("locale-choice");
          onClick(() => switchLocale(activeLocale.get === "de" ? "en" : "de"));
        });
        routerLink("/search", "", {}, () => text(translated("Search")));
        preferenceControls(url);
      });
      div(() => {
        classes("shell");
        nav(() => {
          classes("rail");
          attr("aria-label", translated("Components"));
          details(() => {
            attr("open", "");
            summary(() => text(translated("Components")));
            div(() => {
              classes("app-sidebar__nav");
              for (const section of showcaseSections) {
                const entries = catalog.filter(entry => entry.section === section.id);
                if (!entries.length) continue;
                div(() => {
                  classes("app-nav-group");
                  div(() => { classes("app-sidebar__section-title"); text(translated(section.label)); });
                  for (const entry of entries) routerLink(entry.path, "", { activeClass: "active" }, () => {
                    classes("app-nav-link"); text(translated(entry.title));
                  });
                });
              }
            });
          });
        });
        main(() => {
          classes("app-main");
          attr("id", "main-content");
          viewport(() => { classes("app-content-viewport"); outlet(); });
        });
      });
      footer(() => {
        classes("app-footer");
        text(translated("Scala JS UI 1.0 · Scala.js and TypeScript · one runtime."));
        externalLink("GitHub", "https://github.com/anjunar/scalajs-ui");
        text("v3.0.2");
      });
    });
  });
}
function externalLink(label: string, href: string): void {
  anchor(() => {
    attr("href", href);
    if (href.startsWith("https://")) { attr("target", "_blank"); attr("rel", "noopener noreferrer"); }
    text(label);
  });
}
