/**
 * The consumer test: does a foreign project actually get a usable library?
 *
 * Everything else in this repo tests the source tree. That is a different thing
 * from what a consumer installs, and the difference is where this prototype has
 * lost blood before -- three packaging faults are recorded in JAVASCRIPT_API.md
 * §11/§13 and a fourth in CLAUDE_REVIEW_3.md §9, and every one of them was
 * invisible to a test that imported by relative path. `npm pack` into an empty
 * directory is the only setup that can see them.
 *
 * What is asserted here, and nothing else:
 *
 *  1. The packed tarballs install and resolve.
 *  2. The public `exports` are enough -- `.` and `./stub`, nothing deep.
 *  3. The types ship. `tsc --strict` over a file that imports from both
 *     packages is the regression test for the TS7016 that every real consumer
 *     used to hit.
 *  4. There is exactly one runtime slot, reached through two independent import
 *     routes (bare specifier and resolved real path). This is the acceptance
 *     criterion from PROMPT_NPM_MODULARIZATION.md §5 that no in-repo test can
 *     stand in for: with npm workspaces hoisting everything into one directory,
 *     a second copy of jfx-core cannot arise here -- it can at a stranger's.
 *  5. SSR runs, against the stub and against the linked Scala.js bridge.
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

/**
 * Runs npm.
 *
 * `npm_execpath` is npm's own JavaScript entry point, which npm sets for every
 * script it runs. Going through it keeps this on {@link run}: no shell, so no
 * re-splitting of paths on spaces, and no DEP0190 warning about unescaped
 * arguments. The fallback only matters when the suite is started by hand
 * (`npx vitest`), and on Windows it needs a shell because `npm` is a `.cmd`,
 * which `execFileSync` will not launch otherwise.
 */
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

/** Packs one workspace package and returns the absolute path of the tarball. */
function pack(directory: string, into: string): string {
  const output = npm(["pack", "--pack-destination", into, "--silent"], directory);
  const name = output.trim().split("\n").pop()!.trim();
  return join(into, name);
}

/** npm wants forward slashes in a `file:` specifier, on Windows too. */
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

  consumer = mkdtempSync(join(tmpdir(), "jfx-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  // `prepack` builds dist/, so the tarball carries what a consumer would get.
  const coreTarball = pack(packageRoot, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: @anjunar/jfx-core declares a peer on the CSS package,
  // which this probe deliberately does not install -- it imports no stylesheet,
  // and pulling @anjunar/ui in behind it would test npm's network, not us.
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-core");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    // src/, test/ and the vitest configs are not a consumer's business.
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("stub");
  });

  it("ships the bridge's types next to the linked, optimised bundle", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "scalajs-jfx-bridge");
    expect(readdirSync(installed)).toContain("types");
    expect(readdirSync(installed)).toContain("index.js");
    // The package entry point installs the optimised linked runtime.
    expect(readdirSync(join(installed, "dist", "fullopt"))).toContain("main.js");
  });
});

