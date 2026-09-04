/**
 * The consumer test for the controls package: does a foreign project get usable
 * controls by installing the tarballs and importing only through public
 * `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs install and resolve together.
 *  2. `@anjunar/jfx-controls` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + controls -- the regression test for a broken shipped
 *     declaration.
 *  4. SSR of a tab strip and a local-source table against the linked Scala.js
 *     bridge produces the expected HTML.
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

  consumer = mkdtempSync(join(tmpdir(), "jfx-controls-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const controlsTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "jfx-controls-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/jfx-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-jfx-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/jfx-controls": fileSpecifier(controlsTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core and controls both declare a peer on the CSS package
  // this probe does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-controls");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("table.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all three packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { div, installRuntime, listProperty, renderToString, text } from "@anjunar/jfx-core";',
      'import type { SsrResult } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { carousel, column, dataGrid, remoteSource, tab, tableView, tabs, virtualList } from "@anjunar/jfx-controls";',
      'import type { ColumnDef, RemoteSource } from "@anjunar/jfx-controls";',
      "",
      "interface Row { readonly name: string }",
      "",
      "const columns: readonly ColumnDef<Row>[] = [",
      '  column("Name", (row) => text(row.name), { sortable: true, sortKey: "name" }),',
      "];",
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  return renderToString(() => {",
      "    const rows = listProperty<Row>([{ name: \"a\" }]);",
      "    tabs([tab(\"One\", () => div(() => text(\"one\")))]);",
      "    tableView(rows, columns, { crawlable: true, crawlId: \"t\" });",
      "    carousel(rows, (row) => div(() => text(row.name)));",
      "    dataGrid(rows, (row) => div(() => text(row?.name ?? \"\")));",
      "    virtualList(rows, (row) => div(() => text(row?.name ?? \"\")));",
      "    const remote: RemoteSource<Row, { offset: number }> = remoteSource({",
      "      initialQuery: { offset: 0 },",
      "      initial: [{ name: \"r\" }],",
      "      load: (q) => Promise.resolve({ items: [{ name: \"r\" }], offset: q.offset }),",
      "    });",
      "    tableView(remote, columns);",
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

describe("rendering controls from a packed install", () => {
  it("renders a tab strip and a table against the bridge", () => {
    const script = [
      'import { div, installRuntime, listProperty, renderToString, text } from "@anjunar/jfx-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";',
      'import { tab, tableView, tabs } from "@anjunar/jfx-controls";',
      "installRuntime(bridgeRuntime);",
      "const result = await renderToString(() => {",
      "  const rows = listProperty([{ title: \"Dune\" }, { title: \"Solaris\" }]);",
      "  tabs([tab(\"A\", () => div(() => text(\"panel a\"))), tab(\"B\", () => div(() => text(\"panel b\")))], { selectedIndex: 1 });",
      "  tableView(rows, [{ text: \"Title\", cell: (r) => text(r.title) }], { crawlable: true, crawlId: \"probe\" });",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  panelB: result.html.includes(\"panel b\"),",
      "  panelA: result.html.includes(\"panel a\"),",
      "  dune: result.html.includes(\"Dune\"),",
      "  solaris: result.html.includes(\"Solaris\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-controls.mjs"), script);

    const result = lastJsonLine<{
      status: number;
      panelB: boolean;
      panelA: boolean;
      dune: boolean;
      solaris: boolean;
    }>(run(process.execPath, ["ssr-controls.mjs"], consumer));

    expect(result.status).toBe(200);
    expect(result.panelB).toBe(true);
    expect(result.panelA).toBe(false);
    expect(result.dune).toBe(true);
    expect(result.solaris).toBe(true);
  });
});
