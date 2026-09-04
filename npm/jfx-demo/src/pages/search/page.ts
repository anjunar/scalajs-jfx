/**
 * Takes the search index as a parameter rather than importing app/catalog.ts
 * directly -- page.ts may only import from @anjunar/jfx-* (CLAUDE_DEMO_PLAN.md
 * E-2). doc.ts builds `SearchEntry[]` from the real catalog and passes it in;
 * this function itself has no idea catalog.ts exists.
 */
import { attr, classes, div, element, forEach, li, on, property, text, ul } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";

export interface SearchEntry {
  readonly path: string;
  readonly title: string;
  readonly summary: string;
  readonly keywords: readonly string[];
}

const searchInput = element("input");

function matches(entry: SearchEntry, needle: string): boolean {
  if (needle === "") return true;
  return (
    entry.title.toLowerCase().includes(needle) ||
    entry.summary.toLowerCase().includes(needle) ||
    entry.keywords.some((keyword) => keyword.toLowerCase().includes(needle))
  );
}

export function searchPage(entries: readonly SearchEntry[]): void {
  const sorted = [...entries].sort((a, b) => a.title.localeCompare(b.title));
  const query = property("");

  // Server and client alike start from query === "", so this shows the full,
  // alphabetically sorted index without any JavaScript -- the "input" event
  // below is what narrows it, and only after hydration can fire at all.
  const results = query.map((value) => {
    const needle = value.trim().toLowerCase();
    return sorted.filter((entry) => matches(entry, needle));
  });

  div(() => {
    classes("flex", "flex-col", "gap-3");

    searchInput(() => {
      classes("px-3", "py-1.5", "border", "border-line", "rounded-control");
      attr("type", "search");
      attr("placeholder", "Filter by title, summary or keyword…");
      on("input", (event) => {
        query.set((event.target as HTMLInputElement | null)?.value ?? "");
      });
    });

    ul(() => {
      classes("flex", "flex-col", "gap-3");
      forEach(results, (entry) => {
        li(() => {
          routerLink(entry.path, entry.title);
          div(() => {
            classes("text-ink-soft", "text-sm");
            text(entry.summary);
          });
        });
      });
    });
  });
}
