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
import { carousel, column, tableView, tab, tabs } from "@anjunar/jfx-controls";
import { floatingWindow, notify, overlay } from "@anjunar/jfx-viewport";
import {
  arrayForm,
  comboBox,
  form,
  imageCropper,
  input as formInput,
  inputContainer,
  notBlank,
  email as emailValidator,
  size as sizeValidator,
  subForm,
} from "@anjunar/jfx-forms";
import type { MediaValue } from "@anjunar/jfx-forms";

// The navigation bar lives in `routes.ts`'s `appShell`, rendered around every
// route by `router(appRoutes, config, appShell)` -- `routerLink`s, not plain
// anchors, so moving between pages is a client-side route change. These page
// bodies are just the page content; the Node runners (`node-stub.ts` /
// `node-bridge.ts`) render them bare, with no shell and no router.

/* --------------------------------------------------------------- StatePage */

export function statePage(): void {
  const counter = property(0);
  const status = counter.map((value) => `Current value: ${value}`);

  vbox(() => {
    classes("clarity-grid");

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

/* ---------------------------------------------------------------- ControlsPage */

/**
 * `@anjunar/jfx-controls` in one screen: a tab strip, a table over a local
 * `ListProperty`, and a carousel. All three are registry entries in `jfx-bridge`
 * (`ControlFactories.scala`); the bodies here are ordinary core DSL, wrapped by
 * the facade into the `(scope) => void` the bridge runs. Rendered by
 * `node-bridge.ts` (not the stub -- controls have no stub) and hydrated in the
 * browser at `/controls`.
 */
interface Album {
  readonly title: string;
  readonly artist: string;
  readonly year: number;
}

const albums: readonly Album[] = [
  { title: "Kind of Blue", artist: "Miles Davis", year: 1959 },
  { title: "Blue Train", artist: "John Coltrane", year: 1957 },
  { title: "The Köln Concert", artist: "Keith Jarrett", year: 1975 },
  { title: "Head Hunters", artist: "Herbie Hancock", year: 1973 },
  { title: "Speak No Evil", artist: "Wayne Shorter", year: 1966 },
];

export function controlsPage(): void {
  div(() => {
    classes("controls-page");
    heading(2, () => text("Controls"));

    tabs([
      tab("Table", () => {
        const rows = listProperty<Album>([...albums]);
        div(() => {
          classes("controls-page__frame");
          tableView(
            rows,
            [
              column("Title", (album) => text(album.title), { prefWidth: 260, sortable: true, sortKey: "title" }),
              column("Artist", (album) => text(album.artist), { prefWidth: 220 }),
              column("Year", (album) => text(String(album.year)), { prefWidth: 90 }),
            ],
            { rowHeight: 40, crawlable: true, crawlId: "albums" }
          );
        });
      }),
      tab("Carousel", () => {
        const slides = listProperty<Album>([...albums]);
        div(() => {
          classes("controls-page__frame");
          carousel(
            slides,
            (album, index) => {
              div(() => {
                classes("controls-page__slide");
                div(() => {
                  classes("docs-card__title");
                  text(`${index + 1}. ${album.title}`);
                });
                div(() => {
                  classes("docs-card__summary");
                  text(`${album.artist} — ${album.year}`);
                });
              });
            },
            { autoAdvanceMs: 3200, ssrShowAllStates: true }
          );
        });
      }),
    ]);
  });
}

/* ---------------------------------------------------------------- ViewportPage */

/**
 * `@anjunar/jfx-viewport` in one screen: a notification, a window, and a
 * menu-style overlay. All four registry entries it uses (`viewport`, `window`,
 * `overlay`, `notification`) live in `jfx-bridge` (`ViewportFactories.scala`).
 * Rendered by `node-bridge.ts` (not the stub -- the viewport has no stub) and
 * hydrated in the browser at `/viewport`. Needs a `viewport` ancestor -- the
 * one `entry-client.ts`/`entry-server.ts` wrap the whole app in.
 */
export function viewportPage(): void {
  const windowOpen = property(false);
  const menuOpen = property(false);

  div(() => {
    classes("viewport-page");
    heading(2, () => text("Viewport"));

    div(() => {
      classes("viewport-page__row");
      button("Notify", {}, () => {
        classes("calm-action", "calm-action--primary");
        onClick(() => notify("Saved.", { kind: "success" }));
      });

      button("Open window", {}, () => {
        classes("calm-action", "calm-action--secondary");
        onClick(() => windowOpen.set(true));
      });

      div(() => {
        classes("viewport-page__menu");
        button("Menu", {}, () => {
          classes("calm-action", "calm-action--secondary");
          onClick(() => menuOpen.set(!menuOpen.get));
        });
        when(menuOpen, () => {
          overlay({ widthPx: 200 }, () => {
            div(() => {
              classes("viewport-page__menu-item");
              onClick(() => {
                notify("Menu item chosen.");
                menuOpen.set(false);
              });
              text("Choose me");
            });
          });
        });
      });
    });

    when(windowOpen, () => {
      floatingWindow(
        { title: "A room for thoughts", widthPx: 400, heightPx: 260, onClose: () => windowOpen.set(false) },
        () => {
          div(() => {
            classes("viewport-page__window-body");
            text("This content is mounted into the shared viewport layer, not into the route subtree.");
            button("Confirm note", {}, () => {
              onClick(() => notify("The note in the window was confirmed.", { kind: "success" }));
            });
          });
        }
      );
    });
  });
}

/* ------------------------------------------------------------------- FormsPage */

/**
 * `@anjunar/jfx-forms` in one screen: a validated `name`/`email`, a repeating
 * `tags` field, a nested `address` sub-form, a `color` combo box, and an
 * `avatar` image cropper. All eight registry entries `jfx-forms` needs
 * (`form`, `sub-form`, `input`, `input-container`, `field-set`, `array-form`,
 * `combo-box`, `image-cropper`) live in `jfx-bridge` (`FormFactories.scala`).
 * Rendered by `node-bridge.ts` (not the stub -- forms has no stub) and
 * hydrated in the browser at `/forms`. `comboBox`'s dropdown needs a
 * `viewport` ancestor, which `entry-client.ts`/`entry-server.ts` already wrap
 * the whole app in.
 */
export function formsPage(): void {
  const address = { city: property("") };
  const model = {
    name: property(""),
    email: property(""),
    tags: listProperty<string>(["typescript"]),
    address: property(address),
    color: property<string | null>(null),
    avatar: property<MediaValue | null>(null),
  };

  div(() => {
    classes("forms-page");
    heading(2, () => text("Forms"));

    form(
      model,
      {
        schema: {
          name: [notBlank()],
          email: [notBlank(), emailValidator()],
        },
      },
      () => {
        div(() => {
          classes("forms-page__frame");

          inputContainer({ label: "Name" }, () => {
            formInput("name");
          });

          inputContainer({ label: "Email" }, () => {
            formInput("email", { type: "email" });
          });

          div(() => {
            classes("forms-page__field-label");
            text("Tags");
          });
          arrayForm("tags", (index) => {
            formInput(`tags-${index}`);
          });
          button("Add tag", {}, () => {
            classes("calm-action", "calm-action--secondary");
            onClick(() => model.tags.add(""));
          });

          div(() => {
            classes("forms-page__field-label");
            text("Address");
          });
          subForm("address", address, { schema: { city: [sizeValidator(1, 60)] } }, () => {
            inputContainer({ label: "City" }, () => {
              formInput("city");
            });
          });

          div(() => {
            classes("forms-page__field-label");
            text("Favorite color");
          });
          comboBox("color", { items: ["Red", "Green", "Blue"], placeholder: "Choose one" });

          div(() => {
            classes("forms-page__field-label");
            text("Avatar");
          });
          imageCropper("avatar", { aspectRatio: 1, windowTitle: "Crop avatar" });
        });

        div(() => {
          classes("clarity-action-row");
          button("Log model", {}, () => {
            classes("calm-action", "calm-action--primary");
            onClick(() =>
              notify(
                `name=${model.name.get} email=${model.email.get} tags=${model.tags.get.join(",")} city=${address.city.get} color=${model.color.get ?? ""}`,
                { kind: "info", durationMs: 4000 }
              )
            );
          });
        });
      }
    );
  });
}

export function format(html: string): string {
  return html.replace(/></g, ">\n<");
}
