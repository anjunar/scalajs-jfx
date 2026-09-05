/**
 * Renders every page that needs the real Scala.js bridge -- everything under
 * controls/, forms/ and viewport/, plus the six @anjunar/jfx-core pages
 * node/stub.ts also renders. Run with `npm run demo:bridge`. Needs
 * @anjunar/scalajs-jfx-bridge linked -- `sbt --server "scalajs-jfx-bridge/fullLinkJS"`
 * from the repo root.
 *
 * It uses the build-safe page manifest rather than `app/catalog.ts`: every
 * doc.ts closes over a `?jfx-code` import that only resolves inside Vite's
 * module graph. Router pages are verified through the mounted app and
 * `verify:pages`, because a bare renderToString has no router context.
 */
import { property, renderToString } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";
import { format } from "./format.js";
import { pageManifest } from "../app/page-manifest.js";

async function render(label: string, body: () => void, wrap?: (body: () => void) => void): Promise<void> {
  const result = await renderToString(() => (wrap === undefined ? body() : wrap(body)));
  console.log(`\n--- ${label} ---`);
  console.log(format(result.html));
}

async function main(): Promise<void> {
  for (const page of pageManifest) {
    await render(page.title, page.render, page.wrap);
  }

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
