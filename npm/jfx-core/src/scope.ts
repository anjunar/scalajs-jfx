import type { ComponentHandle, ScopeHandle } from "./contract.js";

/**
 * The ambient render scope.
 *
 * Scala passes `(using AbstractComponent, Cursor)` implicitly, which is what makes
 * its DSL read like markup. TypeScript has no implicit parameters, so the same
 * effect comes from a stack that is pushed around every synchronous body.
 *
 * ARCHITECTURE.md §5 forbids request-dependent state in shared objects, and this
 * stack is exactly that shape. It is safe only under one rule, which the code
 * below enforces rather than documents:
 *
 *   **A scope is installed around synchronous work only. Nothing awaits while a
 *   scope is installed.**
 *
 * Node runs one request's synchronous body to completion before touching another,
 * so a stack that never survives an `await` cannot be observed by a second
 * request. Asynchronous continuations do not inherit the stack; they re-enter
 * through {@link capture}, which restores the scope it was taken in.
 */

interface Frame {
  readonly scope: ScopeHandle;
  readonly self: ComponentHandle | null;
}

let stack: Frame[] = [];

class ScopeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ScopeError";
  }
}

/** The scope currently being composed. Throws outside a render pass. */
export function currentScope(): ScopeHandle {
  const frame = stack[stack.length - 1];
  if (frame === undefined) {
    throw new ScopeError(
      "No render scope is active. Element builders may only be called inside a " +
        "render body, or inside a callback restored with capture()."
    );
  }
  return frame.scope;
}

/** The component currently being composed -- the receiver of `classes`, `on`, ... */
export function currentComponent(): ComponentHandle {
  const frame = stack[stack.length - 1];
  if (frame === undefined || frame.self === null) {
    throw new ScopeError(
      "No component is being composed. Attribute, class, style and event helpers " +
        "need an element body around them."
    );
  }
  return frame.self;
}

/** True when a render pass is active. Lets callers branch without catching. */
export function hasScope(): boolean {
  return stack.length > 0;
}

/**
 * Runs `body` with `scope` installed, then restores the previous frame.
 *
 * A body that returns a thenable is a bug, not a feature: it would await with the
 * scope installed and hand a second request the wrong parent. That is rejected
 * here rather than debugged later (ARCHITECTURE.md §7 -- fail loudly).
 */
export function withScope<T>(
  scope: ScopeHandle,
  self: ComponentHandle | null,
  body: () => T
): T {
  stack.push({ scope, self });
  let result: T;
  try {
    result = body();
  } finally {
    stack.pop();
  }

  if (isThenable(result)) {
    throw new ScopeError(
      "A render body returned a promise. Render bodies are synchronous; use " +
        "fetchInto() or an async route loader to bring asynchronous data in."
    );
  }

  return result;
}

/**
 * Freezes the current component stack so a later callback can run inside it.
 * Each scope is refreshed first, because hydration cursors are one-shot.
 *
 * Use this at every boundary the runtime does not own: a `setTimeout`, a
 * `then` you wrote yourself, an event handler from a third-party library. The
 * DSL's own asynchronous helpers already do it.
 */
export function capture(): <T>(body: () => T) => T {
  // Keep the component position, but replace every cursor with a fresh append
  // cursor. Hydration cursors are one-shot claim walkers; retaining one here
  // makes a later event handler try to claim nodes from an already completed
  // hydration pass.
  const frozen = stack.map((frame) => ({
    self: frame.self,
    scope: frame.scope.fresh(),
  }));
  return <T>(body: () => T): T => {
    const previous = stack;
    stack = frozen;
    try {
      return body();
    } finally {
      stack = previous;
    }
  };
}

function isThenable(value: unknown): boolean {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as { then?: unknown }).then === "function"
  );
}
