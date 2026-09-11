import registry from "./designs/registry.json" with { type: "json" };

export const designs = Object.freeze(registry.designs.map(({ id, name, description }) => Object.freeze({ id, name, description })));
export const defaultDesign = registry.defaultDesign;
export const defaultColorScheme = registry.defaultColorScheme;
export const storageKeys = Object.freeze({ design: "ui.design", colorScheme: "ui.color-scheme" });
const validDesign = (value) => designs.some((design) => design.id === value) ? value : defaultDesign;
const validScheme = (value) => value === "light" || value === "dark" ? value : defaultColorScheme;

/** URL previews are page-local and never write storage. No browser globals are read by SSR. */
export function serverPreferences(url = "/") {
  const params = new URL(url, "http://ui.local").searchParams;
  return { design: validDesign(params.get("design")), colorScheme: validScheme(params.get("colorScheme")), storageAvailable: true };
}

/** Literal source keeps the head script identical in minified client and SSR builds. */
export function bootstrapScript(legacyKey) {
  const config = JSON.stringify({ ids: designs.map((design) => design.id), defaultDesign, defaultColorScheme, keys: storageKeys, legacyKey }).replaceAll("<", "\\u003c");
  return `/* ui-preferences */\n(function (config) {
    var root = document.documentElement;
    if (root.getAttribute('data-preference-source') === 'server') return;
    var params = new URL(window.location.href).searchParams;
    var storedDesign = null, storedScheme = null;
    try { storedDesign = window.localStorage.getItem(config.keys.design); } catch (_) {}
    try {
      storedScheme = window.localStorage.getItem(config.keys.colorScheme);
      if (storedScheme === null) storedScheme = window.localStorage.getItem(config.legacyKey);
    } catch (_) {}
    var design = params.has('design') ? params.get('design') : storedDesign;
    var scheme = params.has('colorScheme') ? params.get('colorScheme') : storedScheme;
    root.setAttribute('data-design', config.ids.indexOf(design) >= 0 ? design : config.defaultDesign);
    root.setAttribute('data-color-scheme', scheme === 'light' || scheme === 'dark' ? scheme : config.defaultColorScheme);
  })(${config});`;
}

/** One owner per mounted app. Only document attributes and preferences change, never UI content. */
export function createPreferences(legacyKey, url = "/", environment = typeof window === "undefined" ? null : window) {
  const win = environment;
  const root = win?.document.documentElement;
  let state = root ? {
    design: validDesign(root.getAttribute("data-design")),
    colorScheme: validScheme(root.getAttribute("data-color-scheme")),
    storageAvailable: true,
  } : serverPreferences(url);
  const listeners = new Set();
  const emit = () => { for (const listener of listeners) listener({ ...state }); };
  const normalize = { design: validDesign, colorScheme: validScheme };
  const attributes = { design: "data-design", colorScheme: "data-color-scheme" };

  function apply(axis, value) {
    state = { ...state, [axis]: normalize[axis](value) };
    root?.setAttribute(attributes[axis], state[axis]);
  }

  function set(axis, value) {
    apply(axis, value);
    if (win) {
      try {
        win.localStorage.setItem(storageKeys[axis], state[axis]);
        state.storageAvailable = true;
      } catch (_) { state.storageAvailable = false; }
      const location = new URL(win.location.href);
      if (location.searchParams.has(axis)) {
        location.searchParams.set(axis, state[axis]);
        win.history.replaceState(win.history.state, "", location.href);
      }
    }
    emit();
  }

  function onStorage(event) {
    const params = new URL(win.location.href).searchParams;
    for (const axis of ["design", "colorScheme"]) {
      if ((event.key === storageKeys[axis] || event.key === null) && !params.has(axis)) {
        apply(axis, event.key === null ? null : event.newValue);
      }
    }
    emit();
  }

  return {
    getState: () => ({ ...state }),
    setDesign: (value) => set("design", value),
    setColorScheme: (value) => set("colorScheme", value),
    subscribe(listener) {
      if (listeners.size === 0) win?.addEventListener("storage", onStorage);
      listeners.add(listener);
      listener({ ...state });
      return () => {
        listeners.delete(listener);
        if (listeners.size === 0) win?.removeEventListener("storage", onStorage);
      };
    },
  };
}
