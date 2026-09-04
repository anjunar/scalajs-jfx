/**
 * Renders every /core/* page that needs only @anjunar/jfx-core against the
 * stub runtime, so the output can be compared against what the real bridge
 * produces (node/bridge.ts). Run with `npm run demo`.
 *
 * The build-safe page manifest deliberately contains only page bodies suitable
 * for a plain Node render. Interactive `/core/todos` and the app chrome stay
 * out of this proof; the docs routes are verified through the built server.
 */
import { installRuntime, property, renderToString } from "@anjunar/jfx-core";
import { stubRuntime } from "@anjunar/jfx-core/stub";
import { format } from "./format.js";
import { stubPages } from "../app/page-manifest.js";

async function render(label: string, body: () => void, wrap?: (body: () => void) => void): Promise<void> {
  const result = await renderToString(() => (wrap === undefined ? body() : wrap(body)));
  console.log(`\n--- ${label} ---`);
  console.log(format(result.html));
}

async function main(): Promise<void> {
  installRuntime(stubRuntime);

  for (const page of stubPages) {
    await render(page.title, page.render, page.wrap);
  }

  console.log("\n--- Reactivity check ---------------------------------------------");
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
