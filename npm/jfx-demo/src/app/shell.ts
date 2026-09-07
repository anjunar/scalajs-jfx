/** Shared showcase chrome. The DOM class vocabulary intentionally mirrors
 * application/src/main/scala-3/app/App.scala so both API entrances consume the
 * same visual implementation from the Scala showcase CSS. */
import {
  anchor,
  attr,
  button,
  classes,
  classIf,
  div,
  disposeWith,
  locale,
  onClick,
  property,
  span,
  text,
} from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { catalog } from "./catalog.js";
import { showcaseSections } from "./presentation.js";
import { themeProperty, toggleTheme } from "./theme.js";
import { switchLocale, translated } from "./i18n.js";

export function appShell(): void {
  const mobileNavigationOpen = property(false);
  const activeLocale = locale();
  const theme = themeProperty();
  const themeLabel = property("");
  const updateThemeLabel = (mode = theme.get): void => {
    themeLabel.set(translated(mode === "dark" ? "Light" : "Dark").get);
  };
  updateThemeLabel();
  disposeWith(theme.observeWithoutInitial(updateThemeLabel));
  disposeWith(activeLocale.observeWithoutInitial(() => updateThemeLabel()));

  div(() => {
    classes("app-sidebar");
    classIf("is-open", mobileNavigationOpen);

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
            onClick(() => mobileNavigationOpen.set(false));
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

  button("", {}, () => {
    classes("app-sidebar__backdrop");
    classIf("is-open", mobileNavigationOpen);
    attr("aria-label", translated("Close navigation"));
    onClick(() => mobileNavigationOpen.set(false));
  });

  div(() => {
    classes("app-toolbar");
    button("menu", {}, () => {
      classes("app-toolbar__menu-toggle", "material-icons");
      attr("aria-label", translated("Open navigation"));
      onClick(() => mobileNavigationOpen.set(!mobileNavigationOpen.get));
    });

    div(() => {
      classes("app-toolbar__title");
      text(translated("TypeScript Showcase"));
    });
    div(() => classes("spacer"));

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
    externalLink("GitHub", "https://github.com/anjunar/scalajs-jfx", "app-toolbar__text-link app-toolbar__text-link--optional");

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

  div(() => {
    classes("app-footer");
    div(() => {
      classes("app-footer__text");
      text(translated("One runtime. Two APIs. Choose the language, keep the capabilities."));
    });
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
