import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const packageRoot = resolve(process.cwd());
const repoRoot = resolve(packageRoot, "..", "..");
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
  if (entry?.endsWith(".js")) return run(process.execPath, [entry, ...args], cwd);
  return execFileSync("npm", [...args], {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    shell: process.platform === "win32",
  });
}

function pack(directory: string, into: string): string {
  const name = npm(["pack", "--pack-destination", into, "--silent"], directory)
    .trim()
    .split("\n")
    .pop()!;
  return join(into, name);
}

function fileSpecifier(path: string): string {
  return "file:" + path.replace(/\\/g, "/");
}

beforeAll(() => {
  consumer = mkdtempSync(join(tmpdir(), "jfx-webauthn-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);
  const tarball = pack(packageRoot, tarballs);
  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify({
      name: "jfx-webauthn-consumer",
      private: true,
      type: "module",
      dependencies: { "@anjunar/jfx-webauthn": fileSpecifier(tarball) },
    }),
  );
  npm(["install", "--no-audit", "--no-fund", "--silent"], consumer);
});

afterAll(() => {
  if (consumer) rmSync(consumer, { recursive: true, force: true });
});

describe("packed install", () => {
  it("ships the public build and omits sources and tests", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "jfx-webauthn");
    const entries = readdirSync(installed);
    expect(entries).toContain("dist");
    expect(entries).toContain("README.md");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");
  });

  it("resolves the public API under strict TypeScript", () => {
    mkdirSync(join(consumer, "src"));
    writeFileSync(
      join(consumer, "src", "probe.ts"),
      [
        'import { Base64Url, CredentialMediationRequirement, authenticateJson, isAvailable } from "@anjunar/jfx-webauthn";',
        "const encoded = Base64Url.encode(new Uint8Array([1, 2]));",
        "encoded satisfies string;",
        "const mediation: string = CredentialMediationRequirement.Conditional;",
        "isAvailable();",
        "authenticateJson({ challenge: encoded }, { mediation });",
      ].join("\n"),
    );
    writeFileSync(
      join(consumer, "tsconfig.json"),
      JSON.stringify({
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
      }),
    );
    const tsc = join(repoRoot, "node_modules", "typescript", "bin", "tsc");
    expect(() => run(process.execPath, [tsc, "-p", "tsconfig.json"], consumer)).not.toThrow();
  });
});
