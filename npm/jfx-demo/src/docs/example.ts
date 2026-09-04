/**
 * One runnable example on a doc page: the live component, then the exact
 * source that produced it (a `?jfx-code` snippet, see code-block.ts), then
 * an optional note. See CLAUDE_DEMO_PLAN.md §4/E-3.
 */
import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { codeBlock, type CodeSnippet } from "./code-block.js";

export interface ExampleOptions {
  readonly title?: string;
  readonly code: CodeSnippet;
  readonly note?: string;
}

export function example(options: ExampleOptions, body: () => void): void {
  div(() => {
    classes("docs-example");

    if (options.title !== undefined) {
      heading(3, () => text(options.title as string));
    }

    div(() => {
      classes("docs-example__live");
      body();
    });

    codeBlock(options.code);

    if (options.note !== undefined) {
      paragraph(() => {
        classes("docs-example__note");
        text(options.note as string);
      });
    }
  });
}
