/**
 * A note / pitfall / known-library-issue box. Used to surface project
 * knowledge inline instead of hiding it (see CLAUDE_DEMO_PLAN.md E-2 on
 * `/core/async`'s and `/core/todos`'s carried-over comments, and E-6 on
 * `/controls/remote`'s SSR-pager caveat).
 */
import { classes, div, text } from "@anjunar/jfx-core";
import { translated } from "../app/i18n.js";

export type CalloutKind = "note" | "pitfall" | "library-bug";

const LABEL: Record<CalloutKind, string> = {
  note: "Note",
  pitfall: "Pitfall",
  "library-bug": "Known library issue",
};

export function callout(kind: CalloutKind, body: () => void): void {
  div(() => {
    classes("docs-callout", `docs-callout--${kind}`);
    div(() => {
      classes("docs-callout__label");
      text(translated(LABEL[kind]));
    });
    div(() => {
      classes("docs-callout__body");
      body();
    });
  });
}
