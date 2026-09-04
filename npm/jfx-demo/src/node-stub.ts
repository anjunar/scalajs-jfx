/**
 * Renders `pages.ts` against the stub runtime and prints the HTML, so the
 * output can be compared against what the Scala SSR path produces.
 *
 * Run with `npm run demo`. `bridgeDemo.ts` runs the identical page functions
 * against the real Scala.js bridge instead -- see that file for what changes
 * and, more to the point, what does not.
 */
import { installRuntime, property, renderToString } from "@anjunar/jfx-core";
import { stubRuntime } from "@anjunar/jfx-core/stub";
import { format, libraryPage, statePage } from "./pages.js";

async function main(): Promise<void> {
  installRuntime(stubRuntime);

  const state = await renderToString(statePage);
  console.log("--- StatePage ------------------------------------------------");
  console.log(format(state.html));

  const library = await renderToString(libraryPage);
  console.log("\n--- LibraryPage (async loader drained before serialising) ----");
  console.log(format(library.html));

  console.log("\n--- Reactivity check ----------------------------------------");
  const counter = property(0);
  const doubled = counter.map((value) => value * 2);
  const seen: number[] = [];
  const subscription = doubled.observe((value) => seen.push(value));
  counter.set(1);
  counter.set(4);
  subscription.dispose();
  counter.set(9);
  console.log(`observed: ${seen.join(", ")} (expected 0, 2, 8)`);
}

main().catch((error: unknown) => {
  console.error(error);
  // No process access here on purpose: the demo runs unchanged in Node and in a
  // browser bundle. An unhandled rejection is enough to fail a CI step.
  throw error;
});
