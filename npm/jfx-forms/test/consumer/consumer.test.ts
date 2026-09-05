/**
 * The consumer test for the forms package: does a foreign project get a
 * usable forms API by installing the tarballs and importing only through
 * public `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs (core, controls, viewport, bridge, forms) install
 *     and resolve together.
 *  2. `@anjunar/jfx-forms` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + forms -- the regression test for a broken shipped
 *     declaration.
 *  4. SSR of a form with a validated input against the linked Scala.js bridge
 *     produces the expected HTML.
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

  consumer = mkdtempSync(join(tmpdir(), "jfx-forms-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const controlsTarball = pack(controlsPackage, tarballs);
  const viewportTarball = pack(viewportPackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const formsTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-forms-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/jfx-controls": fileSpecifier(controlsTarball),
          "@anjunar/jfx-viewport": fileSpecifier(viewportTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/jfx-forms": fileSpecifier(formsTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core/controls/viewport/forms all declare a peer on the
  // CSS package this probe does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-forms");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("form.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { installRuntime, mount, property, renderToString } from "@anjunar/jfx-core";',
      'import type { SsrResult } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { form, input, notBlank } from "@anjunar/jfx-forms";',
      'import type { FormModel, FormOptions } from "@anjunar/jfx-forms";',
      "",
      "const model: FormModel = { name: property(\"\") };",
      "const options: FormOptions = { schema: { name: [notBlank()] } };",
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  return renderToString(() => {",
      "    form(model, options, () => {",
      "      input(\"name\");",
      "    });",
      "  });",
      "}",
      "",
      "export function boot(root: Element): void {",
      "  installRuntime(bridgeRuntime);",
      "  mount(root, () => {",
      "    form(model, options, () => input(\"name\"));",
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

describe("rendering a form from a packed install", () => {
  it("renders a validated input against the bridge", () => {
    const script = [
      'import { installRuntime, property, renderToString } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { form, input, notBlank } from "@anjunar/jfx-forms";',
      "installRuntime(bridgeRuntime);",
      "const model = { name: property(\"Ada\") };",
      "const result = await renderToString(() => {",
      "  form(model, { schema: { name: [notBlank()] } }, () => {",
      "    input(\"name\");",
      "  });",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  hasForm: result.html.includes(\"<form\"),",
      "  hasValue: result.html.includes('value=\"Ada\"'),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-form.mjs"), script);

    const result = lastJsonLine<{ status: number; hasForm: boolean; hasValue: boolean }>(
      run(process.execPath, ["ssr-form.mjs"], consumer)
    );

    expect(result.status).toBe(200);
    expect(result.hasForm).toBe(true);
    expect(result.hasValue).toBe(true);
  });
});
