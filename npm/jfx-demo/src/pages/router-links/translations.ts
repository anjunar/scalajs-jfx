import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`routerLink`, { de: "routerLink" }),
  catalogEntry(i18n`A navigating anchor with an activeClass -- a real <a href>, so navigation works before any JavaScript runs.`, { de: "Ein navigierender Anchor mit activeClass – ein echtes <a href>, daher funktioniert Navigation ohne JavaScript." }),
  catalogEntry(i18n`A navigating anchor with an activeClass -- a real <a href> either way, so navigation works before any JavaScript runs.`, { de: "Ein navigierender Anchor mit activeClass – ein echtes <a href>, daher funktioniert Navigation ohne JavaScript." }),
  catalogEntry(i18n`This page`, { de: "Diese Seite" }),
  catalogEntry(i18n`A broken link (still works without JS)`, { de: "Ein defekter Link (funktioniert auch ohne JS)" }),
  catalogEntry(i18n`RouterConfig also takes a `, { de: "RouterConfig akzeptiert auch ein " }),
  catalogEntry(i18n`basePath`, { de: "basePath" }),
  catalogEntry(i18n` -- every route and routerLink resolves under it, for mounting the whole app under a URL prefix (a reverse proxy path, say). Not exercised live here: this demo's own routes, nav links and search index all assume no prefix, and setting one would mean rewriting every hardcoded path in this project just to prove the option exists. The option itself is unchanged since CLAUDE_REVIEW_3.md -- see RouterConfig in @anjunar/jfx-router's router.ts.`, { de: " – jede Route und jeder routerLink wird darunter aufgelöst, um die gesamte Anwendung unter einem URL-Präfix (etwa einem Reverse-Proxy-Pfad) zu mounten. Hier wird das nicht live verwendet: Die Routen, Navigationslinks und der Suchindex dieser Demo gehen von keinem Präfix aus. Ein Präfix würde das Umschreiben aller hartcodierten Pfade erfordern, nur um die Option zu demonstrieren. Die Option ist seit CLAUDE_REVIEW_3.md unverändert – siehe RouterConfig in router.ts von @anjunar/jfx-router." }),
];
