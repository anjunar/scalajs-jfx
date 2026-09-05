/**
 * The consumer test for the editor package: does a foreign project get a
 * usable editor API by installing the tarballs and importing only through
 * public `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs (core, forms, viewport, bridge, editor) install and
 *     resolve together.
 *  2. `@anjunar/jfx-editor` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + forms + editor -- the regression test for a broken
 *     shipped declaration.
 *  4. SSR of a form-bound editor against the linked Scala.js bridge produces
 *     the expected HTML.
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
const controlsPackage = join(repoRoot, "npm", "jfx-controls");
const viewportPackage = join(repoRoot, "npm", "jfx-viewport");
const formsPackage = join(repoRoot, "npm", "jfx-forms");
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

  consumer = mkdtempSync(join(tmpdir(), "jfx-editor-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const controlsTarball = pack(controlsPackage, tarballs);
  const viewportTarball = pack(viewportPackage, tarballs);
  const formsTarball = pack(formsPackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const editorTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-editor-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/jfx-controls": fileSpecifier(controlsTarball),
          "@anjunar/jfx-viewport": fileSpecifier(viewportTarball),
          "@anjunar/jfx-forms": fileSpecifier(formsTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/jfx-editor": fileSpecifier(editorTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core/controls/viewport/forms/editor all declare a peer
  // on the CSS package this probe does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-editor");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("editor.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { installRuntime, mount, property, renderToString } from "@anjunar/jfx-core";',
      'import type { SsrResult } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { form } from "@anjunar/jfx-forms";',
      'import { viewport } from "@anjunar/jfx-viewport";',
      'import { editor } from "@anjunar/jfx-editor";',
      'import type { EditorOptions, Markdown } from "@anjunar/jfx-editor";',
      "",
      'const initial: Markdown = "";',
      'const model = { body: property(initial) };',
      'const options: EditorOptions = { plugins: ["base", "heading"] };',
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  return renderToString(() => {",
      "    viewport(() => form(model, {}, () => editor(\"body\", options)));",
      "  });",
      "}",
      "",
      "export function boot(root: Element): void {",
      "  installRuntime(bridgeRuntime);",
      "  mount(root, () => {",
      "    viewport(() => form(model, {}, () => editor(\"body\", options)));",
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

describe("rendering a Markdown editor from a packed install", () => {
  it("renders against the bridge", () => {
    const script = [
      'import { installRuntime, property, renderToString } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { viewport } from "@anjunar/jfx-viewport";',
      'import { editor } from "@anjunar/jfx-editor";',
      "installRuntime(bridgeRuntime);",
      "const result = await renderToString(() => {",
      "  viewport(() => {",
      "    editor(\"body\", { standalone: true, value: \"## Ada\", editable: false, plugins: [\"base\"] });",
      "  });",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  hasEditor: result.html.includes('name=\"body\"'),",
      "  hasValue: result.html.includes(\"Ada\"),",
      "  hasHeading: result.html.includes(\"<h2\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-editor.mjs"), script);

    const result = lastJsonLine<{ status: number; hasEditor: boolean; hasValue: boolean; hasHeading: boolean }>(
      run(process.execPath, ["ssr-editor.mjs"], consumer)
    );

    expect(result.status).toBe(200);
    expect(result.hasEditor).toBe(true);
    expect(result.hasValue).toBe(true);
    expect(result.hasHeading).toBe(true);
  });
});
