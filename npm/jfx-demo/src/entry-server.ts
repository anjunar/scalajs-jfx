// By package specifier, like entry-client.ts -- see the note there and
// vite.config.ts's `resolve.dedupe`, which is what makes it safe.
import { installRuntime, renderToString } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { pageFor } from "./routes.js";

installRuntime(bridgeRuntime);

export async function render(path: string): Promise<{ html: string; status: number }> {
  const result = await renderToString(pageFor(path));
  return { html: result.html, status: result.status };
}
