/**
 * Everything the demo puts into the document `<head>` that isn't per-page --
 * ported from `application/src/main/scala-3/app/AppHead.scala`'s
 * `siteEntries`/`themeInitScript`, minus the SEO fields (Open Graph, JSON-LD,
 * hreflang alternates, canonical URL) that Scala file also carries: those are
 * driven by a `SiteConfig`/i18n setup this demo doesn't have. Per-page
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
import { charset, disposeWith, documentHead, head, type HeadEntry, inlineScript, link, meta, title } from "@anjunar/jfx-core";

/** Runs before anything paints so the first frame already carries the right
 * `data-theme` -- otherwise the page would flash light before a stored
 * "dark" preference (or the client's own toggle in theme.ts) caught up.
 * Verbatim from the inline script `index.html` used to carry. */
const THEME_INIT_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem("jfx-demo.theme");
    var mode =
      stored === "light" || stored === "dark"
        ? stored
        : window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches
          ? "dark"
          : "light";
    document.documentElement.setAttribute("data-theme", mode);
  } catch (error) {
    /* localStorage can throw in a locked-down browser context; the CSS
       default (light) is a fine fallback. */
  }
})();
`;

export function appHead(assets: readonly HeadEntry[] = []): void {
  head(() => {
    const documentHeadHandle = documentHead();
    if (documentHeadHandle === null) return;

    documentHeadHandle.htmlAttribute("lang", "en");

    disposeWith(documentHeadHandle.push(charset()));
    disposeWith(documentHeadHandle.push(title("@anjunar/jfx demo")));
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
