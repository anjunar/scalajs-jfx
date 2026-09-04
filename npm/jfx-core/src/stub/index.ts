/**
 * The stub runtime.
 *
 * It implements the same contract as `jfx-bridge` on top of a small host
 * abstraction, so the declarative layer can be developed, typechecked and tested
 * without a Scala.js build. It is deliberately *not* a second framework:
 *
 *  - `forEach` re-renders its whole block instead of reconciling by key.
 *  - `hydrate` clears and re-renders instead of claiming server nodes.
 *  - There is no `HeadSink`, no i18n, no router, no form binding. `head()` mounts
 *    a plain `<head>` element; `documentHead()` is always `null`, so a page's
 *    `documentHead()?.push(...)` calls harmlessly no-op under this runtime.
 *
 * Those are exactly the parts where the Scala runtime earns its keep. Anything
 * this stub gets away with, the real bridge must still do properly.
 */
import type {
  Build,
  ComponentHandle,
  Disposable,
  DocumentHeadHandle,
  JfxRuntime,
  ListProperty,
  MountedApp,
  Property,
  Reactive,
  ReadOnlyProperty,
  ScopeHandle,
  SsrOptions,
  SsrResult,
  UiEvent,
} from "../contract.js";
import {
  DomDocument,
  DomElement,
  renderChildren,
  SsrDocument,
  SsrElement,
  type HostDocument,
  type HostElement,
  type HostNode,
} from "./host.js";
import { StubListProperty, StubProperty } from "./state.js";

/* ---------------------------------------------------------------- async work */

class AsyncRenderContext {
  private tasks: Array<Promise<unknown>> = [];
  private collecting = true;

  add(task: Promise<unknown>): void {
    if (this.collecting) this.tasks.push(task);
  }

  async drain(): Promise<void> {
    let offset = 0;
    let depth = 0;
    while (this.collecting) {
      const batch = this.tasks.slice(offset);
      if (batch.length === 0) break;
      if (++depth > 100) throw new Error("AsyncRender: max depth exceeded");
      offset += batch.length;
      await Promise.all(batch);
    }
    this.collecting = false;
    this.tasks = [];
  }

  cancel(): void {
    this.collecting = false;
    this.tasks = [];
  }
}

/* ------------------------------------------------------------------ cursor */

interface Cursor {
  readonly parent: HostElement;
  readonly before: HostNode | null;
}

/* --------------------------------------------------------------- component */

class StubComponent implements ComponentHandle {
  private readonly baseClasses: string[] = [];
  private userClasses: readonly string[] = [];
  private readonly disposables: Disposable[] = [];

  constructor(
    readonly tagName: string,
    private readonly element: HostElement | null
  ) {}

  private syncClasses(): void {
    if (this.element === null) return;
    const all = [...this.baseClasses, ...this.userClasses];
    this.element.setClassNames([...new Set(all)]);
  }

  addClass(name: string): void {
    if (!this.baseClasses.includes(name)) {
      this.baseClasses.push(name);
      this.syncClasses();
    }
  }
  removeClass(name: string): void {
    const index = this.baseClasses.indexOf(name);
    if (index >= 0) {
      this.baseClasses.splice(index, 1);
      this.syncClasses();
    }
  }
  setClasses(names: readonly string[]): void {
    this.userClasses = [...names];
    this.syncClasses();
  }
  classIf(name: string, condition: ReadOnlyProperty<boolean>): void {
    this.addDisposable(
      condition.observe((enabled) =>
        enabled ? this.addClass(name) : this.removeClass(name)
      )
    );
  }
  setAttribute(name: string, value: string): void {
    this.element?.setAttribute(name, value);
  }
  removeAttribute(name: string): void {
    this.element?.removeAttribute(name);
  }
  attribute(name: string): string | null {
    return this.element?.getAttribute(name) ?? null;
  }
  setDomProperty(name: string, value: unknown): void {
    this.element?.setDomProperty(name, value);
  }
  setStyle(name: string, value: string): void {
    this.element?.setStyle(name, value);
  }
  removeStyle(name: string): void {
    this.element?.removeStyle(name);
  }
  on(eventName: string, handler: (event: UiEvent) => void): void {
    if (this.element === null) return;
    const off = this.element.addEventListener(eventName, (native) =>
      handler(toUiEvent(native))
    );
    this.addDisposable({ dispose: off });
  }
  addDisposable(disposable: Disposable): void {
    this.disposables.push(disposable);
  }
  dispose(): void {
    for (const disposable of this.disposables.splice(0)) disposable.dispose();
  }
}

