/**
 * The ambient scope rules.
 *
 * `demo/scopeRules.ts` demonstrates these three cases; this file asserts them,
 * so a refactoring that quietly loosens the rule fails a build instead of a
 * reading. The rule itself is in src/scope.ts: a scope is installed around
 * synchronous work only, and nothing awaits while one is installed.
 */
import { beforeEach, describe, expect, it } from "vitest";
import { capture, currentScope, div, hasScope, span, text } from "../src/index.js";
import { flush, render, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

describe("scope discipline", () => {
  it("refuses a render body that returns a promise", () => {
    expect(() =>
      render((() => Promise.resolve()) as unknown as () => void)
    ).toThrow(/A render body returned a promise/);
  });

  it("refuses composition after an await, with no scope installed", async () => {
    let error: unknown = null;

    render(() => {
      void (async () => {
        await Promise.resolve();
        try {
          text("too late");
        } catch (caught) {
          error = caught;
        }
      })();
    });

    await flush();
    expect(String(error)).toMatch(/No render scope is active/);
  });

  it("capture() restores the position for a later turn", async () => {
    const { root } = render(() => {
      div(() => {
        const restore = capture();
        setTimeout(() => restore(() => text("composed from a later turn")), 0);
      });
    });

    await flush();
    expect(root.querySelector("div")!.textContent).toBe("composed from a later turn");
  });

  it("capture() restores the stack it was taken in, not the one at call time", async () => {
    let restore: (<T>(body: () => T) => T) | null = null;

    const { root } = render(() => {
      div(() => {
        span(() => {
          restore = capture();
        });
      });
    });

    expect(hasScope()).toBe(false);
    restore!(() => text("inside the span"));
    expect(root.querySelector("span")!.textContent).toBe("inside the span");
  });

  it("hasScope() is false outside a render pass and true inside", () => {
    expect(hasScope()).toBe(false);
    render(() => {
      expect(hasScope()).toBe(true);
      div(() => expect(hasScope()).toBe(true));
    });
    expect(hasScope()).toBe(false);
  });

  it("pops the frame again when a body throws", () => {
    expect(() =>
      render(() => {
        div(() => {
          throw new Error("boom");
        });
      })
    ).toThrow("boom");

    expect(hasScope()).toBe(false);
  });

  it("currentScope() reports where the render is running", () => {
    render(() => {
      expect(currentScope().isBrowser).toBe(true);
      expect(currentScope().isHydrating).toBe(false);
    });
  });
});
