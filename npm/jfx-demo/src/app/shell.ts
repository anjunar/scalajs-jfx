/**
 * Shared showcase chrome. The ownership hierarchy intentionally mirrors
 * application/src/main/scala-3/app/App.scala: router -> app shell -> drawer ->
 * navigation/content -> viewport -> routed page.
 */
import {
  anchor,
  attr,
  button,
  classes,
  div,
  disposeWith,
  drawer,
  drawerContent,
  drawerNavigation,
  isBrowser,
  locale,
  onClick,
  property,
  span,
  style,
  text,
  type DrawerHandle,
} from "@anjunar/jfx-core";
import {
  router,
  routerLink,
  type RouteDefinition,
  type RouterConfig,
} from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import { catalog } from "./catalog.js";
import { showcaseSections } from "./presentation.js";
import { themeProperty, toggleTheme } from "./theme.js";
import { switchLocale, translated } from "./i18n.js";

export function appShell(
  routes: readonly RouteDefinition[],
  config: RouterConfig = {}
): void {
  const activeLocale = locale();
  const theme = themeProperty();
  const themeLabel = property("");
  const updateThemeLabel = (mode = theme.get): void => {
    themeLabel.set(translated(mode === "dark" ? "Light" : "Dark").get);
  };
  updateThemeLabel();
  disposeWith(theme.observeWithoutInitial(updateThemeLabel));
  disposeWith(activeLocale.observeWithoutInitial(() => updateThemeLabel()));

  router(routes, config, (outlet) => {
    div(() => {
      classes("app-shell");

      drawer({ open: false }, (drawerHandle) => {
        classes("app-shell-drawer");

        drawerNavigation(() => renderNavigation(drawerHandle));

        drawerContent(() => {
          div(() => {
            classes("app-main");
            renderToolbar(drawerHandle, activeLocale, themeLabel);

            viewport(() => {
              style("flex", "1");
              style("overflow", "auto");

              div(() => {
                classes("app-content-viewport");
                outlet();
              });
            });

            div(() => {
              classes("app-footer");
              div(() => {
                classes("app-footer__text");
                text(
                  translated("One runtime. Two APIs. Choose the language, keep the capabilities.")
                );
              });
            });
          });
        });
      });
    });
  });
}

function renderNavigation(drawerHandle: DrawerHandle): void {
  div(() => {
    classes("app-sidebar");

    div(() => {
      classes("app-sidebar__header");
      routerLink("/", "", {}, () => {
        classes("app-sidebar__logo");
        text("JFX API");
      });
    });

    div(() => {
      classes("app-sidebar__nav");
      for (const section of showcaseSections) {
        const entries = catalog.filter((entry) => entry.section === section.id);
        if (entries.length === 0) continue;

        div(() => {
          classes("app-sidebar__section-title");
          text(translated(section.label));
        });

        for (const entry of entries) {
          routerLink(entry.path, "", { activeClass: "active" }, () => {
            classes("app-nav-link");
            onClick(() => {
              if (isBrowser() && window.innerWidth <= 720) drawerHandle.setOpen(false);
            });
            div(() => {
              classes("app-nav-link__label");
              text(translated(entry.title));
            });
            div(() => {
              classes("app-nav-link__sub");
              text(translated(entry.summary));
            });
          });
        }
      }
    });

    div(() => {
      classes("app-sidebar__footer");
      text(translated("JFX 3 · Scala.js and TypeScript · one runtime."));
      div(() => {
        classes("app-sidebar__project-links");
        externalLink("Showcase", "../");
        externalLink("Scala", "../scala/");
        externalLink("Quick Start", "https://github.com/anjunar/scalajs-jfx#quick-start");
      });
    });
  });
}

function renderToolbar(
  drawerHandle: DrawerHandle,
  activeLocale: ReturnType<typeof locale>,
  themeLabel: ReturnType<typeof property<string>>
): void {
  div(() => {
    classes("app-toolbar");
    button("menu", {}, () => {
      classes("app-toolbar__menu-toggle", "material-icons");
      attr("aria-label", translated("Open navigation"));
      onClick(() => drawerHandle.toggle());
    });

    div(() => {
      classes("app-toolbar__title");
      text(translated("TypeScript Showcase"));
    });
    div(() => {
      classes("spacer");
      style("flex", "1");
    });

    div(() => {
      classes("app-toolbar__api-switch");
      externalLink("Scala", "../scala/");
      span(() => {
        classes("is-active");
        text("TypeScript");
      });
    });

    routerLink("/search", "", {}, () => {
      classes("app-toolbar__text-link", "app-toolbar__text-link--optional");
      text(translated("Search"));
    });
    externalLink(
      "GitHub",
      "https://github.com/anjunar/scalajs-jfx",
      "app-toolbar__text-link app-toolbar__text-link--optional"
    );

    div(() => {
      classes("app-toolbar__chooser", "app-toolbar__language");
      button(activeLocale.map((code) => (code === "de" ? "EN" : "DE")), {}, () => {
        classes("app-toolbar__choice");
        onClick(() => switchLocale(activeLocale.get === "de" ? "en" : "de"));
      });
    });
    div(() => {
      classes("app-toolbar__chooser", "app-toolbar__theme");
      button(themeLabel, {}, () => {
        classes("app-toolbar__choice");
        onClick(toggleTheme);
      });
    });
    externalLink(
      "v3.0.1",
      "https://www.npmjs.com/package/@anjunar/jfx-core/v/3.0.1",
      "app-toolbar__version"
    );
  });
}

function externalLink(label: string, href: string, classNames = ""): void {
  anchor(() => {
    if (classNames !== "") classes(...classNames.split(" "));
    attr("href", href);
    if (href.startsWith("https://")) {
      attr("target", "_blank");
      attr("rel", "noopener noreferrer");
    }
    text(label);
  });
}
