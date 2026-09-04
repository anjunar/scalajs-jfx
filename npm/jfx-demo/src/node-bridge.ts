/**
 * Renders `pages.ts` -- the exact same `statePage`/`libraryPage` functions
 * `node-stub.ts` renders against the stub -- against the real Scala.js bridge
 * instead. Only the two lines below `main()`'s `installRuntime` call differ from
 * `node-stub.ts`; everything the page functions themselves do is unchanged
 * because [[Reactive]], `ScopeHandle` et al. are the same TypeScript contract
 * either way (JAVASCRIPT_API.md §2 -- a facade, not a second implementation).
 *
 * Run with `npm run demo:bridge`. Needs `@anjunar/scalajs-jfx-bridge` linked --
 * `sbtn "scalajs-jfx-bridge/fullLinkJS"` from the repo root. No `npm install`
 * needed afterwards: npm workspaces already symlink this package by directory,
 * so a freshly linked `dist/` is picked up without reinstalling.
 */
import { installRuntime, property, renderToString } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { viewport } from "@anjunar/jfx-viewport";
import { controlsPage, format, formsPage, libraryPage, statePage, viewportPage } from "./pages.js";

async function main(): Promise<void> {
  installRuntime(bridgeRuntime);

  const state = await renderToString(statePage);
  console.log("--- StatePage (real Scala.js bridge) --------------------------");
  console.log(format(state.html));

  const library = await renderToString(libraryPage);
  console.log("\n--- LibraryPage (async loader drained before serialising) ----");
  console.log(format(library.html));

  const controls = await renderToString(controlsPage);
  console.log("\n--- ControlsPage (tabs + table + carousel, @anjunar/jfx-controls) ---");
  console.log(format(controls.html));

  // `viewportPage` needs a `viewport` ancestor to reach `Viewport.requireCurrent`
  // through `notify`/`floatingWindow`/`overlay` -- see entry-client.ts's note.
  const viewportResult = await renderToString(() => viewport(viewportPage));
  console.log("\n--- ViewportPage (notification + window + overlay, @anjunar/jfx-viewport) ---");
  console.log(format(viewportResult.html));

  // `formsPage` also needs a `viewport` ancestor -- its combo box's dropdown
  // is an `@anjunar/jfx-viewport` overlay.
  const formsResult = await renderToString(() => viewport(formsPage));
  console.log("\n--- FormsPage (validated inputs + array/sub-form + combo box + image cropper, @anjunar/jfx-forms) ---");
  console.log(format(formsResult.html));

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
