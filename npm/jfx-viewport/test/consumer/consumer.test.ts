/**
 * The consumer test for the viewport package: does a foreign project get a
 * usable viewport by installing the tarballs and importing only through
 * public `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs install and resolve together.
 *  2. `@anjunar/jfx-viewport` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + viewport -- the regression test for a broken shipped
 *     declaration.
 *  4. SSR of a viewport with a notification and a window against the linked
 *     Scala.js bridge produces the expected HTML.
 */
import { execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const packageRoot = resolve(process.cwd());
const repoRoot = resolve(packageRoot, "..", "..");
const corePackage = join(repoRoot, "npm", "jfx-core");
const bridgePackage = join(repoRoot, "npm", "scalajs-jfx-bridge");
const linkedArtifact = join(bridgePackage, "dist", "fullopt", "main.js");

let consumer = "";

function run(command: string, args: readonly string[], cwd: string): string {
  return execFileSync(command, [...args], {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function npm(args: readonly string[], cwd: string): string {
  const entry = process.env["npm_execpath"];
  if (entry !== undefined && entry.endsWith(".js")) {
    return run(process.execPath, [entry, ...args], cwd);
  }
  return execFileSync("npm", [...args], {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    shell: process.platform === "win32",
  });
}

function pack(directory: string, into: string): string {
  const output = npm(["pack", "--pack-destination", into, "--silent"], directory);
  const name = output.trim().split("\n").pop()!.trim();
  return join(into, name);
}

function fileSpecifier(path: string): string {
  return "file:" + path.replace(/\\/g, "/");
}

function lastJsonLine<T>(output: string): T {
  return JSON.parse(output.trim().split("\n").pop()!) as T;
}

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      "The Scala.js bridge is not linked. Run:\n\n" +
        '    sbtn "scalajs-jfx-bridge/fullLinkJS"\n\n' +
        "Expected: " +
        linkedArtifact
    );
  }

  consumer = mkdtempSync(join(tmpdir(), "jfx-viewport-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const viewportTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-viewport-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/jfx-viewport": fileSpecifier(viewportTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core and viewport both declare a peer on the CSS package
  // this probe does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-viewport");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("window.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all three packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { button, div, installRuntime, onClick, property, renderToString, text, when } from "@anjunar/jfx-core";',
      'import type { SsrResult } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { floatingWindow, notify, overlay, viewport } from "@anjunar/jfx-viewport";',
      'import type { NotificationOptions, OverlayOptions, WindowOptions } from "@anjunar/jfx-viewport";',
      "",
      "const windowOptions: WindowOptions = { title: \"Room\", widthPx: 400 };",
      "const overlayOptions: OverlayOptions = { widthPx: 200 };",
      "const notifyOptions: NotificationOptions = { kind: \"success\", durationMs: 2000 };",
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  const open = property(false);",
      "  return renderToString(() => {",
      "    viewport(() => {",
      "      button(\"Open\", {}, () => onClick(() => open.set(true)));",
      "      when(open, () => {",
      "        floatingWindow(windowOptions, () => div(() => text(\"body\")));",
      "      });",
      "      div(() => overlay(overlayOptions, () => text(\"menu\")));",
      "      notify(\"Saved\", notifyOptions);",
      "    });",
      "  });",
      "}",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "src", "app.ts"), source);

    writeFileSync(
      join(consumer, "tsconfig.json"),
      JSON.stringify(
        {
          compilerOptions: {
            target: "ES2022",
            module: "ES2022",
            moduleResolution: "bundler",
            lib: ["ES2022", "DOM"],
            strict: true,
            noEmit: true,
            skipLibCheck: false,
          },
          include: ["src"],
        },
        null,
        2
      )
    );

    const tsc = join(repoRoot, "node_modules", "typescript", "bin", "tsc");
    expect(() => run(process.execPath, [tsc, "-p", "tsconfig.json"], consumer)).not.toThrow();
  });
});

describe("rendering a viewport from a packed install", () => {
  it("renders a notification and a window against the bridge", () => {
    const script = [
      'import { button, div, installRuntime, onClick, property, renderToString, text, when } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { floatingWindow, notify, viewport } from "@anjunar/jfx-viewport";',
      "installRuntime(bridgeRuntime);",
      "const open = property(true);",
      "const result = await renderToString(() => {",
      "  viewport(() => {",
      "    notify(\"Saved\", { kind: \"success\" });",
      "    when(open, () => {",
      "      floatingWindow({ title: \"A room for thoughts\" }, () => div(() => text(\"window body\")));",
      "    });",
      "  });",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  viewportHost: result.html.includes(\"jfx-viewport\"),",
      "  notification: result.html.includes(\"jfx-viewport-notification--success\"),",
      "  saved: result.html.includes(\"Saved\"),",
      "  windowTitle: result.html.includes(\"A room for thoughts\"),",
      "  windowBody: result.html.includes(\"window body\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-viewport.mjs"), script);

    const result = lastJsonLine<{
      status: number;
      viewportHost: boolean;
      notification: boolean;
      saved: boolean;
      windowTitle: boolean;
      windowBody: boolean;
    }>(run(process.execPath, ["ssr-viewport.mjs"], consumer));

    expect(result.status).toBe(200);
    expect(result.viewportHost).toBe(true);
    expect(result.notification).toBe(true);
    expect(result.saved).toBe(true);
    expect(result.windowTitle).toBe(true);
    expect(result.windowBody).toBe(true);
  });
});
