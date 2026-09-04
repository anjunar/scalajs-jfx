import type {
  Build,
  JfxRuntime,
  ListProperty,
  MountedApp,
  Property,
  SsrOptions,
  SsrResult,
} from "./contract.js";
import { withScope } from "./scope.js";

let installed: JfxRuntime | null = null;

/**
 * Installs the runtime for this process. Called once at boot -- by the Scala.js
 * bundle in production, by the stub in tests.
 */
export function installRuntime(runtime: JfxRuntime): void {
  if (installed !== null && installed !== runtime) {
    throw new Error(
      `A JFX runtime is already installed ("${installed.name}"). Installing a ` +
        `second one ("${runtime.name}") would split the component tree.`
    );
  }
  installed = runtime;
}

export function runtime(): JfxRuntime {
  if (installed === null) {
    throw new Error(
      "No JFX runtime installed. Call installRuntime() with the Scala.js bridge " +
        "(or the stub runtime) before rendering."
    );
  }
  return installed;
}

/** For tests that swap runtimes between cases. */
export function resetRuntime(): void {
  installed = null;
}

export function property<T>(initial: T): Property<T> {
  return runtime().property(initial);
}

export function listProperty<T>(initial: readonly T[] = []): ListProperty<T> {
  return runtime().listProperty(initial);
}

/** Client-side render into an empty container. */
export function mount(root: Element, body: () => void): MountedApp {
  return runtime().mount(root, asBuild(body));
}

/** Claim a server-rendered tree. */
export function hydrate(
  root: Document | Element,
  body: () => void
): Promise<MountedApp> {
  return runtime().hydrate(root, asBuild(body));
}

/** Server-side render, including any asynchronous work the body registered. */
export function renderToString(
  body: () => void,
  options?: SsrOptions
): Promise<SsrResult> {
  return runtime().renderToString(asBuild(body), options);
}

function asBuild(body: () => void): Build {
  return (scope) => withScope(scope, null, body);
}
