import { classes, div, forEach, text } from "@anjunar/jfx-core";
import { fetchInto, listProperty } from "@anjunar/jfx-core";

export interface Book {
  readonly title: string;
  readonly author: string;
}

export function loadBooks(): Promise<readonly Book[]> {
  return new Promise((resolve) =>
    setTimeout(
      () =>
        resolve([
          { title: "Structure and Interpretation", author: "Abelson" },
          { title: "A Discipline of Programming", author: "Dijkstra" },
        ]),
      20
    )
  );
}

export function coreAsyncPage(): void {
  const books = listProperty<Book>([]);

  div(() => {
    classes("flex", "flex-col", "gap-2");

    // NOT `when(books.map(v => v.length === 0), ...)` next to fetchInto, on
    // purpose: that combination does not hydrate. `renderToString` only ever
    // serializes the *settled* SSR state -- by the time it serializes, this
    // Condition has already toggled true -> false as a side effect of
    // `books.setAll` inside the loader below, so its anchors reach the wire
    // empty. Hydration replays the render from scratch, though, so the
    // client's *first* synchronous pass evaluates the Condition at `true`
    // again and tries to claim a DOM node for the "empty" branch that the
    // server, having already moved past it, never sent -- a hydration fault
    // ("There is no further DOM node"), not a build error, so nothing catches
    // it before a real browser does. This is a real gap in `when()`/`Condition`
    // hydration, not specific to this demo -- see the callout on this page.
    // The branch below reaches the same UI by choosing once, synchronously,
    // at the one point server and client are guaranteed to agree: when the
    // loader itself resolves.
    fetchInto(loadBooks, (loaded) => {
      books.setAll(loaded);

      if (loaded.length === 0) {
        div(() => {
          classes("text-ink-muted", "italic");
          text("Nothing loaded yet.");
        });
        return;
      }

      div(() => {
        classes("flex", "flex-col", "gap-1");
        forEach(books, (book, index) => {
          div(() => {
            text(`${index + 1}. ${book.title} — ${book.author}`);
          });
        });
      });
    });
  });
}
