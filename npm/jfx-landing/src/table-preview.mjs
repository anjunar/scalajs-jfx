import { attr, property, style, text } from "@anjunar/jfx-core";
import { column, remoteSource, tableView } from "@anjunar/jfx-controls";
import { viewport } from "@anjunar/jfx-viewport";

const totalBooks = 1000;
const pageSize = 50;
const titles = ["The Long Route", "Signal Garden", "Atlas Notes", "Quiet Systems", "Northwind"];
const authors = ["Ada Reed", "Mira Chen", "Noah Klein", "Lea Ortiz", "Sam Okafor"];
const books = Array.from({ length: totalBooks }, (_, index) => ({
  title: `${titles[index % titles.length]} ${index + 1}`,
  author: authors[(index * 3) % authors.length],
  year: 1980 + (index % 46),
}));

function slice(query) {
  const ordered = query.sorting.length === 0
    ? books
    : [...books].sort((left, right) => {
        for (const term of query.sorting) {
          const leftValue = left[term.field];
          const rightValue = right[term.field];
          const result = leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0;
          if (result !== 0) return term.ascending ? result : -result;
        }
        return 0;
      });
  return ordered.slice(query.offset, query.offset + query.limit);
}

function tableBody() {
  const showAuthors = property(true);
  const initialQuery = { offset: 0, limit: pageSize, sorting: [] };
  const source = remoteSource({
    load: query => Promise.resolve({
      items: slice(query),
      offset: query.offset,
      totalCount: totalBooks,
      hasMore: query.offset + query.limit < totalBooks,
    }),
    initialQuery,
    initial: slice(initialQuery),
    totalCount: totalBooks,
    rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    sortQuery: (query, sorting) => ({ ...query, offset: 0, sorting }),
  });

  tableView(source, [
    column("Title", book => text(book.title), {
      prefWidth: 280, minWidth: 140, maxWidth: 900, sortable: true, sortKey: "title",
    }),
    column("Author", book => text(book.author), {
      prefWidth: 190, minWidth: 100, maxWidth: 600, sortable: true, sortKey: "author",
      visible: showAuthors, onVisibilityChange: value => showAuthors.set(value),
    }),
    column("Year", book => text(String(book.year)), {
      prefWidth: 90, minWidth: 70, maxWidth: 300, sortable: true, sortKey: "year",
    }),
  ], {
    rowHeight: 40,
    showFooter: false,
    tableMenuButtonVisible: true,
    columnMenuText: "Columns",
    columnResizePolicy: "flex-last-column",
    selectionMode: "multiple",
    header: () => text("Remote catalogue · visible rows load on demand"),
    placeholder: () => text("No books found."),
    row: row => {
      const book = row.item.get;
      if (book !== null) attr("title", `${book.title} · ${book.author} · ${book.year}`);
      style("font-weight", row.selected.map(selected => selected ? "600" : "400"));
      row.renderCells();
    },
  });
}

export function projectTable() {
  viewport(tableBody);
}
