/**
 * The consumer test for the router package: does a foreign project get a usable
 * router by installing three tarballs and importing only through public
 * `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The three packed tarballs install and resolve together.
 *  2. `@anjunar/jfx-router` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from all
 *     three packages -- the regression test for a broken shipped declaration.
 *  4. SSR of a route table against the linked Scala.js bridge produces the
 *     matched route's HTML and an error route's own status.
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
        '    sbt --server "scalajs-jfx-bridge/fullLinkJS"\n\n' +
        "Expected: " +
        linkedArtifact
    );
  }

  consumer = mkdtempSync(join(tmpdir(), "jfx-router-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const routerTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-router-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/jfx-router": fileSpecifier(routerTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: jfx-core declares a peer on the CSS package this probe
  // does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-router");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("router.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all three packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { div, installRuntime, renderToString, text } from "@anjunar/jfx-core";',
      'import type { SsrResult } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { errorRoute, router, routerLink, routerOutlet, view } from "@anjunar/jfx-router";',
      'import type { RouteContext, RouteDefinition } from "@anjunar/jfx-router";',
      "",
      "const routes: readonly RouteDefinition[] = [",
      "  view(",
      '    "/",',
      "    async (context: RouteContext) => () => {",
      "      div(() => text(context.path));",
      "      routerOutlet();",
      '      routerLink("/other", "Other");',
      "    },",
      '    { children: [view("other", async () => () => div(() => text("other")))] },',
      "  ),",
      '  errorRoute("/404", 404, async () => () => div(() => text("missing"))),',
      "];",
      "",
      "export async function render(path: string): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      '  return renderToString(() => router(routes, { url: path, onFailure: () => "/404" }));',
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

describe("rendering a route table from a packed install", () => {
  it("renders the matched route and an error route's status against the bridge", () => {
    const script = [
      'import { div, installRuntime, renderToString, text } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { errorRoute, router, view } from "@anjunar/jfx-router";',
      "installRuntime(bridgeRuntime);",
      "const routes = [",
      '  view("/", async () => () => div(() => text("home page"))),',
      '  errorRoute("/404", 404, async () => () => div(() => text("no such page"))),',
      "];",
      "const ok = await renderToString(() => router(routes, { url: \"/\" }));",
      'const missing = await renderToString(() => router(routes, { url: "/nope", onFailure: () => "/404", renderErrorsOnServer: true }));',
      "console.log(JSON.stringify({",
      "  okStatus: ok.status, okHtml: ok.html.includes(\"home page\"),",
      "  missingStatus: missing.status, missingHtml: missing.html.includes(\"no such page\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-router.mjs"), script);

    const result = lastJsonLine<{
      okStatus: number;
      okHtml: boolean;
      missingStatus: number;
      missingHtml: boolean;
    }>(run(process.execPath, ["ssr-router.mjs"], consumer));

    expect(result.okStatus).toBe(200);
    expect(result.okHtml).toBe(true);
    expect(result.missingStatus).toBe(404);
    expect(result.missingHtml).toBe(true);
  });
});
