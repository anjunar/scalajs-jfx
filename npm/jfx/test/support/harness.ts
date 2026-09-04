/**
 * Shared setup for the stub-runtime tests.
 *
 * Everything here goes through the package's own public entry point
 * (`../../src/index.js`) rather than reaching into `runtime.ts` or `scope.ts`
 * directly. A harness that used private paths would keep passing after a
 * refactoring that broke every consumer -- which is the exact failure this
 * harness exists to catch.
 */
import { installRuntime, mount, renderToString, resetRuntime } from "../../src/index.js";
import type { MountedApp, SsrResult } from "../../src/index.js";
import { stubRuntime } from "../../src/stub/index.js";

/** Installs the stub runtime for one test. Call from `beforeEach`. */
export function useStubRuntime(): void {
  resetRuntime();
  installRuntime(stubRuntime);
}

export interface Rendered {
  readonly root: HTMLElement;
  readonly app: MountedApp;
  /** The container's current markup, without the container element itself. */
  html(): string;
}

/** Mounts `body` into a detached `<div>` and hands back both halves. */
export function render(body: () => void): Rendered {
  const root = document.createElement("div");
  document.body.appendChild(root);
  const app = mount(root, body);
  return { root, app, html: () => root.innerHTML };
}

export function renderServerSide(body: () => void): Promise<SsrResult> {
  return renderToString(body);
}

/** Lets queued microtasks -- an observer, a resolved fetch -- run. */
export function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
