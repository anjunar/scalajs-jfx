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
 * import "@anjunar/scalajs-jfx-bridge";
 * ```
 * Importing the package automatically installs this instance in core.
 */
export declare const bridgeRuntime: JfxRuntime;

/** A stable TypeScript view over scala-java-time's real LocalDate value. */
export interface ScalaLocalDate {
  readonly year: number;
  readonly monthValue: number;
  readonly dayOfMonth: number;
  toString(): string;
  format(pattern: string, languageTag: string): string;
}

/** A stable TypeScript view over scala-java-time's real Instant value. */
export interface ScalaInstant {
  readonly epochMilli: number;
  toString(): string;
  format(pattern: string, languageTag: string, zoneId: string): string;
}

export interface ScalaLocalDateTime {
  readonly year: number;
  readonly monthValue: number;
  readonly dayOfMonth: number;
  readonly hour: number;
  readonly minute: number;
  toString(): string;
  format(pattern: string, languageTag: string): string;
}

export declare function parseLocalDate(value: string): ScalaLocalDate;
export declare function parseInstant(value: string): ScalaInstant;
export declare function parseLocalDateTime(value: string): ScalaLocalDateTime;
