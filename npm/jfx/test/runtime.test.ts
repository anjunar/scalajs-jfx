/**
 * Runtime installation -- the one-runtime invariant, at the level TypeScript
 * can see it.
 *
 * This is the guard that makes the npm modularisation safe: however many
 * packages a consumer installs, exactly one runtime may be installed into
 * exactly one `runtime.ts` module instance. The two failure modes it has to
 * catch are a second, different runtime (a duplicated Scala.js bundle) and a
 * render with none at all.
 *
 * What it cannot catch is a second *copy of this module* -- two `installed`
 * slots that never meet. That is a bundler/resolution property, and the test
 * for it belongs in the consumer test over `npm pack`ed tarballs (Lauf 2).
 */
import { beforeEach, describe, expect, it } from "vitest";
import {
  div,
  installRuntime,
  mount,
  property,
  renderToString,
  resetRuntime,
  runtime,
  text,
} from "../src/index.js";
import type { JfxRuntime } from "../src/index.js";
import { stubRuntime, StubRuntime } from "../src/stub/index.js";

beforeEach(() => {
  resetRuntime();
});

describe("installRuntime", () => {
  it("installs one runtime and hands it back", () => {
    installRuntime(stubRuntime);
    expect(runtime()).toBe(stubRuntime);
    expect(runtime().name).toBe("stub");
  });

  it("is idempotent for the same instance", () => {
    installRuntime(stubRuntime);
    expect(() => installRuntime(stubRuntime)).not.toThrow();
    expect(runtime()).toBe(stubRuntime);
  });

  it("refuses a second, different runtime", () => {
    installRuntime(stubRuntime);

    // A second StubRuntime is a different object -- exactly the shape of the
    // real failure: two Scala.js bundles, or one bundle loaded twice, each
    // calling installRuntime with its own bridgeRuntime.
    const second = new StubRuntime();
    expect(second).not.toBe(stubRuntime);

    expect(() => installRuntime(second)).toThrow(
      /A JFX runtime is already installed \("stub"\)/
    );
    expect(() => installRuntime(second)).toThrow(/would split the component tree/);
  });

  it("names both runtimes in the refusal, so the duplicate is identifiable", () => {
    installRuntime(stubRuntime);

    const foreign: JfxRuntime = {
      ...stubRuntime,
      name: "jfx-bridge",
      property: stubRuntime.property.bind(stubRuntime),
      listProperty: stubRuntime.listProperty.bind(stubRuntime),
      mount: stubRuntime.mount.bind(stubRuntime),
      hydrate: stubRuntime.hydrate.bind(stubRuntime),
      renderToString: stubRuntime.renderToString.bind(stubRuntime),
    };

    expect(() => installRuntime(foreign)).toThrow(/"stub"/);
    expect(() => installRuntime(foreign)).toThrow(/"jfx-bridge"/);
  });

  it("keeps the first runtime installed after a refused second one", () => {
    installRuntime(stubRuntime);
    expect(() => installRuntime(new StubRuntime())).toThrow();
    expect(runtime()).toBe(stubRuntime);
  });
});

describe("without a runtime", () => {
  it("runtime() explains what is missing", () => {
    expect(() => runtime()).toThrow(/No JFX runtime installed/);
  });

  it("property() fails rather than inventing state", () => {
    expect(() => property(0)).toThrow(/No JFX runtime installed/);
  });

  it("mount() fails", () => {
    expect(() => mount(document.createElement("div"), () => text("x"))).toThrow(
      /No JFX runtime installed/
    );
  });

  it("renderToString() fails", () => {
    expect(() => renderToString(() => div())).toThrow(/No JFX runtime installed/);
  });
});
