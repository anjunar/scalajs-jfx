/**
 * Smoke test against the real bridge.
 *
 * There is no stub half here: the stub runtime knows nothing about a global UI
 * layer, so the viewport facade can only be exercised against the linked
 * Scala.js bundle. This file asserts what step 7 of JAVASCRIPT_API.md §9
 * promised: `viewport`, `window`, `overlay` and `notification` mount, render
 * server-side, and hydrate the server tree with node identity.
 *
 * It needs the linked artifact:
 *
 *     sbt --server "scalajs-jfx-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  button,
  div,
  hydrate,
  installRuntime,
  mount,
  onClick,
  property,
  renderToString,
  resetRuntime,
  runtime,
  text,
  when,
} from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { floatingWindow, notify, overlay, viewport } from "../src/index.js";

const linkedArtifact = resolve(process.cwd(), "../scalajs-jfx-bridge/dist/fullopt/main.js");

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbt --server "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
});

function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });
});

describe("viewport", () => {
  it("mounts as the host and renders its body", async () => {
    const build = (): void => {
      viewport(() => {
        div(() => text("page content"));
      });
    };

    const result = await renderToString(build);
    expect(withoutAnchors(result.html)).toContain("jfx-viewport");
    expect(withoutAnchors(result.html)).toContain("page content");
  });
});

describe("notify", () => {
  it("renders the message under its kind class", async () => {
    const build = (): void => {
      viewport(() => {
        notify("Saved.", { kind: "success" });
      });
    };

    const result = await renderToString(build);
    const html = withoutAnchors(result.html);
    expect(html).toContain("jfx-viewport-notification--success");
    expect(html).toContain("Saved.");
  });

  it("defaults to the info kind", async () => {
    const build = (): void => {
      viewport(() => {
        notify("Heads up.");
      });
    };

    const result = await renderToString(build);
    expect(withoutAnchors(result.html)).toContain("jfx-viewport-notification--info");
  });

  // Regression: `notify` called straight from a bare `onClick` (no `when()` gate
  // between it and the button) after *hydration* -- not `mount` -- used to throw
  // "Hydration fault: There is no further DOM node." `ScopeHandleBridge.cursor`
  // is captured once, at the point `on(...)` registers the handler, and replayed
  // verbatim when the handler fires; with no reactive gate in between to hand
  // out a fresh one, that replayed cursor was still the (by then spent)
  // `HydratingCursor`. See `ViewportFactories.scala`'s file-level doc comment
  // for the fix: `NotificationFactory`/`WindowFactory` no longer touch the call
  // site's cursor at all.
  it("fires from a bare onClick after hydration, with no reactive gate", async () => {
    const build = (): void => {
      viewport(() => {
        button("Notify", {}, () => {
          onClick(() => notify("Saved.", { kind: "success" }));
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, build);

    const trigger = root.querySelector("button");
    (trigger as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.querySelector(".jfx-viewport-notification--success")).not.toBeNull();
    expect(root.textContent).toContain("Saved.");
    app.dispose();
  });
});

describe("floatingWindow", () => {
  const openFlagBuild = (): { open: ReturnType<typeof property<boolean>>; build: () => void } => {
    const open = property(false);
    const build = (): void => {
      viewport(() => {
        button("Open", {}, () => {
          onClick(() => open.set(true));
        });
        when(open, () => {
          floatingWindow({ title: "A room for thoughts", widthPx: 400, heightPx: 300 }, () => {
            div(() => text("window body"));
          });
        });
      });
    };
    return { open, build };
  };

  // Same regression as `notify`'s bare-onClick test above, for the other
  // factory that skips the call site's cursor.
  it("opens from a bare onClick after hydration, with no reactive gate", async () => {
    const build = (): void => {
      viewport(() => {
        button("Open", {}, () => {
          onClick(() => floatingWindow({ title: "Direct from a click" }, () => {
            div(() => text("no when() involved"));
          }));
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, build);

    const trigger = root.querySelector("button");
    (trigger as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.querySelector(".jfx-window")).not.toBeNull();
    expect(root.textContent).toContain("Direct from a click");
    expect(root.textContent).toContain("no when() involved");
    app.dispose();
  });

  it("stays closed until its condition flips true, in a real browser mount", () => {
    const { build } = openFlagBuild();
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, build);
    expect(root.querySelector(".jfx-window")).toBeNull();

    const trigger = root.querySelector("button");
    (trigger as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.querySelector(".jfx-window")).not.toBeNull();
    expect(root.textContent).toContain("A room for thoughts");
    expect(root.textContent).toContain("window body");
    app.dispose();
  });

  // A window present (open) at the moment of the *initial* render is not a
  // supported starting state: `Viewport.compose` sets up `Foreach.foreach(windows)`
  // before `renderInto(contentHost) { body }` runs, so hydration walks past that
  // Foreach's anchors -- expecting zero items -- before the declarative body that
  // would register one has had a chance to run. This is not new to this facade:
  // it is exactly why `jfx.forms.ComboBox`'s own overlay starts from
  // `Property(false)` and only ever opens from a click, never from the initial
  // render (see `ComboBox.openProperty`). The supported shape, exercised below,
  // is the one every existing consumer of `jfx.viewport` already uses: closed at
  // hydration, opened by a subsequent interaction.
  it("opens after hydration completes, without disturbing the already-hydrated tree", async () => {
    const open = property(false);
    const build = (): void => {
      viewport(() => {
        button("Open", {}, () => {
          onClick(() => open.set(true));
        });
        when(open, () => {
          floatingWindow({ title: "Hydrated window" }, () => {
            div(() => text("hydrated body"));
          });
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector(".jfx-viewport");
    expect(before).not.toBeNull();
    expect(root.querySelector(".jfx-window")).toBeNull();

    const app = await hydrate(root, build);
    expect(root.querySelector(".jfx-viewport")).toBe(before);

    const trigger = root.querySelector("button");
    (trigger as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.querySelector(".jfx-window")).not.toBeNull();
    expect(root.textContent).toContain("hydrated body");
    app.dispose();
  });

  it("closes when onClose fires from the chrome's close button", () => {
    let closed = false;
    const open = property(true);
    const build = (): void => {
      viewport(() => {
        when(open, () => {
          floatingWindow({ title: "Closable", onClose: () => (closed = true) }, () => {
            div(() => text("body"));
          });
        });
      });
    };

    const root = document.createElement("div");
    document.body.appendChild(root);
    const app = mount(root, build);

    const closeButton = root.querySelector(".jfx-window__chrome-button");
    expect(closeButton).not.toBeNull();
    (closeButton as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(closed).toBe(true);
    app.dispose();
  });
});

describe("overlay", () => {
  it("renders its body anchored under the viewport", async () => {
    const build = (): void => {
      viewport(() => {
        div(() => {
          overlay({ widthPx: 240 }, () => {
            text("overlay body");
          });
        });
      });
    };

    const result = await renderToString(build);
    const html = withoutAnchors(result.html);
    expect(html).toContain("jfx-viewport-overlay");
    expect(html).toContain("overlay body");
  });

  // Same constraint as `floatingWindow`'s hydration test above: an overlay
  // present at the *initial* render is not a supported starting state. This
  // matches `jfx.forms.ComboBox`, the one existing consumer of `Overlay.overlay`
  // -- its dropdown is always closed (`Property(false)`) until a click.
  it("opens after hydration completes, without disturbing the already-hydrated tree", async () => {
    const open = property(false);
    const build = (): void => {
      viewport(() => {
        button("Open", {}, () => {
          onClick(() => open.set(true));
        });
        div(() => {
          when(open, () => {
            overlay({}, () => {
              text("menu");
            });
          });
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector(".jfx-viewport");
    expect(before).not.toBeNull();
    expect(root.querySelector(".jfx-viewport-overlay")).toBeNull();

    const app = await hydrate(root, build);
    expect(root.querySelector(".jfx-viewport")).toBe(before);

    const trigger = root.querySelector("button");
    (trigger as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.querySelector(".jfx-viewport-overlay")).not.toBeNull();
    expect(root.textContent).toContain("menu");
    app.dispose();
  });
});
