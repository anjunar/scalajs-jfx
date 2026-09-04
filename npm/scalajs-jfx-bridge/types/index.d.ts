/**
 * The type surface of the linked Scala.js bundle.
 *
 * `dist/` is linker output -- generated, gitignored, and without a `.d.ts` of
 * its own. This file supplies one, and it does so by *importing* the contract
 * from `@anjunar/jfx-core` rather than restating it. That direction is
 * deliberate and matches the Scala side exactly: `jfxBridge.dependsOn(jfxCore)`
 * (ARCHITECTURE.md §1). The bridge implements core's contract, so core owns the
 * type and there is only ever one definition of it to keep in step.
 *
 * This replaces the ambient `declare module` that used to live in
 * `@anjunar/jfx-core`'s own `src/bridge.d.ts`. That arrangement did not survive
 * packaging: `tsc` does not copy an input `.d.ts` into `outDir`, and it strips
 * the `/// <reference path>` that was supposed to pull it into a consumer's
 * program. The declaration therefore never reached anyone who installed the
 * package, and every consumer got TS7016 under `strict` -- see
 * CLAUDE_REVIEW_3.md §9, Risiko 3, and test/consumer.test.ts, which now proves
 * the opposite.
 */
import type { JfxRuntime } from "@anjunar/jfx-core";

/**
 * The one runtime instance this bundle installs.
 *
 * ```ts
 * import { installRuntime } from "@anjunar/jfx-core";
 * import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
 *
 * installRuntime(bridgeRuntime);
 * ```
 */
export declare const bridgeRuntime: JfxRuntime;
