/**
 * The pages themselves, runtime-agnostic: nothing here imports a runtime or
 * calls `installRuntime`. `statePage.ts` renders them against the stub;
 * `bridgeDemo.ts` renders the same two functions, unchanged, against the real
 * Scala.js bridge -- the point being that there is exactly one `statePage`, not
 * one per runtime (JAVASCRIPT_API.md §2: a facade, not a second implementation).
 */
import {
  addClass,
  anchor,
  attr,
  button,
  classes,
  div,
  fetchInto,
  forEach,
  heading,
  listProperty,
  nav,
  onClick,
  property,
  text,
  vbox,
} from "../src/index.js";

/* ---------------------------------------------------------------------- nav */

/**
 * The only thing standing in for a router here: two plain `<a href>` links.
 * `jfx-bridge` doesn't wire up `jfx-router` yet (JAVASCRIPT_API.md §9, step 5),
 * so this is ordinary browser navigation between two SSR'd pages, not a
 * client-side route change -- `jfx-demo/src/entry-server.ts` and
 * `entry-client.ts` pick the page to render by request path, not by a Router
 * component.
 */
function pageNav(current: "state" | "library"): void {
  nav(() => {
    classes("page-nav");
    anchor(() => {
      attr("href", "/");
      if (current === "state") addClass("page-nav__link--active");
      text("Counter");
    });
    anchor(() => {
      attr("href", "/library");
      if (current === "library") addClass("page-nav__link--active");
      text("Library");
    });
  });
}

/* --------------------------------------------------------------- StatePage */

export function statePage(): void {
  const counter = property(0);
  const status = counter.map((value) => `Current value: ${value}`);

  vbox(() => {
    classes("clarity-grid");

    pageNav("state");

    div(() => {
      classes("docs-card");
      div(() => {
        classes("docs-card__title");
        text(status);
      });
      div(() => {
        classes("docs-card__summary");
        text("The visible text is derived directly from a Property<number>.");
      });
    });

    div(() => {
      classes("clarity-action-row");

      button("Increment", {}, () => {
        classes("calm-action", "calm-action--primary");
        onClick(() => counter.set(counter.get + 1));
      });

      button("Reset", {}, () => {
        classes("calm-action", "calm-action--secondary");
        onClick(() => counter.set(0));
      });
    });
  });
}

/* ----------------------------------------------------- control flow + async */

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

export function libraryPage(): void {
  const books = listProperty<Book>([]);

  div(() => {
    classes("library");
    pageNav("library");
    heading(2, () => text("Library"));

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
    // hydration, not specific to this demo -- flagged separately, see the
    // task this session filed for it. The branch below reaches the same UI by
    // choosing once, synchronously, at the one point server and client are
    // guaranteed to agree: when the loader itself resolves.
    fetchInto(loadBooks, (loaded) => {
      books.setAll(loaded);

      if (loaded.length === 0) {
        div(() => {
          classes("library__empty");
          text("Nothing loaded yet.");
        });
        return;
      }

      div(() => {
        classes("library__list");
        forEach(books, (book, index) => {
          div(() => {
            classes("library__row");
            text(`${index + 1}. ${book.title} — ${book.author}`);
          });
        });
      });
    });
  });
}

export function format(html: string): string {
  return html.replace(/></g, ">\n<");
}
