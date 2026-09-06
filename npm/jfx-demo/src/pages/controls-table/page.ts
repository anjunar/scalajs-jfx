import { classes, div, style, text } from "@anjunar/jfx-core";
import { column, remoteSource, tableView } from "@anjunar/jfx-controls";
import type { RemotePage, RemoteSource, SortSpec } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

interface Book {
  readonly title: string;
  readonly author: string;
  readonly year: number;
}

interface Query {
  readonly offset: number;
  readonly limit: number;
  readonly sorting: readonly SortSpec[];
}

const TOTAL_BOOKS = 1000;
const PAGE_SIZE = 50;
const titles = ["The Long Route", "Signal Garden", "Atlas Notes", "Quiet Systems", "Northwind"];
const authors = ["Ada Reed", "Mira Chen", "Noah Klein", "Lea Ortiz", "Sam Okafor"];

const books: readonly Book[] = Array.from({ length: TOTAL_BOOKS }, (_, index) => ({
  title: `${titles[index % titles.length]} ${index + 1}`,
  author: authors[(index * 3) % authors.length],
  year: 1980 + (index % 46),
}));

function slice(query: Query): readonly Book[] {
  const term = query.sorting[0];
  const ordered = term === undefined
    ? books
    : [...books].sort((left, right) => {
        const leftValue = left[term.field as keyof Book];
        const rightValue = right[term.field as keyof Book];
        const result = leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0;
        return term.ascending ? result : -result;
      });
  return ordered.slice(query.offset, query.offset + query.limit);
}

function loadPage(query: Query): Promise<RemotePage<Book, Query>> {
  return Promise.resolve({
    items: slice(query),
    offset: query.offset,
    totalCount: TOTAL_BOOKS,
    hasMore: query.offset + query.limit < TOTAL_BOOKS,
  });
}

export function controlsTablePage(): void {
  const initialQuery: Query = { offset: 0, limit: PAGE_SIZE, sorting: [] };
  const source: RemoteSource<Book, Query> = remoteSource({
    load: loadPage,
    initialQuery,
    initial: slice(initialQuery),
    totalCount: TOTAL_BOOKS,
    rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    sortQuery: (query, sorting) => ({ ...query, offset: 0, sorting }),
  });

  div(() => {
    classes("flex", "flex-col", "gap-4");

    div(() => {
      classes("showcase-note");
      div(() => {
        classes("showcase-note__title");
        text(translated("50 initial rows · 1,000 total"));
      });
      div(() => {
        classes("showcase-note__body");
        text(translated("Scroll through remote ranges or sort any column; the table keeps one stable virtual surface."));
      });
    });

    div(() => {
      style("height", "420px");
      tableView(
        source,
        [
          column(translated("Title").get, (book) => text(book.title), { prefWidth: 280, sortable: true, sortKey: "title" }),
          column(translated("Author").get, (book) => text(book.author), { prefWidth: 220, sortable: true, sortKey: "author" }),
          column(translated("Year").get, (book) => text(String(book.year)), { prefWidth: 100, sortable: true, sortKey: "year" }),
        ],
        {
          rowHeight: 40,
          crawlable: true,
          crawlId: "books",
          header: () => text(translated("Remote catalogue · visible rows load on demand")),
          placeholder: () => text(translated("No books found.")),
        }
      );
    });
  });
}
