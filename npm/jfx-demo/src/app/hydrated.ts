/**
 * False on the server and on the client's first synchronous render pass, so
 * anything gated behind it can never disagree between SSR and hydration --
 * the same reasoning as the theme toggle in E-7. `entry-client.ts` flips it
 * to true once `hydrate()` has settled; nothing on the server ever does.
 *
 * Created lazily so importing the page manifest does not require a runtime:
 * the stub runner installs its runtime in main(), while the browser and SSR
 * entry points install the bridge through its package import. A shared singleton
 * is safe even across SSR requests because nothing ever mutates it on the
 * server; only the browser-only line in entry-client.ts does.
 */
import { property } from "@anjunar/jfx-core";
import type { Property } from "@anjunar/jfx-core";

let instance: Property<boolean> | null = null;

export function hydratedProperty(): Property<boolean> {
  return (instance ??= property(false));
}
