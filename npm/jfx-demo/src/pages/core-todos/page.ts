import {
  attr,
  button,
  classes,
  classIf,
  disposeWith,
  div,
  element,
  forEach,
  listProperty,
  on,
  onClick,
  onInput,
  property,
  self,
  text,
  when,
} from "@anjunar/jfx-core";
import { i18n, locale, named, t } from "@anjunar/jfx-core";
import type { ComponentHandle, Disposable, Property, UiEvent } from "@anjunar/jfx-core";
import { translated } from "../../app/i18n.js";

/**
 * `input` has no typed wrapper in dsl.ts -- there is no `vbox`/`hbox`/`button`
 * for it, the way jfx-core deliberately keeps the wrapper set small.
 * `element("input")` is the same one-line extension point `div`/`span`/`anchor`
 * are themselves built from, so reaching for it here is the ordinary way to
 * grow the DSL, not a workaround.
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
  return (event.target as HTMLInputElement | null)?.value ?? "";
}

export function coreTodosPage(): void {
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
  const activeLocale = locale();
  const remainingLabel = property("");
  const updateRemainingLabel = (): void => {
    const count = remaining.get;
    const message =
      count === 1 ? i18n`${named("count", count)} item left` : i18n`${named("count", count)} items left`;
    remainingLabel.set(t(message).get);
  };
  updateRemainingLabel();
  disposeWith(remaining.observeWithoutInitial(updateRemainingLabel));
  disposeWith(activeLocale.observeWithoutInitial(updateRemainingLabel));
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

  let draftField: ComponentHandle | null = null;

  function addTodo(): void {
    const label = draft.get.trim();
    if (label === "") return;
    todos.add({ label, done: property(false) });
    draft.set("");
    draftField?.setDomProperty("value", "");
  }

  div(() => {
    classes("flex", "flex-col", "gap-3");
    disposeWith(todos.observe(subscribeToItems));

    div(() => {
      classes("flex", "gap-2");
      input(() => {
        draftField = self();
        classes("px-3", "py-1.5", "border", "border-line", "rounded-control", "flex-1");
        attr("type", "text");
        attr("placeholder", translated("Add a todo…").get);
        onInput((event) => draft.set(inputValue(event)));
        on("keydown", (event) => {
          if ((event.native as KeyboardEvent | null)?.key === "Enter") addTodo();
        });
      });
      button(translated("Add"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(addTodo);
      });
    });

    when(isEmpty, () => {
      div(() => {
        classes("text-ink-muted", "italic");
        text(translated("Nothing to do yet — add one above."));
      });
    });

    when(hasTodos, () => {
      div(() => {
        classes("flex", "flex-col", "gap-1.5");
        forEach(todos, (todo, index) => {
          div(() => {
            classes("flex", "items-center", "gap-3", "px-3", "py-2", "border", "border-line", "rounded-control");
            classIf("line-through", todo.done);

            button("✓", {}, () => {
              classes("w-7", "h-7", "rounded-full", "border", "border-line");
              attr("aria-pressed", todo.done.map((done) => String(done)));
              onClick(() => todo.done.set(!todo.done.get));
            });

            div(() => {
              classes("flex-1");
              text(todo.label);
            });

            button(translated("Remove"), {}, () => {
              classes("text-ink-muted");
              onClick(() => todos.removeAt(index));
            });
          });
        });
      });

      div(() => {
        classes("flex", "items-center", "justify-between", "text-ink-soft");
        text(remainingLabel);
        button(translated("Clear completed"), {}, () => {
          classes("underline");
          onClick(() => todos.setAll(todos.get.filter((todo) => !todo.done.get)));
        });
      });
    });
  });
}
