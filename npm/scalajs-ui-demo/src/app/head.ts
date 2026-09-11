import { bootstrapScript } from "@anjunar/scalajs-ui/preferences";
/**
 * Everything the demo puts into the document `<head>` that isn't per-page --
 * ported from `scalajs-ui-demo/src/main/scala-3/app/AppHead.scala`'s
 * `siteEntries`/`themeInitScript`, minus the SEO fields (Open Graph, JSON-LD,
 * hreflang alternates, canonical URL) that Scala file also carries: those are
 * driven by a `SiteConfig` setup this demo doesn't have. Per-page
 * `<title>`/`meta[description]` are pushed from `../docs/page.ts` instead,
 * since a doc page -- not the shell -- is what knows them.
 *
 * Registered once, for the life of the document: unlike a doc page, this
 * composes exactly once per request/hydration, so a plain `push()` disposed
 * with the `<head>` element itself is enough -- no `Handle` needed. `assets`
 * (the built bundle's own script/stylesheet tags, see `app/document.ts`) are
 * disposed the same way, for the same reason -- and have to be pushed from
 * inside this `head()` call rather than `document.ts`'s top level, which has
 * no composing element of its own to tie their disposal to.
 */
import { base, charset, disposeWith, documentHead, head, type HeadEntry, inlineScript, link, locale, meta, title } from "@anjunar/scalajs-ui-core";
import { basePath } from "./base-path.js";

const THEME_INIT_SCRIPT = bootstrapScript("scalajs-ui-demo.theme");

export function appHead(assets: readonly HeadEntry[] = []): void {
  head(() => {
    const documentHeadHandle = documentHead();
    if (documentHeadHandle === null) return;

    const activeLocale = locale();
    documentHeadHandle.htmlAttribute("lang", activeLocale.get);
    disposeWith(activeLocale.observeWithoutInitial((code) => documentHeadHandle.htmlAttribute("lang", code)));

    disposeWith(documentHeadHandle.push(charset()));
    disposeWith(documentHeadHandle.push(base(basePath === "" ? "/" : `${basePath}/`)));
    disposeWith(documentHeadHandle.push(title("Scala JS UI 1.0 demo")));
    disposeWith(documentHeadHandle.push(meta("viewport", "width=device-width, initial-scale=1")));
    // The editor toolbar's buttons render Material Icons ligatures (e.g.
    // `<span class="material-icons">format_bold</span>`); without this font
    // linked they fall back to the literal icon name as text.
    disposeWith(
      documentHeadHandle.push(link("stylesheet", "https://fonts.googleapis.com/icon?family=Material+Icons"))
    );
    disposeWith(documentHeadHandle.push(inlineScript("theme-init", THEME_INIT_SCRIPT)));

    for (const asset of assets) disposeWith(documentHeadHandle.push(asset));
  });
}
