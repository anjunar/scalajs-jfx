// The only thing standing in for a router: pick a page function by request
// path. There is no client-side navigation here, no Router component, no
// route table -- jfx-bridge doesn't wire up jfx-router yet (JAVASCRIPT_API.md
// §9, step 5). `pageNav()` in pages.ts links between these with plain
// `<a href>`s, so every navigation is a full page load, and the server and
// the client have to agree on the same page for the same path or hydration
// faults (server rendered X, client tries to claim it as Y).
import { libraryPage, statePage } from "./pages.js";

export function pageFor(path: string): () => void {
  return path === "/library" ? libraryPage : statePage;
}
