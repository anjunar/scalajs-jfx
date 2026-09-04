/**
 * The declarative layer: nesting, element settings and events.
 *
 * These run against the DOM host, so what is asserted is the markup a browser
 * would see -- not an internal representation the DSL happens to build.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  addClass,
  anchor,
  attr,
  button,
  classes,
  classIf,
  component,
  div,
  domProperty,
  hbox,
  heading,
  mount,
  onClick,
  onInput,
  property,
  self,
  span,
  style,
  text,
  vbox,
} from "../src/index.js";
import { render, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

describe("nesting", () => {
  it("composes children in call order and in the right parent", () => {
    const { html } = render(() => {
      div(() => {
        span(() => text("one"));
        span(() => text("two"));
      });
      div(() => text("sibling"));
    });

    expect(html()).toBe(
      "<div><span>one</span><span>two</span></div><div>sibling</div>"
    );
  });

  it("nests to arbitrary depth", () => {
    const { html } = render(() => {
      div(() => div(() => div(() => text("deep"))));
    });
    expect(html()).toBe("<div><div><div>deep</div></div></div>");
  });

  it("unmounts a half-built child when its body throws", () => {
    const root = document.createElement("div");

    expect(() =>
      mount(root, () => {
        div(() => text("kept"));
        div(() => {
          text("discarded");
          throw new Error("boom");
        });
      })
    ).toThrow("boom");

    // The failing element was unmounted again; only the completed one is left.
    expect(root.innerHTML).toBe("<div>kept</div>");
  });

  it("heading renders the requested level", () => {
    const { html } = render(() => heading(3, () => text("Title")));
    expect(html()).toBe("<h3>Title</h3>");
  });
});

describe("library components", () => {
  it("mounts the three registered components", () => {
    const { html } = render(() => {
      vbox(() => hbox(() => button("Go")));
    });

    expect(html()).toBe(
      '<div class="jfx-vbox"><div class="jfx-hbox">' +
        '<button class="jfx-button" type="button">Go</button>' +
        "</div></div>"
    );
  });

  it("rejects an unregistered component name", () => {
    // "combo-box" lives in jfx-controls, which neither runtime registers today.
    // This is the exact failure a `controls` npm package would hit if it shipped
    // before the bridge grew its registry entries.
    expect(() => render(() => component("combo-box"))).toThrow(
      /Unknown component "combo-box"/
    );
  });

  it("binds a reactive button label", () => {
    const label = property("Go");
    const { root } = render(() => button(label));
    const element = root.querySelector("button")!;
    expect(element.textContent).toBe("Go");
    label.set("Stop");
    expect(element.textContent).toBe("Stop");
  });

  it("binds a reactive disabled flag", () => {
    const busy = property(false);
    const { root } = render(() => button("Save", { disabled: busy }));
    const element = root.querySelector("button")!;
    expect(element.hasAttribute("disabled")).toBe(false);
    expect(element.getAttribute("aria-disabled")).toBe("false");
    busy.set(true);
    expect(element.hasAttribute("disabled")).toBe(true);
    expect(element.getAttribute("aria-disabled")).toBe("true");
  });
});

describe("element settings", () => {
  it("classes replaces, addClass adds", () => {
    const { root } = render(() => {
      div(() => {
        classes("a", "b");
        addClass("c");
        classes("d");
      });
    });
    expect(root.querySelector("div")!.className.split(" ").sort()).toEqual(["c", "d"]);
  });

  it("classIf follows a boolean property", () => {
    const active = property(false);
    const { root } = render(() => div(() => classIf("is-active", active)));
    const element = root.querySelector("div")!;
    expect(element.className).toBe("");
    active.set(true);
    expect(element.className).toBe("is-active");
    active.set(false);
    expect(element.className).toBe("");
  });

  it("attr and style accept constants and properties", () => {
    const href = property("/one");
    const { root } = render(() => {
      anchor(() => {
        attr("href", href);
        attr("rel", "noopener");
        style("color", "red");
      });
    });

    const element = root.querySelector("a")!;
    expect(element.getAttribute("href")).toBe("/one");
    expect(element.getAttribute("rel")).toBe("noopener");
    expect(element.style.color).toBe("red");

    href.set("/two");
    expect(element.getAttribute("href")).toBe("/two");
  });

  it("domProperty writes a DOM property, not an attribute", () => {
    const { root } = render(() => div(() => domProperty("datasetIsh", 42)));
    const element = root.querySelector("div")!;
    expect((element as unknown as Record<string, unknown>)["datasetIsh"]).toBe(42);
    expect(element.hasAttribute("datasetIsh")).toBe(false);
  });

  it("self() is the element currently being composed", () => {
    let tagName = "";
    render(() =>
      span(() => {
        tagName = self().tagName;
      })
    );
    expect(tagName).toBe("span");
  });

  it("refuses element settings outside an element body", () => {
    expect(() => render(() => classes("orphan"))).toThrow(/No component is being composed/);
  });
});

describe("events", () => {
  it("delivers a click through the UiEvent projection", () => {
    const handler = vi.fn();
    const { root } = render(() => div(() => onClick(handler)));
    root.querySelector("div")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));

    expect(handler).toHaveBeenCalledTimes(1);
    const event = handler.mock.calls[0]![0] as { type: string; native: Event | null };
    expect(event.type).toBe("click");
    expect(event.native).toBeInstanceOf(Event);
  });

  it("restores the render position inside a handler", () => {
    const { root } = render(() =>
      div(() => {
        onClick(() => {
          // Composing from an event handler only works because `on` captured the
          // scope. Without capture() this call throws ScopeError.
          text("added later");
        });
      })
    );

    const element = root.querySelector("div")!;
    element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(element.textContent).toBe("added later");
  });

  it("onInput maps to the input event", () => {
    const handler = vi.fn();
    const { root } = render(() => div(() => onInput(handler)));
    root.querySelector("div")!.dispatchEvent(new Event("input"));
    expect(handler).toHaveBeenCalledTimes(1);
  });
});
