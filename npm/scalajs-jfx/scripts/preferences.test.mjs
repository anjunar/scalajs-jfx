import { test } from "node:test";
import assert from "node:assert/strict";
import { runInNewContext } from "node:vm";
import { bootstrapScript, createPreferences, designs, serverPreferences, storageKeys } from "../preferences.js";
import { verifyThemeBootstrap } from "../../../tools/verify-theme.mjs";

function browser(url = "http://jfx.local/") {
  const attrs = new Map([["data-design", "atlas"], ["data-color-scheme", "light"]]);
  const stored = new Map();
  const events = new Set();
  const win = {
    document: { documentElement: { getAttribute: key => attrs.get(key) ?? null, setAttribute: (key, value) => attrs.set(key, value) } },
    location: { href: url },
    history: { state: { route: "retained" }, replaceState(state, _, href) { this.state = state; win.location.href = href; } },
    localStorage: { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) },
    addEventListener: (_, listener) => events.add(listener), removeEventListener: (_, listener) => events.delete(listener),
  };
  return { win, attrs, stored, events };
}

test("all four designs resolve on the server in both explicit schemes", () => {
  assert.equal(designs.length, 4);
  for (const design of designs) for (const scheme of ["light", "dark"]) {
    assert.deepEqual(serverPreferences(`/?design=${design.id}&colorScheme=${scheme}`), { design: design.id, colorScheme: scheme, storageAvailable: true });
  }
  assert.equal(serverPreferences("/?design=unknown&colorScheme=auto").design, "atlas");
  assert.equal(serverPreferences("/?colorScheme=auto").colorScheme, "light");
});

test("bootstrap validates stored preferences and never reads the system theme", () => {
  verifyThemeBootstrap(`<script>${bootstrapScript("legacy.theme")}</script>`);
});

test("preview overrides storage without writing it and respects authoritative server roots", () => {
  const { win, attrs, stored } = browser("http://jfx.local/?design=ember&colorScheme=dark");
  stored.set(storageKeys.design, "flora");
  const run = () => runInNewContext(bootstrapScript("legacy.theme"), { URL, window: win, document: win.document });
  run();
  assert.equal(attrs.get("data-design"), "ember");
  assert.equal(attrs.get("data-color-scheme"), "dark");
  assert.deepEqual([...stored], [[storageKeys.design, "flora"]]);
  attrs.set("data-preference-source", "server");
  attrs.set("data-design", "terra");
  run();
  assert.equal(attrs.get("data-design"), "terra");
});

test("manual choice writes one axis and preserves search, hash and history state", () => {
  const { win, attrs, stored } = browser("http://jfx.local/?design=atlas&q=text&page=2#sample");
  const preferences = createPreferences("legacy", "/", win);
  preferences.setDesign("flora");
  assert.equal(attrs.get("data-design"), "flora");
  assert.deepEqual([...stored], [[storageKeys.design, "flora"]]);
  assert.equal(win.location.href, "http://jfx.local/?design=flora&q=text&page=2#sample");
  assert.deepEqual(win.history.state, { route: "retained" });
  preferences.setColorScheme("dark");
  assert.equal(preferences.getState().design, "flora");
});

test("denied storage keeps the current page usable and reports the limitation", () => {
  const { win, attrs } = browser();
  win.localStorage.setItem = () => { throw Error("denied"); };
  const preferences = createPreferences("legacy", "/", win);
  preferences.setDesign("terra");
  assert.equal(attrs.get("data-design"), "terra");
  assert.equal(preferences.getState().storageAvailable, false);
});

test("storage synchronization preserves pinned previews and releases its listener", () => {
  const { win, events } = browser("http://jfx.local/?design=atlas");
  const preferences = createPreferences("legacy", "/", win);
  const unsubscribe = preferences.subscribe(() => {});
  for (const listener of events) {
    listener({ key: storageKeys.design, newValue: "flora" });
    listener({ key: storageKeys.colorScheme, newValue: "dark" });
  }
  assert.equal(preferences.getState().design, "atlas");
  assert.equal(preferences.getState().colorScheme, "dark");
  unsubscribe();
  assert.equal(events.size, 0);
});

test("SSR state belongs to one render, not the Node process", () => {
  const first = createPreferences("legacy", "/?design=flora", null);
  const second = createPreferences("legacy", "/", null);
  first.setColorScheme("dark");
  assert.deepEqual(second.getState(), { design: "atlas", colorScheme: "light", storageAvailable: true });
});