function toUiEvent(native: Event): UiEvent {
  return {
    type: native.type,
    target: native.target,
    preventDefault: () => native.preventDefault(),
    stopPropagation: () => native.stopPropagation(),
    native,
  };
}

/* -------------------------------------------------------------------- scope */

type Registry = Record<
  string,
  (
    scope: StubScope,
    options: Record<string, unknown>,
    body: (self: ComponentHandle, scope: ScopeHandle) => void
  ) => ComponentHandle
>;

class StubScope implements ScopeHandle {
  constructor(
    private readonly doc: HostDocument,
    private readonly cursor: Cursor,
    private readonly owner: StubComponent,
    private readonly async: AsyncRenderContext,
    /**
     * Every enclosing block's node list, outermost first.
     *
     * A block (`when`, `forEach`, `fetch`) has to be able to take back exactly
     * what was mounted inside it -- including what a *nested* block mounted.
     * One tracker per scope was not enough for that: a `forEach` inside a
     * `when` inserted its items into the shared parent through its own tracker
     * only, so `when(false)` left them standing.
     */
    private readonly trackers: readonly HostNode[][] = []
  ) {}

  get isBrowser(): boolean {
    return this.doc.isBrowser;
  }
  get isHydrating(): boolean {
    return false;
  }

  private insert(node: HostNode): void {
    this.cursor.parent.insertBefore(node, this.cursor.before);
    for (const tracker of this.trackers) tracker.push(node);
  }

  child(
    tagName: string,
    body: (self: ComponentHandle, scope: ScopeHandle) => void
  ): ComponentHandle {
    const element = this.doc.createElement(tagName);
    this.insert(element);
    const component = new StubComponent(tagName, element);
    this.owner.addDisposable({ dispose: () => component.dispose() });

    const inner = new StubScope(
      this.doc,
      { parent: element, before: null },
      component,
      this.async
    );

    try {
      body(component, inner);
    } catch (error) {
      this.cursor.parent.removeChild(element);
      throw error;
    }
    return component;
  }

  text(value: Reactive<string>): ComponentHandle {
    const initial = typeof value === "string" ? value : value.get;
    const node = this.doc.createText(initial);
    this.insert(node);
    const component = new StubComponent("#text", null);
    if (typeof value !== "string") {
      this.owner.addDisposable(
        value.observeWithoutInitial((next) => node.setText(next))
      );
    }
    return component;
  }

  head(body: (self: ComponentHandle, scope: ScopeHandle) => void): ComponentHandle {
    return this.child("head", body);
  }

  documentHead(): DocumentHeadHandle | null {
    return null;
  }

  /**
   * Opens a virtual range and returns a scope that composes into it, plus the
   * `clear` that takes the whole block back.
   *
   * The block gets an owner of its own. Everything composed inside it -- child
   * components, their subscriptions, nested blocks -- hangs off that owner, so
   * `clear()` both removes the nodes and lets go of what was watching them.
   * Without it, a `when` that flipped to `false` left its children's observers
   * subscribed, writing into nodes that were no longer in the document.
   */
  private range(label: string): { scope: StubScope; clear: () => void } {
    const start = this.doc.createComment(`jfx:${label}:start`);
    const end = this.doc.createComment(`jfx:${label}:end`);
    this.insert(start);
    this.insert(end);

    const parent = this.cursor.parent;
    const nodes: HostNode[] = [];
    const trackers = [...this.trackers, nodes];

    const owner = new StubComponent(`#${label}`, null);
    this.owner.addDisposable({ dispose: () => owner.dispose() });

    const clear = (): void => {
      // dispose() drains the list rather than sealing the component, so the
      // same owner serves every pass of the block.
      owner.dispose();
      for (const node of nodes.splice(0)) {
        parent.removeChild(node);
        for (const tracker of trackers) {
          const index = tracker.indexOf(node);
          if (index >= 0) tracker.splice(index, 1);
        }
      }
    };

    return {
      clear,
      scope: new StubScope(this.doc, { parent, before: end }, owner, this.async, trackers),
    };
  }

  when(active: ReadOnlyProperty<boolean>, body: (scope: ScopeHandle) => void): void {
    const { scope, clear } = this.range("Condition");
    // On the outer owner, not the block's: this subscription outlives every
    // pass of the block and is what starts the next one.
    this.owner.addDisposable(
      active.observe((enabled) => {
        clear();
        if (enabled) body(scope);
      })
    );
  }

  forEach<T>(
    items: ReadOnlyProperty<readonly T[]>,
    body: (item: T, index: number, scope: ScopeHandle) => void
  ): void {
    const { scope, clear } = this.range("Foreach");
    this.owner.addDisposable(
      items.observe((values) => {
        clear();
        values.forEach((value, index) => body(value, index, scope));
      })
    );
  }

