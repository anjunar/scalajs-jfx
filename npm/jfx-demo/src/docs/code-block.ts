/**
 * The token types a `?jfx-code` import (tools/vite-plugin-jfx-code.ts)
 * produces, and the renderer that turns them into `pre > code > span`. Kept
 * in this file rather than in the plugin itself so it is importable without
 * going through the plugin's resolution path -- see CLAUDE_DEMO_PLAN.md E-3.
 */
import { button, classes, code, div, onClick, pre, span, text, when } from "@anjunar/jfx-core";
import { hydratedProperty } from "../app/hydrated.js";
import { translated } from "../app/i18n.js";

export type TokenKind = "kw" | "str" | "num" | "com" | "id" | "typ" | "pun";

export interface CodeToken {
  readonly k: TokenKind;
  readonly s: string;
}

export interface CodeSnippet {
  readonly file: string;
  readonly region: string | null;
  readonly lines: readonly (readonly CodeToken[])[];
}

/** `pun` has no entry on purpose -- see the size rule in codeBlock() below. */
const TOKEN_CLASS: Partial<Record<TokenKind, string>> = {
  kw: "tok-kw",
  str: "tok-str",
  num: "tok-num",
  com: "tok-com",
  id: "tok-id",
  typ: "tok-typ",
};

function isWhitespaceOnly(value: string): boolean {
  return value.trim().length === 0;
}

/**
 * Renders a `CodeSnippet` server- and client-side alike -- the tokens were
 * already computed at build time (E-3), so there is nothing left to
 * highlight at render time, only to lay out.
 *
 * Two rules keep the SSR document small: a `pun` token or a run of pure
 * whitespace is appended to the line as text instead of wrapped in a `span`
 * (most of a typical file is exactly that), and the plugin already merged
 * adjacent same-kind tokens before this ever runs.
 *
 * Adjacent plain-text runs (across token *and* line boundaries -- a line
 * almost always ends with one and the next one almost always starts with
 * leading-indentation whitespace) are buffered into a single `text()` call
 * rather than one call per run. Serialized HTML merges neighbouring text
 * nodes into one regardless of how many `text()` calls produced them, but
 * hydration claims exactly one DOM node per call -- two `text()` calls in a
 * row would claim only one real node between them and then read the whole
 * tree as misaligned from that point on ("DOM node type does not match").
 */
export function codeBlock(snippet: CodeSnippet): void {
  div(() => {
    classes("docs-code-block");

    pre(() => {
      classes("docs-code");
      code(() => {
        let plain = "";
        const flush = (): void => {
          if (plain !== "") {
            text(plain);
            plain = "";
          }
        };

        snippet.lines.forEach((line, lineIndex) => {
          for (const token of line) {
            const cssClass = TOKEN_CLASS[token.k];
            if (cssClass === undefined || isWhitespaceOnly(token.s)) {
              plain += token.s;
            } else {
              flush();
              span(() => {
                classes(cssClass);
                text(token.s);
              });
            }
          }
          if (lineIndex < snippet.lines.length - 1) plain += "\n";
        });

        flush();
      });
    });

    // Mounted only once hydration has settled (hydratedProperty(), set in
    // entry-client.ts), so its absence without JavaScript is not a loading
    // state that never resolves -- it simply never appears, per E-6.
    when(hydratedProperty(), () => {
      button(translated("Copy"), {}, () => {
        classes("docs-code-block__copy");
        onClick(() => {
          void navigator.clipboard?.writeText(plainText(snippet));
        });
      });
    });
  });
}

function plainText(snippet: CodeSnippet): string {
  return snippet.lines.map((line) => line.map((token) => token.s).join("")).join("\n");
}
