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

describe("form + editor", () => {
  it("imports the initial Markdown value into the live Lexical surface", () => {
    const model = { body: property("## Hello **world**") };
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
    expect(model.body.get).toBe("## Hello **world**");

    app.dispose();
  });

  it("reflects an external model update in the live surface", () => {
    const model = { body: property("Hello world") };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", {});
        });
      });
    });

    model.body.set("### Updated text");

    const surface = root.querySelector(".jfx-editor__surface");
    expect(surface?.textContent).toBe("Updated text");

    app.dispose();
  });

  it("renders and hydrates, matching the SSR preview in the live surface", async () => {
    const model = { body: property("Hello **world**") };
    const build = (): void => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", { plugins: ["base"] });
        });
      });
    };

    const rendered = await renderToString(build);
    expect(rendered.html).toContain("<textarea");
    expect(rendered.html).toContain("Hello **world**");

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    // Hydration first claims the textarea emitted by SSR. `afterCompose` then
    // progressively enhances it to Lexical without changing the Markdown
    // value stored in the form model.
    const app = await hydrate(root, build);

    const surface = root.querySelector(".jfx-editor__surface");
    expect(surface?.textContent).toBe("Hello world");

    app.dispose();
  });

  it("round-trips the project nodes through the public Markdown value", () => {
    const markdown = [
      "# Article",
      "",
      "![Preview](https://example.test/image.png){width=42}",
      "",
      "| Name | Value |",
      "| --- | --- |",
      "| **answer** | [source](https://example.test) |",
      "",
      "```scala",
      "val answer = 42",
      "```",
      "",
      "---",
    ].join("\n");
    const model = { body: property(markdown) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        form(model, {}, () => {
          editor("body", {
            plugins: ["image", "table", "code", "horizontalRule"],
          });
        });
      });
    });

    expect(root.querySelector("img")?.getAttribute("style")).toContain("max-width: 42px");
    expect(root.querySelector("table")).not.toBeNull();
    expect(root.querySelector(".codemirror-container")).not.toBeNull();
    expect(root.querySelector("hr")).not.toBeNull();
    expect(model.body.get).toBe(markdown);

    model.body.set("![Updated](https://example.test/updated.png){width=17}");
    expect(root.querySelector("img")?.getAttribute("alt")).toBe("Updated");
    expect(root.querySelector("img")?.getAttribute("style")).toContain("max-width: 17px");

    app.dispose();
  });

  it("renders semantic readonly Markdown and an edit URL during SSR", async () => {
    const rendered = await renderToString(() => {
      viewport(() => {
        editor("body", {
          standalone: true,
          value: "## Hello **world** ++underlined++ ==marked==",
          editable: false,
        });
      });
    });

    expect(rendered.html).toContain('<h2 class="lexical-heading-h2">');
    expect(rendered.html).toContain("<strong>world</strong>");
    expect(rendered.html).toContain("<u>underlined</u>");
    expect(rendered.html).toContain("<mark>marked</mark>");
    expect(rendered.html).toContain('href="?body.editor=editable"');
    expect(rendered.html).not.toContain("<textarea");
  });

  it("renders the Markdown value in a textarea during editable SSR", async () => {
    const rendered = await renderToString(() => {
      viewport(() => {
        editor("body", {
          standalone: true,
          value: "## Hello **world**",
          editable: true,
        });
      });
    });

    expect(rendered.html).toContain("<textarea");
    expect(rendered.html).toContain("## Hello **world**");
    expect(rendered.html).toContain('href="?body.editor=readonly"');
    expect(rendered.html).not.toContain("<h2");
  });

  it("does not create executable links or images in the live surface", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value:
            "[bad](javascript:alert(1)) [also-bad](data:text/html;base64,PHNjcmlwdD4=) " +
            "![bad](data:image/svg+xml;base64,PHN2Zz4=)",
        })
      );
    });

    expect(root.querySelector('a[href^="javascript:"]')).toBeNull();
    expect(root.querySelector('a[href^="data:"]')).toBeNull();
    expect(root.querySelector('img[src^="data:"]')).toBeNull();
    expect(root.textContent).toContain("bad");

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
