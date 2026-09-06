/**
 * Title block + frame shared by every doc page. See CLAUDE_DEMO_PLAN.md §4.
 *
 * Also where `meta.title`/`meta.summary` reach the document `<head>` -- E-8's
 * own prediction, once `documentHead()` existed: a page recomposes fresh on
 * every navigation to it (the router's `RouteLoad` calls its `doc.ts` again),
 * so a plain `push()` disposed with this article is enough. No `Handle`
 * needed here, unlike a tag that changes without its owner unmounting.
 */
import { anchor, article, attr, classes, disposeWith, div, documentHead, heading, meta as metaTag, paragraph, span, text, title as titleTag } from "@anjunar/jfx-core";
import { translated } from "../app/i18n.js";
import { packageName, presentationForTitle } from "../app/presentation.js";

export interface DocPageMeta {
  readonly title: string;
  readonly summary: string;
}

export function docPage(meta: DocPageMeta, body: () => void): void {
  const localizedTitle = translated(meta.title);
  const localizedSummary = translated(meta.summary);
  const presentation = presentationForTitle(meta.title);
  article(() => {
    classes("docs-page");

    const head = documentHead();
    if (head !== null) {
      const pageHead = head.handle();
      const updateHead = () =>
        pageHead.set(
          titleTag(`${localizedTitle.get} · @anjunar/jfx demo`),
          metaTag("description", localizedSummary.get)
        );
      updateHead();
      disposeWith(pageHead);
      disposeWith(localizedTitle.observeWithoutInitial(updateHead));
      disposeWith(localizedSummary.observeWithoutInitial(updateHead));
    }

    div(() => {
      classes("docs-page__header", "showcase-page__header");
      div(() => {
        classes("showcase-page__header-topline");
        div(() => {
          classes("showcase-page__eyebrow");
          text("scalajs-jfx");
        });
        div(() => {
          classes("showcase-page__api-switch");
          if (presentation.scalaPath !== undefined) {
            anchor(() => {
              attr("href", `../scala${presentation.scalaPath}`);
              text("Scala");
            });
          }
          span(() => {
            classes("is-active");
            text("TypeScript");
          });
        });
      });
      heading(1, () => {
        classes("showcase-page__title");
        text(localizedTitle);
      });
      paragraph(() => {
        classes("docs-page__summary", "showcase-page__subtitle");
        text(localizedSummary);
      });
      if (presentation.pkg !== null) {
        const npmPackage = packageName(presentation.pkg);
        div(() => {
          classes("showcase-page__metadata");
          metadata("Package", npmPackage);
          metadata("Import", `import { ${presentation.symbols} } from \"${npmPackage}\";`, true);
        });
      }
    });
    div(() => {
      classes("docs-page__body", "showcase-page__content");
      if (presentation.pkg !== null) runtimeInsights();
      body();
    });
  });
}

function runtimeInsights(): void {
  div(() => {
    classes("showcase-insight-grid");
    insight(0, "Runtime", "Shared engine", "The TypeScript facade delegates to the installed Scala.js JFX runtime.");
    insight(1, "SSR", "Server first", "The page and its live component render into the initial server HTML.");
    insight(2, "Hydration", "Same structure", "The client claims the deterministic tree and continues with the same state model.");
  });
}

function insight(index: number, label: string, title: string, body: string): void {
  div(() => {
    classes("showcase-insight", `showcase-insight--${index}`);
    div(() => { classes("showcase-insight__label"); text(translated(label)); });
    div(() => { classes("showcase-insight__title"); text(translated(title)); });
    div(() => { classes("showcase-insight__body"); text(translated(body)); });
  });
}

function metadata(label: string, value: string, code = false): void {
  div(() => {
    classes("showcase-page__metadata-item");
    div(() => {
      classes("showcase-page__metadata-label");
      text(translated(label));
    });
    div(() => {
      classes(
        "showcase-page__metadata-value",
        ...(code ? ["showcase-page__metadata-value--code"] : [])
      );
      text(value);
    });
  });
}
