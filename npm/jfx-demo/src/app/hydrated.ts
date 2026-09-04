/**
 * False on the server and on the client's first synchronous render pass, so
 * anything gated behind it can never disagree between SSR and hydration --
 * the same reasoning as the theme toggle in E-7. `entry-client.ts` flips it
 * to true once `hydrate()` has settled; nothing on the server ever does.
 *
 * Created lazily, not as a module-top-level `property(false)`: both
 * `entry-server.ts` and `node/bridge.ts` import the route table before they
 * call `installRuntime()` (imports evaluate before a module's own body
 * runs), and every runtime factory -- `property()` included -- throws until
 * a runtime is installed. Lazy construction defers that first call to
 * render time, which is always after `installRuntime()`. A shared singleton
 * is safe even across SSR requests because nothing ever mutates it on the
 * server; only the browser-only line in entry-client.ts does.
 */
import { property } from "@anjunar/jfx-core";
import type { Property } from "@anjunar/jfx-core";

let instance: Property<boolean> | null = null;

export function hydratedProperty(): Property<boolean> {
  return (instance ??= property(false));
}
