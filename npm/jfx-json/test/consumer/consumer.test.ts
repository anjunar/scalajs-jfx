import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const packageRoot = resolve(process.cwd());
const repoRoot = resolve(packageRoot, "..", "..");
let consumer = "";
function run(command: string, args: readonly string[], cwd: string): string {
  return execFileSync(command, [...args], { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}
function npm(args: readonly string[], cwd: string): string {
  const entry = process.env["npm_execpath"];
  return entry?.endsWith(".js") ? run(process.execPath, [entry, ...args], cwd) : run("npm", args, cwd);
}
function pack(directory: string, into: string): string {
  const name = npm(["pack", "--pack-destination", into, "--silent"], directory).trim().split("\n").pop()!;
  return join(into, name);
}
function fileSpecifier(path: string): string { return "file:" + path.replace(/\\/g, "/"); }

beforeAll(() => {
  consumer = mkdtempSync(join(tmpdir(), "jfx-json-consumer-"));
  const tarballs = join(consumer, "tarballs"); mkdirSync(tarballs);
  const tarball = pack(packageRoot, tarballs);
  const coreTarball = pack(join(repoRoot, "npm", "jfx-core"), tarballs);
  writeFileSync(join(consumer, "package.json"), JSON.stringify({ name: "probe", private: true, type: "module", dependencies: { "@anjunar/jfx-json": fileSpecifier(tarball), "@anjunar/jfx-core": fileSpecifier(coreTarball) } }));
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});
afterAll(() => { if (consumer) rmSync(consumer, { recursive: true, force: true }); });

describe("packed install", () => {
  it("ships only the public build", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-json");
    const entries = readdirSync(installed);
    expect(entries).toContain("dist");
    expect(entries).toContain("README.md");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");
  });
  it("resolves its public types under strict TypeScript", () => {
    mkdirSync(join(consumer, "src"));
    writeFileSync(join(consumer, "src", "probe.ts"), 'import { JsonMapper, jsonSchema, jsonField } from "@anjunar/jfx-json";\nconst schema = jsonSchema(() => ({ value: "" }), { value: jsonField() });\nconst value = JsonMapper.deserialize({ value: "ok" }, schema);\nvalue.value satisfies string;\n');
    writeFileSync(join(consumer, "tsconfig.json"), JSON.stringify({ compilerOptions: { target: "ES2022", module: "ES2022", moduleResolution: "bundler", lib: ["ES2022"], strict: true, noEmit: true, skipLibCheck: false }, include: ["src"] }));
    const tsc = join(repoRoot, "node_modules", "typescript", "bin", "tsc");
    expect(() => run(process.execPath, [tsc, "-p", "tsconfig.json"], consumer)).not.toThrow();
  });
});
