/**
 * The light/dark toggle in the shell. `index.html`'s inline script already
 * set `data-theme` on `<html>` before anything rendered (from `localStorage`,
 * key `jfx-demo.theme`, else `prefers-color-scheme`) -- this module is only
 * the reactive side the shell's button binds to.
 *
 * The property is created with the *same* default ("light") on the server
 * and on the client's first render pass, so the button's label can never
 * disagree between SSR and hydration -- see CLAUDE_DEMO_PLAN.md E-7.
 * `syncFromDocument()` reconciles it with whatever the inline script already
 * resolved, but only after `hydrate()` in entry-client.ts has settled: a
 * property change at that point is an ordinary update, not a hydration
 * conflict, the same reasoning as `hydratedProperty()` in app/hydrated.ts.
 */
import { property } from "@anjunar/jfx-core";
import type { Property } from "@anjunar/jfx-core";

export type ThemeMode = "light" | "dark";

const STORAGE_KEY = "jfx-demo.theme";

let instance: Property<ThemeMode> | null = null;

export function themeProperty(): Property<ThemeMode> {
  return (instance ??= property<ThemeMode>("light"));
}

/** Reads the value entry-server.ts's/index.html's inline script already applied. */
export function syncThemeFromDocument(): void {
  try {
    const attribute = document.documentElement.getAttribute("data-theme");
    if (attribute === "light" || attribute === "dark") themeProperty().set(attribute);
  } catch {
    /* matches index.html's own guard around a locked-down localStorage/DOM. */
  }
}

export function toggleTheme(): void {
  const next: ThemeMode = themeProperty().get === "dark" ? "light" : "dark";
  themeProperty().set(next);
  try {
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    /* same guard as above -- the property itself is already updated. */
  }
}
