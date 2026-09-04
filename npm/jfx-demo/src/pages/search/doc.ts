/** Keep title/summary in sync with the "/search" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { catalog } from "../../app/catalog.js";
import { searchPage } from "./page.js";
import type { SearchEntry } from "./page.js";

export function searchDoc(): void {
  const entries: readonly SearchEntry[] = catalog.map((entry) => ({
    path: entry.path,
    title: entry.title,
    summary: entry.summary,
    keywords: entry.keywords,
  }));

  docPage({ title: "Search", summary: "Find any example by title, summary or keyword." }, () => {
    searchPage(entries);
  });
}
