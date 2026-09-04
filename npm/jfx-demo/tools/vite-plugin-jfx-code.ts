/**
 * Answers imports of the form `./page.ts?jfx-code` (the whole file) or
 * `./page.ts?jfx-code=composer` (the region marked `// #region composer` /
 * `// #endregion` inside it) with a module whose default export is a
 * `CodeSnippet` -- tokens, not HTML. See CLAUDE_DEMO_PLAN.md E-3 for why:
 * the code shown on a doc page is the code that runs it, highlighted once at
 * build time so server and client render the identical tree.
 *
 * Tokenizing with `typescript`'s own scanner means no highlighting library
 * ships in the bundle -- `typescript` is already a devDependency for
 * `tsc` itself.
 */
import { readFileSync } from "node:fs";
import { basename } from "node:path";
import * as ts from "typescript";
import type { Plugin } from "vite";
import type { CodeSnippet, CodeToken, TokenKind } from "../src/docs/code-block.js";

const QUERY_PREFIX = "?jfx-code";

export function jfxCode(): Plugin {
  return {
    name: "jfx-code",

    async resolveId(source, importer, options) {
      const marker = source.indexOf(QUERY_PREFIX);
      if (marker === -1) return null;

      const filePart = source.slice(0, marker);
      const query = source.slice(marker + QUERY_PREFIX.length);
      const resolved = await this.resolve(filePart, importer, { ...options, skipSelf: true });
      if (!resolved) return null;

      return `${resolved.id}${QUERY_PREFIX}${query}`;
    },

    load(id) {
      const marker = id.indexOf(QUERY_PREFIX);
      if (marker === -1) return null;

      const filePath = id.slice(0, marker);
      const rest = id.slice(marker + QUERY_PREFIX.length);
      const region = rest.startsWith("=") ? rest.slice(1) : null;

      this.addWatchFile(filePath);
      const source = readFileSync(filePath, "utf8");
      const body = extractRegion(source, region);
      const snippet: CodeSnippet = {
        file: basename(filePath),
        region,
        lines: tokenizeLines(body),
      };

      return `export default ${JSON.stringify(snippet)};\n`;
    },
  };
}

/* --------------------------------------------------------------- regions */

const REGION_START = /^\s*\/\/\s*#region\s+(\S+)\s*$/;
const REGION_END = /^\s*\/\/\s*#endregion\b/;

/**
 * Extracts `region` from `source`, or the whole file with `region: null`.
 * Marker lines never appear in the result either way. A region's shared
 * indentation is stripped; the whole file keeps its own.
 */
function extractRegion(source: string, region: string | null): string {
  const lines = source.replace(/\r\n/g, "\n").split("\n");

  if (region === null) {
    return lines.filter((line) => !REGION_START.test(line) && !REGION_END.test(line)).join("\n");
  }

  const startIndex = lines.findIndex((line) => {
    const match = line.match(REGION_START);
    return match !== null && match[1] === region;
  });
  if (startIndex === -1) {
    throw new Error(`jfx-code: region "${region}" not found`);
  }

  let depth = 1;
  let endIndex = -1;
  for (let i = startIndex + 1; i < lines.length; i++) {
    if (REGION_START.test(lines[i])) depth += 1;
    else if (REGION_END.test(lines[i])) {
      depth -= 1;
      if (depth === 0) {
        endIndex = i;
        break;
      }
    }
  }
  if (endIndex === -1) {
    throw new Error(`jfx-code: region "${region}" has no matching #endregion`);
  }

  const body = lines
    .slice(startIndex + 1, endIndex)
    .filter((line) => !REGION_START.test(line) && !REGION_END.test(line));

  return dedent(body).join("\n");
}

function dedent(lines: readonly string[]): readonly string[] {
  const indents = lines
    .filter((line) => line.trim().length > 0)
    .map((line) => line.match(/^[ \t]*/)?.[0].length ?? 0);
  const common = indents.length === 0 ? 0 : Math.min(...indents);
  if (common === 0) return lines;
  return lines.map((line) => (line.length >= common ? line.slice(common) : line));
}

/* ------------------------------------------------------------ tokenizing */

/**
 * Kind mapping, per CLAUDE_DEMO_PLAN.md E-3: keywords -> kw, every string
 * and template part -> str, numbers -> num, comments -> com, identifiers ->
 * id (capitalized -> typ), everything else -> pun.
 */
function classify(kind: ts.SyntaxKind, text: string): TokenKind {
  if (kind >= ts.SyntaxKind.FirstKeyword && kind <= ts.SyntaxKind.LastKeyword) return "kw";
  if (
    kind === ts.SyntaxKind.StringLiteral ||
    kind === ts.SyntaxKind.NoSubstitutionTemplateLiteral ||
    kind === ts.SyntaxKind.TemplateHead ||
    kind === ts.SyntaxKind.TemplateMiddle ||
    kind === ts.SyntaxKind.TemplateTail
  ) {
    return "str";
  }
  if (kind === ts.SyntaxKind.NumericLiteral) return "num";
  if (kind === ts.SyntaxKind.SingleLineCommentTrivia || kind === ts.SyntaxKind.MultiLineCommentTrivia) return "com";
  if (kind === ts.SyntaxKind.Identifier) return /^[A-Z]/.test(text) ? "typ" : "id";
  return "pun";
}

/**
 * Scans `source` into one `CodeToken[]` per line. `skipTrivia: false` is
 * what surfaces whitespace and newlines as tokens in the first place --
 * `NewLineTrivia` starts a new line, everything else is appended to the
 * current one, merged with the previous token when the kind matches (the
 * size rule from E-3).
 *
 * Template substitutions need `reScanTemplateToken`: a bare `scan()` after a
 * `TemplateHead` has no way to know a following `}` closes back into
 * template text rather than being an ordinary brace.
 */
function tokenizeLines(source: string): readonly (readonly CodeToken[])[] {
  const scanner = ts.createScanner(ts.ScriptTarget.ES2022, false, ts.LanguageVariant.Standard, source);
  const lines: CodeToken[][] = [[]];
  let templateDepth = 0;

  function appendToLine(kind: TokenKind, text: string): void {
    if (text === "") return;
    const line = lines[lines.length - 1];
    const last = line[line.length - 1];
    if (last !== undefined && last.k === kind) {
      line[line.length - 1] = { k: kind, s: last.s + text };
    } else {
      line.push({ k: kind, s: text });
    }
  }

  function appendFragment(kind: TokenKind, text: string): void {
    const parts = text.split("\n");
    parts.forEach((part, index) => {
      if (index > 0) lines.push([]);
      appendToLine(kind, part);
    });
  }

  let kind: ts.SyntaxKind = scanner.scan();
  while (kind !== ts.SyntaxKind.EndOfFileToken) {
    if (kind === ts.SyntaxKind.CloseBraceToken && templateDepth > 0) {
      kind = scanner.reScanTemplateToken(false);
    }

    const text = scanner.getTokenText();
    if (kind === ts.SyntaxKind.TemplateHead) templateDepth += 1;
    else if (kind === ts.SyntaxKind.TemplateTail) templateDepth -= 1;

    if (kind === ts.SyntaxKind.NewLineTrivia) {
      lines.push([]);
    } else {
      appendFragment(classify(kind, text), text);
    }

    kind = scanner.scan();
  }

  return lines;
}
