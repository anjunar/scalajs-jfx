/**
 * Title block + frame shared by every doc page. See CLAUDE_DEMO_PLAN.md §4.
 *
 * Also where `meta.title`/`meta.summary` reach the document `<head>` -- E-8's
 * own prediction, once `documentHead()` existed: a page recomposes fresh on
 * every navigation to it (the router's `RouteLoad` calls its `doc.ts` again),
 * so a plain `push()` disposed with this article is enough. No `Handle`
 * needed here, unlike a tag that changes without its owner unmounting.
 */
import { article, classes, disposeWith, div, documentHead, heading, meta as metaTag, paragraph, text, title as titleTag } from "@anjunar/jfx-core";
import { translated } from "../app/i18n.js";

export interface DocPageMeta {
  readonly title: string;
  readonly summary: string;
}

export function docPage(meta: DocPageMeta, body: () => void): void {
  const localizedTitle = translated(meta.title);
  const localizedSummary = translated(meta.summary);
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
      classes("docs-page__header");
      heading(1, () => text(localizedTitle));
      paragraph(() => {
        classes("docs-page__summary");
        text(localizedSummary);
      });
    });
    div(() => {
      classes("docs-page__body");
      body();
    });
  });
}
