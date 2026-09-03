/**
 * Renders `pages.ts` -- the exact same `statePage`/`libraryPage` functions
 * `statePage.ts` renders against the stub -- against the real Scala.js bridge
 * instead. Only the two lines below `main()`'s `installRuntime` call differ from
 * `statePage.ts`; everything the page functions themselves do is unchanged
 * because [[Reactive]], `ScopeHandle` et al. are the same TypeScript contract
 * either way (JAVASCRIPT_API.md §2 -- a facade, not a second implementation).
 *
 * Run with `npm run demo:bridge`. Needs `@anjunar/scalajs-jfx-bridge` linked --
 * `sbtn "scalajs-jfx-bridge/fastLinkJS"` from the repo root, then `npm install`
 * here so the `file:` devDependency picks up the freshly linked package.
 */
import { installRuntime, property, renderToString } from "../src/index.js";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { format, libraryPage, statePage } from "./pages.js";

async function main(): Promise<void> {
  installRuntime(bridgeRuntime);

  const state = await renderToString(statePage);
  console.log("--- StatePage (real Scala.js bridge) --------------------------");
  console.log(format(state.html));

  const library = await renderToString(libraryPage);
  console.log("\n--- LibraryPage (async loader drained before serialising) ----");
  console.log(format(library.html));

  console.log("\n--- Reactivity check (through PropertyHandle, not the stub) --");
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
  throw error;
});
