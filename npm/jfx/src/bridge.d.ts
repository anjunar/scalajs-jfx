/**
 * The ambient shape of `@anjunar/scalajs-jfx-bridge`.
 *
 * That package ships no `.d.ts` of its own: `@anjunar/jfx` owns the contract
 * (`contract.ts`), so it also owns the declaration of what its runtime peer looks
 * like, the same way a plugin host declares the shape it expects a plugin to
 * have. This keeps the contract in exactly one place instead of two copies that
 * can drift -- see JAVASCRIPT_API.md §4.
 */
declare module "@anjunar/scalajs-jfx-bridge" {
  import type { JfxRuntime } from "./contract.js";

  export const bridgeRuntime: JfxRuntime;
}
