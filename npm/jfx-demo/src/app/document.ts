/**
 * The whole document, `<html>` included -- mirrors
 * `application/src/main/scala-3/app/AppDocument.scala`. The demo has no
 * `index.html` any more: everything a page needs is rendered here (the
 * doctype aside, which the caller prepends), so a route describes itself
 * through `documentHead()` instead of inheriting one build-time head from a
 * template.
 *
 * No enclosing `html(...)` here, on purpose: this is only ever composed as
 * the top-level `build` passed to `renderToString(build, { document: true })`
 * or `hydrate(document, build)`, both of which already mount a real `<html>`
 * root for it (see `BridgeRoot`'s doc comment in `jfx-bridge` for why a
 * virtual one cannot stand for `<html>`) -- composing another one here would
 * double it up.
 *
 * `assets` is the one exception: the built bundle's script/stylesheet tags
 * carry a content hash only the bundler knows, so they arrive as an
 * argument, the way `entry-server.ts`'s `path` does, and become ordinary
 * head entries -- see `Main.render`'s `clientAssets` on the Scala side.
 */
import { attr, div, element, type HeadEntry } from "@anjunar/jfx-core";
import { appHead } from "./head.js";

const body = element("body");

export function appDocument(assets: readonly HeadEntry[], bodyContent: () => void): void {
  appHead(assets);

  body(() => {
    div(() => {
      attr("id", "root");
      bodyContent();
    });
  });
}
