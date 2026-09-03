// Same-real-path note as entry-client.ts: imported the way pages.ts itself
// imports "@anjunar/jfx", not through the node_modules symlink.
import { installRuntime, renderToString } from "../../jfx/src/index.js";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { pageFor } from "./routes.js";

installRuntime(bridgeRuntime);

export async function render(path: string): Promise<{ html: string; status: number }> {
  const result = await renderToString(pageFor(path));
  return { html: result.html, status: result.status };
}
