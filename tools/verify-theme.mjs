import assert from "node:assert/strict";
import { runInNewContext } from "node:vm";

/** Execute the actual SSR script; a dark OS preference must never affect the result. */
export function verifyThemeBootstrap(html) {
  const script = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)]
    .map(match => match[1]).find(body => body.includes("ui-preferences"));
  assert.ok(script, "SSR must include the preference bootstrap");
  for (const stored of [null, "light", "dark", "auto", "system", "invalid", new Error("storage denied")]) {
    const attrs = new Map();
    let systemReads = 0;
    const root = { getAttribute: key => attrs.get(key) ?? null, setAttribute: (key, value) => attrs.set(key, value) };
    runInNewContext(script, {
      URL, document: { documentElement: root },
      window: {
        location: { href: "http://ui.local/" },
        localStorage: { getItem(key) { if (stored instanceof Error) throw stored; return key === "ui.design" ? "flora" : stored; } },
        matchMedia() { systemReads++; return { matches: true }; },
      },
    });
    assert.equal(attrs.get("data-color-scheme"), stored === "light" ? "light" : "dark");
    assert.equal(attrs.get("data-design"), stored instanceof Error ? "ember" : "flora");
    assert.equal(systemReads, 0);
  }
}