  fetch<T>(
    load: () => Promise<T>,
    onLoaded: (value: T, scope: ScopeHandle) => void,
    onFailed: (error: unknown, scope: ScopeHandle) => void
  ): void {
    const { scope } = this.range("Fetch");
    const task = Promise.resolve()
      .then(load)
      .then(
        (value) => onLoaded(value, scope),
        (error: unknown) => onFailed(error, scope)
      );
    this.async.add(task);
  }

  component(
    name: string,
    options: Record<string, unknown>,
    body: (self: ComponentHandle, scope: ScopeHandle) => void
  ): ComponentHandle {
    const factory = registry[name];
    if (factory === undefined) {
      throw new Error(
        `Unknown component "${name}". The stub runtime registers: ` +
          `${Object.keys(registry).join(", ")}.`
      );
    }
    return factory(this, options, body);
  }
}

/* ----------------------------------------------------------------- registry */

const registry: Registry = {
  vbox: (scope, _options, body) =>
    scope.child("div", (self, inner) => {
      self.addClass("jfx-vbox");
      body(self, inner);
    }),

  hbox: (scope, _options, body) =>
    scope.child("div", (self, inner) => {
      self.addClass("jfx-hbox");
      body(self, inner);
    }),

  button: (scope, options, body) =>
    scope.child("button", (self, inner) => {
      self.addClass("jfx-button");
      self.setAttribute("type", String(options["type"] ?? "button"));
      const label = options["label"];
      if (label !== undefined) {
        inner.text(label as Reactive<string>);
      }
      const disabled = options["disabled"];
      if (disabled !== undefined) applyDisabled(self, disabled as Reactive<boolean>);
      body(self, inner);
    }),
};

function applyDisabled(self: ComponentHandle, value: Reactive<boolean>): void {
  const apply = (enabled: boolean): void => {
    if (enabled) {
      self.setAttribute("disabled", "");
      self.setAttribute("aria-disabled", "true");
    } else {
      self.removeAttribute("disabled");
      self.setAttribute("aria-disabled", "false");
    }
  };
  if (typeof value === "boolean") apply(value);
  else self.addDisposable(value.observe(apply));
}

/* ------------------------------------------------------------------ runtime */

export class StubRuntime implements JfxRuntime {
  readonly name = "stub";

  property<T>(initial: T): Property<T> {
    return new StubProperty(initial);
  }

  listProperty<T>(initial: readonly T[]): ListProperty<T> {
    return new StubListProperty(initial);
  }

  mount(root: Element, build: Build): MountedApp {
    const doc = new DomDocument(root.ownerDocument);
    const host = new DomElement(root);
    const owner = new StubComponent("#root", host);
    const async = new AsyncRenderContext();
    build(new StubScope(doc, { parent: host, before: null }, owner, async));
    void async.drain();
    return { dispose: () => owner.dispose() };
  }

  async hydrate(root: Document | Element, build: Build): Promise<MountedApp> {
    const element =
      root instanceof Document ? root.documentElement : (root as Element);
    while (element.firstChild !== null) element.removeChild(element.firstChild);
    return this.mount(element, build);
  }

  async renderToString(build: Build, options?: SsrOptions): Promise<SsrResult> {
    const doc = new SsrDocument();
    // A real `<html>` root for `options.document` -- `build` composes `head()`/a
    // body element directly, no enclosing `html(...)` -- otherwise an invisible
    // container whose own tag never reaches the output.
    const container = options?.document
      ? new SsrElement("html")
      : new SsrElement("jfx:fragment");
    const owner = new StubComponent("#root", container);
    const async = new AsyncRenderContext();

    try {
      build(new StubScope(doc, { parent: container, before: null }, owner, async));
      await withTimeout(async.drain(), options?.timeoutMs ?? 10_000, async);
      return {
        html: options?.document ? container.renderHtml() : renderChildren(container.children),
        status: 200,
        headers: {},
      };
    } finally {
      owner.dispose();
    }
  }
}

function withTimeout(
  work: Promise<void>,
  timeoutMs: number,
  async: AsyncRenderContext
): Promise<void> {
  return new Promise((resolve, reject) => {
    const handle = setTimeout(() => {
      async.cancel();
      reject(new Error(`SSR render timed out after ${timeoutMs} ms`));
    }, timeoutMs);
    work.then(
      (value) => {
        clearTimeout(handle);
        resolve(value);
      },
      (error: unknown) => {
        clearTimeout(handle);
        async.cancel();
        reject(error instanceof Error ? error : new Error(String(error)));
      }
    );
  });
}

export const stubRuntime = new StubRuntime();
