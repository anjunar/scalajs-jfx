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
 *     sbt --server "scalajs-jfx-bridge/fullLinkJS"
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
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
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
import { router, view } from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import { editor } from "../src/index.js";

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
  vi.restoreAllMocks();
  resetRuntime();
  installRuntime(bridgeRuntime);
  window.history.replaceState(null, "", "/");
});

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });
});

describe("form + editor", () => {
  it("switches URL modes through the router without reloading the page", () => {
    window.history.replaceState(null, "", "/article?body.editor=editable&lang=de");
    const pushState = vi.spyOn(window.history, "pushState");
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      router([
        view("/article", () => () => {
          viewport(() => {
            editor("body", {
              standalone: true,
              value: "## Stable Markdown",
              editable: false,
            });
          });
        }),
      ]);
    });

    expect(root.querySelector(".jfx-editor__surface")?.textContent).toBe("Stable Markdown");
    const readonlyLink = root.querySelector(".jfx-editor__readonly-link")!;
    readonlyLink.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));

    expect(pushState).toHaveBeenCalled();
    expect(window.location.pathname).toBe("/article");
    expect(window.location.search).toBe("?lang=de&body.editor=readonly");
    expect(root.querySelector("textarea")).toBeNull();
    expect(root.querySelector("h2")?.textContent).toBe("Stable Markdown");

    const editLink = root.querySelector(".jfx-editor__edit-link")!;
    editLink.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));

    expect(window.location.search).toBe("?lang=de&body.editor=editable");
    expect(root.querySelector(".jfx-editor__surface")?.textContent).toBe("Stable Markdown");

    app.dispose();
  });

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

  it("switches a mounted Lexical surface to readonly without replacing it", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value: "## Stable Markdown",
          editable: true,
          plugins: ["base"],
        })
      );
    });

    const surface = root.querySelector(".jfx-editor__surface") as HTMLElement;
    const toolbar = root.querySelector(".jfx-editor__toolbar") as HTMLElement;
    expect(surface.getAttribute("contenteditable")).toBe("true");

    (root.querySelector(".jfx-editor__readonly-link") as HTMLAnchorElement).click();

    expect(root.querySelector(".jfx-editor__surface")).toBe(surface);
    expect(surface.getAttribute("contenteditable")).toBe("false");
    expect(surface.getAttribute("aria-readonly")).toBe("true");
    expect(toolbar.style.display).toBe("none");
    expect(window.location.search).toBe("?body.editor=readonly");

    (root.querySelector(".jfx-editor__edit-link") as HTMLAnchorElement).click();

    expect(root.querySelector(".jfx-editor__surface")).toBe(surface);
    expect(surface.getAttribute("contenteditable")).toBe("true");
    expect(surface.getAttribute("aria-readonly")).toBe("false");
    expect(window.location.search).toBe("?body.editor=editable");

    app.dispose();
  });

  it("mounts the Lexical surface readonly on the first browser render", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value: "## Initially readonly",
          editable: false,
          plugins: ["base"],
        })
      );
    });

    const fallback = root.querySelector(".jfx-editor__fallback") as HTMLElement;
    const surface = root.querySelector(".jfx-editor__surface") as HTMLElement;
    const toolbar = root.querySelector(".jfx-editor__toolbar") as HTMLElement;

    expect(surface.textContent).toBe("Initially readonly");
    expect(surface.getAttribute("contenteditable")).toBe("false");
    expect(surface.getAttribute("aria-readonly")).toBe("true");
    expect(surface.classList.contains("lexical-read-only")).toBe(true);
    expect(surface.style.display).toBe("");
    expect(fallback.style.display).toBe("none");
    expect(toolbar.style.display).toBe("none");

    app.dispose();
  });

  it("keeps the full heading and inline-format theme contract in Lexical", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value: "###### H6\n\n**bold** *italic* ++underlined++ ~~struck~~ ==marked== `code`",
        })
      );
    });

    expect(root.querySelector(".lexical-heading-h6")?.textContent).toBe("H6");
    expect(root.querySelector(".lexical-text-bold")?.textContent).toBe("bold");
    expect(root.querySelector(".lexical-text-italic")?.textContent).toBe("italic");
    expect(root.querySelector(".lexical-text-underline")?.textContent).toBe("underlined");
    expect(root.querySelector(".lexical-text-strikethrough")?.textContent).toBe("struck");
    expect(root.querySelector("mark")?.textContent).toBe("marked");
    expect(root.querySelector(".jfx-editor__surface code")?.textContent).toBe("code");

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
    const serverTextarea = root.querySelector("textarea");
    const serverSurface = root.querySelector(".jfx-editor__surface");

    // Hydration first claims the textarea emitted by SSR. `afterCompose` then
    // progressively enhances it to Lexical without changing the Markdown
    // value stored in the form model.
    const app = await hydrate(root, build);

    const surface = root.querySelector(".jfx-editor__surface");
    expect(root.querySelector("textarea")).toBe(serverTextarea);
    expect(surface).toBe(serverSurface);
    expect(serverTextarea?.value).toBe("Hello **world**");
    expect(serverTextarea?.isConnected).toBe(true);
    expect(surface?.textContent).toBe("Hello world");

    app.dispose();
  });

  it("enhances an initially readonly SSR preview to readonly Lexical during hydration", async () => {
    const build = (): void => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value: "## Hydrated readonly",
          editable: false,
          plugins: ["base"],
        })
      );
    };

    const rendered = await renderToString(build);
    expect(rendered.html).toContain('<h2 class="lexical-heading-h2">');
    expect(rendered.html).not.toContain("<textarea");

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const serverFallback = root.querySelector(".jfx-editor__fallback") as HTMLElement;
    const serverSurface = root.querySelector(".jfx-editor__surface") as HTMLElement;

    const app = await hydrate(root, build);

    expect(root.querySelector(".jfx-editor__fallback")).toBe(serverFallback);
    expect(root.querySelector(".jfx-editor__surface")).toBe(serverSurface);
    expect(serverFallback.style.display).toBe("none");
    expect(serverSurface.style.display).toBe("");
    expect(serverSurface.textContent).toBe("Hydrated readonly");
    expect(serverSurface.getAttribute("contenteditable")).toBe("false");
    expect(serverSurface.getAttribute("aria-readonly")).toBe("true");
    expect(serverSurface.classList.contains("lexical-read-only")).toBe(true);

    app.dispose();
  });

  it("binds Markdown textarea input back through the form model", () => {
    const model = { body: property("Initial") };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      viewport(() => {
        form(model, {}, () => editor("body"));
      });
    });

    const textarea = root.querySelector("textarea") as HTMLTextAreaElement;
    textarea.value = "## From textarea";
    textarea.dispatchEvent(new Event("input", { bubbles: true }));

    expect(model.body.get).toBe("## From textarea");
    expect(root.querySelector(".jfx-editor__surface")?.textContent).toBe("From textarea");
    app.dispose();
  });

  it("submits the editable SSR textarea without JavaScript", async () => {
    const rendered = await renderToString(() => {
      viewport(() =>
        editor("body", {
          standalone: true,
          value: "## No-JS Markdown",
          editable: true,
        })
      );
    });
    const root = document.createElement("form");
    root.innerHTML = rendered.html;

    expect(new FormData(root).get("body")).toBe("## No-JS Markdown");
  });

  it("keeps Markdown through editable, readonly, and editable remounts", () => {
    let markdown = "## Persistent **Markdown**";
    const root = document.createElement("div");
    document.body.appendChild(root);

    const mountMode = (editable: boolean) =>
      mount(root, () => {
        viewport(() => editor("body", { standalone: true, value: markdown, editable }));
      });

    const editableApp = mountMode(true);
    const firstSurface = root.querySelector(".jfx-editor__surface");
    expect(firstSurface?.textContent).toBe("Persistent Markdown");
    editableApp.dispose();
    expect(firstSurface?.isConnected).toBe(false);

    const readonlyApp = mountMode(false);
    expect(root.querySelector("textarea")).toBeNull();
    expect(root.querySelector(".jfx-editor__surface")?.textContent).toBe("Persistent Markdown");
    expect(root.querySelector(".jfx-editor__surface")?.getAttribute("contenteditable")).toBe(
      "false"
    );
    expect((root.querySelector(".jfx-editor__fallback") as HTMLElement).style.display).toBe(
      "none"
    );
    readonlyApp.dispose();

    markdown = "### Remounted";
    const remountedApp = mountMode(true);
    expect(root.querySelector(".jfx-editor__surface")?.textContent).toBe("Remounted");
    remountedApp.dispose();
  });

  it("removes every listener registered on the Lexical root during unmount", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    const added = new Map<string, number>();
    const removed = new Map<string, number>();
    const originalAdd = EventTarget.prototype.addEventListener;
    const originalRemove = EventTarget.prototype.removeEventListener;

    EventTarget.prototype.addEventListener = function (
      type: string,
      listener: EventListenerOrEventListenerObject | null,
      options?: boolean | AddEventListenerOptions,
    ): void {
      if (this instanceof Element && this.classList.contains("jfx-editor__surface"))
        added.set(type, (added.get(type) ?? 0) + 1);
      originalAdd.call(this, type, listener, options);
    };
    EventTarget.prototype.removeEventListener = function (
      type: string,
      listener: EventListenerOrEventListenerObject | null,
      options?: boolean | EventListenerOptions,
    ): void {
      if (this instanceof Element && this.classList.contains("jfx-editor__surface"))
        removed.set(type, (removed.get(type) ?? 0) + 1);
      originalRemove.call(this, type, listener, options);
    };

    try {
      const app = mount(root, () => {
        viewport(() => editor("body", { standalone: true, plugins: ["base", "image"] }));
      });
      expect(added.size).toBeGreaterThan(0);

      app.dispose();

      for (const [type, count] of added)
        expect(removed.get(type) ?? 0, `listener ${type}`).toBeGreaterThanOrEqual(count);
    } finally {
      EventTarget.prototype.addEventListener = originalAdd;
      EventTarget.prototype.removeEventListener = originalRemove;
    }
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
