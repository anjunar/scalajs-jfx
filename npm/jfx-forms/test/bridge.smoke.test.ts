/**
 * Smoke test against the real bridge.
 *
 * Model binding needs the real bridge's `Property`/`ListProperty` handles
 * (`PropertyHandle`/`ListPropertyHandle`) to find the underlying Scala
 * `Property`, which the stub runtime does not build -- so, like the controls
 * and viewport facades, there is no stub half here.
 *
 * It needs the linked artifact:
 *
 *     sbt --server "scalajs-jfx-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import {
  hydrate,
  installRuntime,
  listProperty,
  mount,
  property,
  renderToString,
  resetRuntime,
  runtime,
} from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { viewport } from "@anjunar/jfx-viewport";
import {
  arrayForm,
  comboBox,
  fieldSet,
  form,
  input,
  inputContainer,
  imageCropper,
  notBlank,
  NotBlank,
  AssertTrue,
  AssertFalse,
  Past,
  Future,
  size,
  subForm,
  type MediaValue,
} from "../src/index.js";
import { button, onClick } from "@anjunar/jfx-core";

const linkedArtifact = resolve(process.cwd(), "../scalajs-jfx-bridge/dist/fullopt/main.js");

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbt --server "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
  // comboBox's dropdown is a jfx-controls TableView, which observes its
  // viewport size in the browser; jsdom ships neither observer. A no-op pair
  // is enough for mount/hydrate to run -- same polyfill jfx-controls' own
  // smoke test uses.
  const noop = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };
  (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= noop;
  (globalThis as { IntersectionObserver?: unknown }).IntersectionObserver ??= noop;
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

describe("form + input", () => {
  it("validates native date input values and boolean model values", () => {
    class Model {
      @Past() readonly past = property("");
      @Future() readonly future = property("");
      @AssertTrue() readonly yes = property(false);
      @AssertFalse() readonly no = property(true);
    }
    const model = new Model();
    const root = document.createElement("div");
    document.body.appendChild(root);
    let handle!: import("@anjunar/jfx-core").FormHandle;
    const app = mount(root, () => viewport(() => {
      handle = form(model, () => {
        input("past", { type: "date" });
        input("future", { type: "date" });
        comboBox("yes", { items: [true, false], converter: String });
        comboBox("no", { items: [true, false], converter: String });
      });
    }));
    try {
      const setDate = (name: string, value: string): void => {
        const field = root.querySelector(`input[name="${name}"]`) as HTMLInputElement;
        field.value = value;
        field.dispatchEvent(new Event("input", { bubbles: true }));
      };
      setDate("past", "2999-01-01");
      setDate("future", "1900-01-01");
      expect(handle.validate()).toEqual(expect.arrayContaining([
        "Must be in the past", "Must be in the future", "Must be true", "Must be false",
      ]));
      setDate("past", "1900-01-01");
      setDate("future", "2999-01-01");
      model.yes.set(true);
      model.no.set(false);
      expect(model.yes.get).toBe(true);
      expect(model.no.get).toBe(false);
      expect(model.past.get).toBe("1900-01-01");
      expect(handle.validate()).toEqual([]);
    } finally {
      app.dispose();
      root.remove();
    }
  });

  it("infers validators from decorated class models", () => {
    class AccountModel {
      @NotBlank()
      readonly name = property("");
    }
    const model = new AccountModel();
    const root = document.createElement("div");
    document.body.appendChild(root);

    let handle!: import("@anjunar/jfx-core").FormHandle;
    const app = mount(root, () => {
      handle = form(model, () => {
        inputContainer({ label: "Name" }, () => input("name"));
      });
    });

    expect(handle.validate()).toContain("Must not be blank");
    expect(model.name.get).toBe("");
    app.dispose();
  });

  it("returns a handle for validation and binding diagnostics", () => {
    const model = { name: property("") };
    const root = document.createElement("div");
    document.body.appendChild(root);
    let handle: import("@anjunar/jfx-core").FormHandle;

    const app = mount(root, () => {
      handle = form(model, { schema: { name: [notBlank()] } }, () => {
        inputContainer({ label: "Name" }, () => input("name"));
      });
    });

    expect(handle!.validateBindings()).toEqual([]);
    expect(handle!.validate()).toContain("Must not be blank");
    handle!.setErrorResponses([{ message: "Rejected by server", path: ["name"] }]);
    expect(root.querySelector(".jfx-input-container__errors")?.textContent).toContain(
      "Rejected by server"
    );
    handle!.clearErrors();
    expect(root.querySelector(".jfx-input-container__errors")?.textContent ?? "").toBe("");
    app.dispose();
  });

  it("binds a control to a model property bidirectionally", () => {
    const model = { name: property("Ada") };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        input("name");
      });
    });

    const field = root.querySelector("input") as HTMLInputElement;
    expect(field.value).toBe("Ada");

    model.name.set("Grace");
    expect(field.value).toBe("Grace");

    field.value = "Katherine";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(model.name.get).toBe("Katherine");

    app.dispose();
  });

  it("attaches schema validators and reports errors through inputContainer", () => {
    const model = { name: property("") };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, { schema: { name: [notBlank(), size(2, 10)] } }, () => {
        inputContainer({ label: "Name" }, () => {
          input("name");
        });
      });
    });

    // Errors surface once the control is dirty (or force-validated) -- a
    // focus/blur with no edit in between leaves it pristine, and `validate()`
    // deliberately clears errors for a pristine control (`Control.validate`).
    const field = root.querySelector("input") as HTMLInputElement;
    field.value = "";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("blur"));

    expect(root.querySelector(".jfx-input-container__errors")?.textContent ?? "").not.toBe("");

    field.value = "Al";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("blur"));

    expect(root.querySelector(".jfx-input-container__errors")?.textContent ?? "").toBe("");

    app.dispose();
  });

  it("renders and hydrates, and reacts to input after hydration with no reactive gate", async () => {
    const model = { name: property("Ada") };
    const build = (): void => {
      form(model, {}, () => {
        input("name");
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, build);
    const field = root.querySelector("input") as HTMLInputElement;
    expect(field.value).toBe("Ada");

    field.value = "Grace";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(model.name.get).toBe("Grace");

    app.dispose();
  });

  it("rebinds a nested form when its parent model object changes", () => {
    const first = { name: property("Ada") };
    const second = { name: property("Grace") };
    const model = { owner: property<typeof first | null>(first) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        subForm("owner", first, {}, () => input("name"));
      });
    });

    const field = root.querySelector("input") as HTMLInputElement;
    expect(field.value).toBe("Ada");
    model.owner.set(second);
    expect(field.value).toBe("Grace");
    field.value = "Katherine";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(first.name.get).toBe("Ada");
    expect(second.name.get).toBe("Katherine");
    model.owner.set(null);
    expect(field.value).toBe("");
    field.value = "Detached";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(first.name.get).toBe("Ada");
    expect(second.name.get).toBe("Katherine");
    app.dispose();
  });
});

describe("imageCropper", () => {
  it.each(["mount", "hydrate"])("preserves an existing image during %s and binds both directions", async (mode) => {
    const initial: MediaValue = {
      id: "11111111-1111-4111-8111-111111111111",
      name: "existing.png", contentType: "image/png", data: "data:image/png;base64,aGVsbG8=",
    };
    const replacement: MediaValue = { ...initial, name: "replacement.png", data: "data:image/png;base64,d29ybGQ=" };
    const photo = property<MediaValue | null>(initial);
    const build = (): void => { form({ photo }, {}, () => imageCropper("photo")); };
    const root = document.createElement("div");
    if (mode === "hydrate") {
      root.innerHTML = (await renderToString(build)).html;
      expect(photo.get).toBe(initial);
    }
    const serverImage = root.querySelector("img");
    const app = mode === "hydrate" ? await hydrate(root, build) : mount(root, build);
    const preview = root.querySelector("img")!;
    expect(photo.get).toBe(initial);
    expect(preview.getAttribute("src")).toBe(initial.data);
    if (mode === "hydrate") expect(preview).toBe(serverImage);
    photo.set(replacement);
    expect(photo.get).toBe(replacement);
    expect(preview.getAttribute("src")).toBe(replacement.data);
    const clear = Array.from(root.querySelectorAll("button")).find((el) => el.textContent === "Clear")!;
    clear.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(photo.get).toBeNull();
    photo.set(initial);
    app.dispose();
    photo.set(replacement);
    expect(preview.getAttribute("src")).toBe(initial.data);
  });
});

describe("fieldSet", () => {
  it("forwards errors and binding diagnostics through a nested dynamic form", () => {
    const row = { name: property("Ada") };
    const root = document.createElement("div");
    let handle!: import("@anjunar/jfx-core").FormHandle;
    const log = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const app = mount(root, () => {
        handle = form({ group: property(undefined) }, {}, () => {
          fieldSet({ name: "group" }, () => subForm("row", row, {}, () => {
            inputContainer({ label: "Name" }, () => input("name"));
            input("typo");
          }));
        });
      });
      expect(handle.validateBindings()).toHaveLength(1);
      expect(handle.validateBindings()[0]).toContain("cannot bind control 'typo'");
      handle.setErrorResponses([{ message: "Grouped error", path: ["group", "row", "name"] }]);
      expect(root.querySelector(".jfx-input-container__errors")?.textContent).toContain("Grouped error");
      handle.clearErrors();
      expect(root.querySelector(".jfx-input-container__errors")?.textContent ?? "").toBe("");
      app.dispose();
    } finally { log.mockRestore(); }
  });

  it("groups controls without binding them to the model", () => {
    const model = { name: property("Ada") };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        fieldSet({ name: "group" }, () => {
          input("name", { standalone: true });
        });
      });
    });

    expect(root.querySelector("fieldset")).not.toBeNull();
    const field = root.querySelector("input") as HTMLInputElement;
    expect(field.value).toBe("");

    app.dispose();
  });
});

describe("arrayForm", () => {
  it("propagates nested server errors and binding diagnostics through array items", () => {
    const row = { name: property("Ada") };
    const model = { rows: listProperty([row]) };
    const root = document.createElement("div");
    let handle!: import("@anjunar/jfx-core").FormHandle;
    const log = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const app = mount(root, () => {
        handle = form(model, {}, () => arrayForm("rows", (index) => {
          subForm(`row-${index}`, row, {}, () => {
            inputContainer({ label: "Name" }, () => input("name"));
            input("typo");
          });
        }));
      });
      expect(handle.validateBindings()).toHaveLength(1);
      expect(handle.validateBindings()[0]).toContain("cannot bind control 'typo'");
      handle.setErrorResponses([{ message: "Nested server error", path: ["rows", "0", "name"] }]);
      expect(root.querySelector(".jfx-input-container__errors")?.textContent).toContain("Nested server error");
      handle.clearErrors();
      expect(root.querySelector(".jfx-input-container__errors")?.textContent ?? "").toBe("");
      model.rows.clear();
      expect(handle.validateBindings()).toEqual([]);
      app.dispose();
    } finally { log.mockRestore(); }
  });

  it("renders one control per item and follows structural changes", () => {
    const model = { tags: listProperty<string>(["math", "compilers"]) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        // Not standalone: an item control must self-register with the
        // arrayForm (the ambient form context here) for `itemControlAt` to
        // find it -- see `FormFactories.arrayFormRenderer`.
        arrayForm("tags", (index) => {
          input(`tag-${index}`);
        });
      });
    });

    let fields = Array.from(root.querySelectorAll("input")) as HTMLInputElement[];
    expect(fields.map((el) => el.value)).toEqual(["math", "compilers"]);

    model.tags.insert(1, "logic");
    fields = Array.from(root.querySelectorAll("input")) as HTMLInputElement[];
    expect(fields.map((el) => el.value)).toEqual(["math", "logic", "compilers"]);

    const firstField = fields[0]!;
    firstField.focus();
    firstField.value = "algebra";
    firstField.dispatchEvent(new Event("input", { bubbles: true }));
    expect(model.tags.get).toEqual(["algebra", "logic", "compilers"]);
    expect(root.querySelector("input")).toBe(firstField);
    expect(document.activeElement).toBe(firstField);

    app.dispose();
  });

  it("creates a new item when a consumer button changes the list", () => {
    const model = { tags: listProperty<string>(["typescript"]) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        arrayForm("tags", (index) => {
          input(`tag-${index}`);
        });
        button("Add tag", {}, () => {
          onClick(() => model.tags.add(""));
        });
      });
    });

    expect(root.querySelectorAll("input")).toHaveLength(1);
    root.querySelector("button")!.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );
    expect(root.querySelectorAll("input")).toHaveLength(2);

    app.dispose();
  });

  // Regression: with the renderer supplied only through `controlRenderer_=`
  // (the plain DSL path), `ArrayForm.compose`'s first `foreachIndexed` pass
  // ran before that setter fired, rendering zero items -- fine for a live
  // `mount()` (the setter's own `valueProperty.notified()` forces a second,
  // correct pass), but wrong for hydration: `HydratingCursor` had already
  // walked past an empty range by the time the second pass tried to claim the
  // server-rendered item nodes, and threw "Server-rendered nodes were not
  // claimed by the client." `ArrayFormFactory` now supplies the renderer via
  // `ArrayForm`'s constructor instead, so the very first pass already renders
  // every item. Only reproducible with a real claim-walking cursor, not a
  // fresh `mount()` -- see the memory note this session left on Lauf 5's own
  // hydration-only bug for why a plain `mount()` test does not cover this.
  it("hydrates a non-empty list without a claim mismatch", async () => {
    const model = { tags: listProperty<string>(["math", "compilers"]) };
    const build = (): void => {
      form(model, {}, () => {
        arrayForm("tags", (index) => {
          input(`tag-${index}`);
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, build);

    const fields = Array.from(root.querySelectorAll("input")) as HTMLInputElement[];
    expect(fields.map((el) => el.value)).toEqual(["math", "compilers"]);

    app.dispose();
  });
});

describe("subForm", () => {
  it("binds a nested model bidirectionally, as a control of its parent", () => {
    const owner = { name: property("") };
    const model = { owner: property(owner) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        subForm("owner", owner, {}, () => {
          input("name");
        });
      });
    });

    const field = root.querySelector("input") as HTMLInputElement;
    field.value = "Ada";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(owner.name.get).toBe("Ada");

    app.dispose();
  });

  it("propagates a null parent model through nested subforms", () => {
    const contact = { name: property("Ada") };
    const address = { contact: property(contact) };
    const model = { address: property<typeof address | null>(address) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      form(model, {}, () => {
        subForm("address", address, {}, () =>
          subForm("contact", contact, {}, () => input("name"))
        );
      });
    });

    const field = root.querySelector("input") as HTMLInputElement;
    expect(field.value).toBe("Ada");
    model.address.set(null);
    expect(field.value).toBe("");
    field.value = "Detached";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    expect(contact.name.get).toBe("Ada");

    app.dispose();
  });
});

describe("comboBox", () => {
  it("selects an item and updates the bound model property", () => {
    const model = { color: property<string | null>(null) };
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      // ComboBox's dropdown is an `overlay`, which needs a `viewport` ancestor
      // -- same requirement as the Scala demo's own combo-box usage.
      viewport(() => {
        form(model, {}, () => {
          comboBox("color", { items: ["red", "green", "blue"] });
        });
      });
    });

    const trigger = root.querySelector(".jfx-combo-box") as HTMLElement;
    trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));

    const option = root.querySelector(".jfx-combo-box__item") as HTMLElement;
    option.dispatchEvent(new MouseEvent("click", { bubbles: true }));

    expect(model.color.get).toBe("red");

    app.dispose();
  });

  // Regression shape from the viewport/controls facades: an interaction that
  // opens an overlay-backed control, exercised after hydration rather than a
  // plain mount, since that is where a stale captured cursor would surface.
  it("opens its dropdown from a bare click after hydration", async () => {
    const model = { color: property<string | null>(null) };
    const build = (): void => {
      viewport(() => {
        form(model, {}, () => {
          comboBox("color", { items: ["red", "green", "blue"] });
        });
      });
    };

    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, build);

    const trigger = root.querySelector(".jfx-combo-box") as HTMLElement;
    trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));

    expect(root.querySelector(".jfx-combo-box__item")).not.toBeNull();
    app.dispose();
  });
});
