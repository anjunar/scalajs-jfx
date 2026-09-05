import { spawnSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const packageDirectories = [
  "scalajs-jfx",
  "jfx-core",
  "scalajs-jfx-bridge",
  "jfx-json",
  "jfx-router",
  "jfx-controls",
  "jfx-viewport",
  "jfx-forms",
  "jfx-editor",
];

const manifests = await Promise.all(
  packageDirectories.map(async (directory) =>
    JSON.parse(await readFile(resolve(repositoryRoot, "npm", directory, "package.json"), "utf8"))
  )
);
const versions = new Set(manifests.map((manifest) => manifest.version));
if (versions.size !== 1) {
  throw new Error(`The npm release set has mixed versions: ${[...versions].join(", ")}`);
}

const consumerDirectory = resolve(repositoryRoot, "target", "npm-registry-consumer");
await rm(consumerDirectory, { recursive: true, force: true });
await mkdir(consumerDirectory, { recursive: true });
await writeFile(
  resolve(consumerDirectory, "package.json"),
  JSON.stringify({ name: "jfx-registry-consumer", version: "0.0.0", private: true }, null, 2),
  "utf8"
);

const specifications = manifests.map((manifest) => `${manifest.name}@${manifest.version}`);
const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const result = spawnSync(
  npm,
  ["install", "--no-audit", "--no-fund", "--ignore-scripts", ...specifications],
  { cwd: consumerDirectory, encoding: "utf8", stdio: "inherit", shell: process.platform === "win32" }
);
if (result.error) throw result.error;
if (result.status !== 0) {
  throw new Error(`Clean registry consumer install failed with exit code ${result.status}.`);
}

console.log(`Clean registry consumer installed ${specifications.length} JFX packages successfully.`);
