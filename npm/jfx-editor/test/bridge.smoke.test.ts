/**
 * Smoke test against the real bridge.
 *
 * Form binding needs the real bridge's `Property` handle (`PropertyHandle`)
 * to find the underlying Scala `Property`, which the stub runtime does not
 * build -- so, like the controls, viewport and forms facades, there is no
 * stub half here.
 *
 * It needs the linked artifact:
 *
 *     sbtn "scalajs-jfx-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 *
 * Lexical itself mounts and runs fine under jsdom (verified while building
 * this suite) -- so, unlike a first guess based on "jsdom lacks
 * Selection/Range", these tests exercise the *real* Lexical surface, not
 * just the SSR preview. What is not exercised here is a real keystroke:
 * simulating actual typing needs Selection/Range editing behavior jsdom does
 * not implement, so the "internal edit -> model" direction is proven by
 * manual testing against the running demo instead, the same "jsdom is not
 * enough on its own" lesson the viewport and forms facades' own hydration
 * bugs already taught this project.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  hydrate,
  installRuntime,
  mount,
  property,
  renderToString,
  resetRuntime,
  runtime,
} from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { form } from "@anjunar/jfx-forms";
import { viewport } from "@anjunar/jfx-viewport";
import { editor } from "../src/index.js";

const linkedArtifact = resolve(process.cwd(), "../scalajs-jfx-bridge/dist/fullopt/main.js");

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbtn "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
});

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });
});

// A minimal, hand-built Lexical `EditorState` JSON document -- one paragraph,
// one text run. Every field here is one `importJSON` on the matching Lexical
// node type actually reads; this is not the semantic preview shape
// `EditorPreview` (in `jfx-editor`) invents for SSR, it is what the real
// Lexical runtime `editor.setEditorState` expects.
function paragraphDocument(text: string): unknown {
  return {
    root: {
      type: "root",
      version: 1,
      indent: 0,
      format: "",
      direction: null,
      children: [
        {
          type: "paragraph",
          version: 1,
          indent: 0,
          format: "",
          direction: null,
          children: [
            { type: "text", version: 1, format: 0, mode: "normal", detail: 0, style: "", text },
          ],
        },
      ],
    },
  };
}

describe("form + editor", () => {
  it("mounts the initial model value into the live Lexical surface", () => {
    const model = { body: property<unknown>(paragraphDocument("Hello world")) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", { plugins: ["base"] });
        });
      });
    });

    const surface = root.querySelector(".jfx-editor__surface");
    expect(surface?.textContent).toBe("Hello world");

    app.dispose();
  });

  it("reflects an external model update in the live surface", () => {
    const model = { body: property<unknown>(paragraphDocument("Hello world")) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", {});
        });
      });
    });

    model.body.set(paragraphDocument("Updated text"));

    const surface = root.querySelector(".jfx-editor__surface");
    expect(surface?.textContent).toBe("Updated text");

    app.dispose();
  });

  it("renders and hydrates, matching the SSR preview in the live surface", async () => {
    const model = { body: property<unknown>(paragraphDocument("Hello world")) };
    const build = (): void => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", { plugins: ["base"] });
        });
      });
    };

    const rendered = await renderToString(build);
    expect(rendered.html).toContain("Hello world");

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    // Regression: `registerWithForm()` used to run after the tree containing
    // `dynamic(valueProperty.map(...))` (the SSR preview) was already built,
    // so that first render saw the constructor's `Property(null)` instead of
    // the value bound from the model. A live `mount()`/SSR self-heals
    // because the resulting reactive `replace()` still lands inside the same
    // synchronous compose call, but `HydratingCursor` claims once per
    // position against the *settled* SSR markup, and threw "Server-rendered
    // nodes were not claimed by the client component tree." Fixed by binding
    // before building the tree (`Editor.compose`), the same "no property a
    // dynamic()/when() branch reads may change after that branch already
    // rendered once" shape as Lauf 5's window/notification fix and Lauf 6's
    // `ArrayForm` renderer fix.
    const app = await hydrate(root, build);

    const surface = root.querySelector(".jfx-editor__surface");
    expect(surface?.textContent).toBe("Hello world");

    app.dispose();
  });
});

describe("standalone", () => {
  it("mounts without a form context and needs no form binding", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        editor("scratch", { standalone: true, plugins: ["base"] });
      });
    });

    expect(root.querySelector(".jfx-editor-host")).not.toBeNull();

    app.dispose();
  });
});

describe("plugins and toolbarMode", () => {
  it("renders no toolbar buttons with no plugins", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => editor("x", { standalone: true, plugins: [] }));
    });

    expect(root.querySelector(".jfx-editor__toolbar")?.innerHTML ?? "").toBe("");

    app.dispose();
  });

  it("renders the base plugin's buttons in ribbon mode by default", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => editor("x", { standalone: true, plugins: ["base"] }));
    });

    expect(root.querySelector(".lexical-ribbon-wrapper")).not.toBeNull();
    expect(root.querySelector('[title="Bold"]')).not.toBeNull();

    app.dispose();
  });

  it("renders a menu bar instead of a ribbon when toolbarMode is menu", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => editor("x", { standalone: true, plugins: ["base"], toolbarMode: "menu" }));
    });

    expect(root.querySelector(".lexical-menu-bar")).not.toBeNull();
    expect(root.querySelector(".lexical-ribbon-wrapper")).toBeNull();

    app.dispose();
  });
});

describe("placeholder", () => {
  it("shows the placeholder while the value is empty", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => editor("x", { standalone: true, placeholder: "Write here" }));
    });

    expect(root.querySelector(".jfx-editor__placeholder")?.textContent).toBe("Write here");

    app.dispose();
  });
});
