/**
 * The pages themselves, runtime-agnostic: nothing here imports a runtime or
 * calls `installRuntime`. `node-stub.ts` renders `statePage`/`libraryPage`
 * against the stub; `node-bridge.ts` renders the same two functions,
 * unchanged, against the real Scala.js bridge -- the point being that there is
 * exactly one `statePage`, not one per runtime (JAVASCRIPT_API.md §2: a
 * facade, not a second implementation). `todosPage` below is the third page;
 * it is only reachable through `npm run dev` (`/todos`), because what it
 * demonstrates -- typing, clicking, toggling -- only means something with a
 * pointer and a keyboard attached, not printed to a console.
 */
import {
  addClass,
  anchor,
  attr,
  button,
  classes,
  classIf,
  disposeWith,
  div,
  element,
  fetchInto,
  forEach,
  heading,
  listProperty,
  nav,
  on,
  onClick,
  onInput,
  property,
  self,
  text,
  vbox,
  when,
} from "@anjunar/jfx-core";
import type { ComponentHandle, Disposable, Property, UiEvent } from "@anjunar/jfx-core";

/* ---------------------------------------------------------------------- nav */

/**
 * The only thing standing in for a router here: two plain `<a href>` links.
 * `jfx-bridge` doesn't wire up `jfx-router` yet (JAVASCRIPT_API.md §9, step 5),
 * so this is ordinary browser navigation between two SSR'd pages, not a
 * client-side route change -- `jfx-demo/src/entry-server.ts` and
 * `entry-client.ts` pick the page to render by request path, not by a Router
 * component.
 */
