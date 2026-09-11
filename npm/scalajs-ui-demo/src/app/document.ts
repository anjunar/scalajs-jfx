import { serverPreferences } from "@anjunar/scalajs-ui/preferences";
/**
 * The whole document, `<html>` included -- mirrors
 * `scalajs-ui-demo/src/main/scala-3/app/AppDocument.scala`. The demo has no
 * `index.html` any more: everything a page needs is rendered here (the
 * doctype aside, which the caller prepends), so a route describes itself
 * through `documentHead()` instead of inheriting one build-time head from a
 * template.
 *
 * No enclosing `html(...)` here, on purpose: this is only ever composed as
 * the top-level `build` passed to `renderToString(build, { document: true })`
 * or `hydrate(document, build)`, both of which already mount a real `<html>`
 * root for it (see `BridgeRoot`'s doc comment in `scalajs-ui-bridge` for why a
 * virtual one cannot stand for `<html>`) -- composing another one here would
 * double it up.
 *
 * `assets` is the one exception: the built bundle's script/stylesheet tags
 * carry a content hash only the bundler knows, so they arrive as an
 * argument, the way `entry-server.ts`'s `path` does, and become ordinary
 * head entries -- see `Main.render`'s `clientAssets` on the Scala side.
 */
import { attr, div, element, isBrowser, documentHead, type HeadEntry } from "@anjunar/scalajs-ui-core";
import { appHead } from "./head.js";

const body = element("body");

export function appDocument(assets: readonly HeadEntry[], bodyContent: () => void, url = "/"): void {
  if (!isBrowser()) {
    const state = serverPreferences(url);
    documentHead()?.htmlAttribute("data-design", state.design);
    documentHead()?.htmlAttribute("data-color-scheme", state.colorScheme);
  }
  appHead(assets);

  body(() => {
    div(() => {
      attr("id", "root");
      bodyContent();
    });
  });
}
