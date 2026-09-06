/**
 * One runnable example on a doc page: the live component, then the exact
 * source that produced it (a `?jfx-code` snippet, see code-block.ts), then
 * an optional note. See CLAUDE_DEMO_PLAN.md §4/E-3.
 */
import { classes, div, paragraph, text } from "@anjunar/jfx-core";
import { codeBlock, type CodeSnippet } from "./code-block.js";
import { translated } from "../app/i18n.js";

export interface ExampleOptions {
  readonly title?: string;
  readonly code: CodeSnippet;
  readonly note?: string;
}

export function example(options: ExampleOptions, body: () => void): void {
  div(() => {
    classes("docs-example", "component-showcase");

    div(() => {
      classes("component-showcase__header");
      div(() => {
        classes("component-showcase__title");
        text(translated(options.title ?? "Live demo"));
      });
      div(() => {
        classes("component-showcase__summary");
        text(translated("The running component and its TypeScript source use the shared JFX runtime."));
      });
    });

    div(() => {
      classes("docs-example__live", "component-showcase__render");
      body();
    });

    div(() => {
      classes("api-section");
      div(() => {
        classes("api-section__header");
        div(() => {
          classes("api-section__title");
          text(translated("TypeScript API"));
        });
        div(() => {
          classes("api-section__summary");
          text(translated("Source for the live demo above."));
        });
      });
      codeBlock(options.code);
    });

    if (options.note !== undefined) {
      paragraph(() => {
        classes("docs-example__note");
        text(translated(options.note as string));
      });
    }
  });
}