function pageNav(current: "state" | "library" | "todos"): void {
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
    anchor(() => {
      attr("href", "/todos");
      if (current === "todos") addClass("page-nav__link--active");
      text("Todos");
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

/* ------------------------------------------------------------------ TodosPage */

/**
 * `input` has no typed wrapper in `dsl.ts` -- there is no `vbox`/`hbox`/`button`
 * for it, the way `jfx-core` deliberately keeps the wrapper set small
 * (JAVASCRIPT_API.md §4). `element("input")` is the same one-line extension
 * point `div`/`span`/`anchor` are themselves built from (`src/dsl.ts`), so
 * reaching for it here is the ordinary way to grow the DSL, not a workaround.
 */
const input = element("input");

/**
 * Each todo carries its own `Property<boolean>`, not a plain `boolean`. That
 * is what lets `classIf` below observe one item's done-state without
 * re-rendering the row it belongs to, and it is why toggling one item never
 * disturbs another's -- `todos.setAll(...)` (in "Clear completed") replaces
 * the *array*, not the objects still in it, so a `Todo` that survives a
 * filter keeps the exact `Property` instance any live subscription is
 * watching.
 */
export interface Todo {
  readonly label: string;
  readonly done: Property<boolean>;
}

function inputValue(event: UiEvent): string {
  // `UiEvent.target` mirrors a DOM event's `.target`, kept as `unknown` at the
  // contract boundary (contract.ts) because the runtime has no reason to know
  // it will be an input element -- that is knowledge only this call site has.
  return (event.target as HTMLInputElement | null)?.value ?? "";
}

export function todosPage(): void {
  const draft = property("");
  const todos = listProperty<Todo>([]);
  const isEmpty = todos.map((items) => items.length === 0);
  const hasTodos = todos.map((items) => items.length > 0);

  // `remaining` cannot be `todos.map(items => items.filter(...).length)`: a
  // `ListProperty` only notifies when the *array itself* changes -- add,
  // insert, removeAt, setAll, clear. Toggling one item's `done` never touches
  // the array, so a `.map()` derived that way would show the count from
  // whenever an item was last added or removed, not the count that matches
  // what's on screen right now (this shipped once, briefly, exactly that
  // broken; caught by clicking the toggle and watching the footer not move).
  //
  // What is actually needed is a value derived from *two* independent
  // sources -- the list, and every item's own `done` -- and the contract has
  // no combinator for that (`ReadOnlyProperty.map` takes one source). So this
  // builds it by hand: resubscribe to every item's `done` whenever the list
  // itself changes, and recompute on either kind of event. `disposeWith` is
  // what makes that safe to do inside a render body -- both the list
  // subscription and the current crop of per-item subscriptions are torn
  // down when this page's `MountedApp` is disposed, the same as any binding
  // `attr`/`classIf` set up.
  const remaining = property(0);
  let itemSubscriptions: Disposable[] = [];

  function recomputeRemaining(items: readonly Todo[]): void {
    remaining.set(items.filter((item) => !item.done.get).length);
  }

  function subscribeToItems(items: readonly Todo[]): void {
    for (const subscription of itemSubscriptions.splice(0)) subscription.dispose();
    itemSubscriptions = items.map((item) =>
      item.done.observeWithoutInitial(() => recomputeRemaining(todos.get))
    );
    recomputeRemaining(items);
  }

  // Captured so `addTodo` can clear the field itself: `domProperty` (used
  // below) only ever writes once, at composition time -- it is not a binding
  // that reasserts itself when `draft` changes later, the way `attr`/`style`
  // are. Clearing the *displayed* value after submit is therefore an
  // imperative follow-up, exactly like a plain DOM script would do it, just
  // routed through the same `ComponentHandle` every other DSL helper uses.
  let draftField: ComponentHandle | null = null;

  function addTodo(): void {
    const label = draft.get.trim();
    if (label === "") return;
    todos.add({ label, done: property(false) });
    draft.set("");
    draftField?.setDomProperty("value", "");
  }

  div(() => {
    classes("todos");
    // Registered here, not above: `disposeWith` needs a component being
    // composed to hang the subscription off of (`currentComponent()` in
    // scope.ts), so it has to run inside a body like this one, the same as
    // every other DSL call that touches the current element.
    disposeWith(todos.observe(subscribeToItems));

    pageNav("todos");
    heading(2, () => text("Todos"));

    div(() => {
      classes("todos__composer");
      input(() => {
        draftField = self();
        attr("type", "text");
        attr("placeholder", "Add a todo…");
        onInput((event) => draft.set(inputValue(event)));
        // `on`, not a typed helper: there is no `onKeyDown` in dsl.ts, the same
        // way there is no `input`. `onClick`/`onInput` are themselves nothing
        // more than `on("click", ...)`/`on("input", ...)` with a name attached
        // (dsl.ts) -- this is that same generic entry point, used directly.
        on("keydown", (event) => {
          if ((event.native as KeyboardEvent | null)?.key === "Enter") addTodo();
        });
      });
      button("Add", {}, () => {
        classes("calm-action", "calm-action--primary");
        onClick(addTodo);
      });
    });

    // Safe here in a way the library page's fetchInto + when combination is
    // not (see the comment on libraryPage): nothing asynchronous mutates
    // `todos` during the render itself. Server and client both start from the
    // same empty list, so both evaluate `isEmpty`/`hasTodos` at `true`/`false`
    // on their very first synchronous pass -- hydration has nothing to
    // disagree about. Every change after that comes from a click or a
    // keystroke, which only ever happens once a browser is already attached.
    when(isEmpty, () => {
      div(() => {
        classes("todos__empty");
        text("Nothing to do yet — add one above.");
      });
    });

    when(hasTodos, () => {
      div(() => {
        classes("todos__list");
        forEach(todos, (todo, index) => {
          div(() => {
            classes("todos__row");
            classIf("todos__row--done", todo.done);

            button("✓", {}, () => {
              classes("todos__toggle");
              attr("aria-pressed", todo.done.map((done) => String(done)));
              onClick(() => todo.done.set(!todo.done.get));
            });

            div(() => {
              classes("todos__label");
              text(todo.label);
            });

            button("Remove", {}, () => {
              classes("todos__remove");
              onClick(() => todos.removeAt(index));
            });
          });
        });
      });

      div(() => {
        classes("todos__footer");
        div(() => {
          classes("todos__count");
          text(remaining.map((count) => `${count} item${count === 1 ? "" : "s"} left`));
        });
        button("Clear completed", {}, () => {
          classes("todos__clear");
          onClick(() => todos.setAll(todos.get.filter((todo) => !todo.done.get)));
        });
      });
    });
  });
}

export function format(html: string): string {
  return html.replace(/></g, ">\n<");
}