describe("typechecking a consumer", () => {
  it("resolves both packages under --strict, including bridgeRuntime", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    // Deliberately exercises every public entry point a page would touch, plus
    // the import that used to fail with TS7016.
    const source = [
      'import { button, classes, div, installRuntime, property, renderToString, text, vbox } from "@anjunar/jfx-core";',
      'import type { JfxRuntime, Property, SsrResult } from "@anjunar/jfx-core";',
      'import { stubRuntime } from "@anjunar/jfx-core/stub";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      "",
      "const runtimes: readonly JfxRuntime[] = [stubRuntime, bridgeRuntime];",
      "",
      "export function page(): void {",
      "  const count: Property<number> = property(0);",
      "  vbox(() => {",
      '    classes("page");',
      "    div(() => text(count.map((value) => String(value))));",
      '    button("Go");',
      "  });",
      "}",
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(runtimes[1]!);",
      "  return renderToString(page);",
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
            // Off on purpose: skipLibCheck is the setting that would hide a
            // broken shipped declaration, which is precisely what is on trial.
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

describe("one runtime, through two import routes", () => {
  it("reaches the same module instance and the same installed slot", () => {
    const probe = [
      'import { createRequire } from "node:module";',
      'import { pathToFileURL } from "node:url";',
      'import { realpathSync } from "node:fs";',
      "",
      "// Route 1: the bare specifier, the way any consumer imports it.",
      'const bare = await import("@anjunar/jfx-core");',
      "",
      "// Route 2: the same module reached by its resolved, real filesystem",
      "// path. Two different specifiers for one file -- exactly the shape that",
      '// gave npm/jfx-demo two "installed" slots under Vite',
      "// (JAVASCRIPT_API.md §13). If the two were kept apart, the namespaces",
      "// would disagree below.",
      "const require = createRequire(import.meta.url);",
      'const real = realpathSync(require.resolve("@anjunar/jfx-core"));',
      "const direct = await import(pathToFileURL(real).href);",
      "",
      'const stub = await import("@anjunar/jfx-core/stub");',
      "bare.installRuntime(stub.stubRuntime);",
      "",
      "const sameModule = bare.runtime === direct.runtime;",
      "const sameSlot = direct.runtime() === stub.stubRuntime;",
      "",
      "// And the guard still bites through the second route.",
      "let refused = false;",
      "try {",
      "  direct.installRuntime(new stub.StubRuntime());",
      "} catch (error) {",
      '  refused = String(error).includes("already installed");',
      "}",
      "",
      "console.log(JSON.stringify({ sameModule, sameSlot, refused }));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "probe.mjs"), probe);

    const result = lastJsonLine<{
      sameModule: boolean;
      sameSlot: boolean;
      refused: boolean;
    }>(run(process.execPath, ["probe.mjs"], consumer));

    expect(result.sameModule).toBe(true);
    expect(result.sameSlot).toBe(true);
    expect(result.refused).toBe(true);
  });
});

describe("rendering from a packed install", () => {
  const page = [
    "const count = property(41);",
    "const result = await renderToString(() => {",
    "  vbox(() => {",
    '    classes("page");',
    "    div(() => text(count.map((value) => \"n=\" + (value + 1))));",
    "  });",
    "});",
  ].join("\n");

  it("renders server-side against the stub", () => {
    const script = [
      'import { classes, div, installRuntime, property, renderToString, text, vbox } from "@anjunar/jfx-core";',
      'import { stubRuntime } from "@anjunar/jfx-core/stub";',
      "installRuntime(stubRuntime);",
      page,
      "console.log(JSON.stringify({ html: result.html, status: result.status }));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-stub.mjs"), script);

    const result = lastJsonLine<{ html: string; status: number }>(
      run(process.execPath, ["ssr-stub.mjs"], consumer)
    );

    expect(result.status).toBe(200);
    expect(result.html).toContain("n=42");
    expect(result.html).toContain('class="jfx-vbox page"');
  });

  it("installs the bridge before a dependent module creates state at module load", () => {
    writeFileSync(join(consumer, "page.mjs"), [
      'import "@anjunar/scalajs-jfx-bridge";',
      'import { property } from "@anjunar/jfx-core";',
      "export const count = property(41);",
    ].join("\n"));
    const script = [
      'import { count } from "./page.mjs";',
      'import { div, renderToString, text } from "@anjunar/jfx-core";',
      'const result = await renderToString(() => div(() => text(String(count.get + 1))));',
      "console.log(JSON.stringify(result));",
    ].join("\n");
    writeFileSync(join(consumer, "ssr-auto.mjs"), script);
    const result = lastJsonLine<{ html: string; status: number }>(
      run(process.execPath, ["ssr-auto.mjs"], consumer)
    );
    expect(result.status).toBe(200);
    expect(result.html).toContain("42");
  });

  it("refuses automatic installation over a different runtime", () => {
    const script = [
      'import { installRuntime, runtime } from "@anjunar/jfx-core";',
      'import { stubRuntime } from "@anjunar/jfx-core/stub";',
      "installRuntime(stubRuntime);",
      "let refused = false;",
      'try { await import("@anjunar/scalajs-jfx-bridge"); }',
      'catch (error) { refused = String(error).includes("would split the component tree"); }',
      "console.log(JSON.stringify({ refused, preserved: runtime() === stubRuntime }));",
    ].join("\n");
    writeFileSync(join(consumer, "auto-guard.mjs"), script);
    expect(lastJsonLine(run(process.execPath, ["auto-guard.mjs"], consumer)))
      .toEqual({ refused: true, preserved: true });
  });

  it("renders server-side after automatic installation through a named import", () => {
    const script = [
      'import { classes, div, property, renderToString, text, vbox } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      page,
      "console.log(JSON.stringify({ html: result.html, status: result.status, name: bridgeRuntime.name }));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-bridge.mjs"), script);

    const result = lastJsonLine<{ html: string; status: number; name: string }>(
      run(process.execPath, ["ssr-bridge.mjs"], consumer)
    );

    expect(result.name).toBe("jfx-bridge");
    expect(result.status).toBe(200);
    expect(result.html).toContain("n=42");
  });
});
