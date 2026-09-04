/** Title block + frame shared by every doc page. See CLAUDE_DEMO_PLAN.md §4. */
import { article, classes, div, heading, paragraph, text } from "@anjunar/jfx-core";

export interface DocPageMeta {
  readonly title: string;
  readonly summary: string;
}

export function docPage(meta: DocPageMeta, body: () => void): void {
  article(() => {
    classes("docs-page");
    div(() => {
      classes("docs-page__header");
      heading(1, () => text(meta.title));
      paragraph(() => {
        classes("docs-page__summary");
        text(meta.summary);
      });
    });
    div(() => {
      classes("docs-page__body");
      body();
    });
  });
}
