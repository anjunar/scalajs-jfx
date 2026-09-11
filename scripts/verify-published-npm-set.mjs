import { spawnSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const packageDirectories = [
  "scalajs-ui",
  "scalajs-ui-core",
  "scalajs-ui-bridge",
  "scalajs-ui-json",
  "scalajs-ui-router",
  "scalajs-ui-controls",
  "scalajs-ui-viewport",
  "scalajs-ui-forms",
  "scalajs-ui-editor",
  "scalajs-ui-webauthn",
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
const npmCache = resolve(consumerDirectory, ".npm-cache");
await writeFile(
  resolve(consumerDirectory, "package.json"),
  JSON.stringify({ name: "ui-registry-consumer", version: "0.0.0", private: true }, null, 2),
  "utf8"
);

const specifications = manifests.map((manifest) => `${manifest.name}@${manifest.version}`);
const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const pending = new Map(manifests.map((manifest) => [manifest.name, manifest.version]));
const registryAttempts = 60;
const registryPollDelayMs = 5_000;

for (let attempt = 1; attempt <= registryAttempts && pending.size > 0; attempt += 1) {
  for (const [name, version] of pending) {
    const visible = spawnSync(
      npm,
      ["--cache", npmCache, "view", `${name}@${version}`, "version", "--json", "--prefer-online"],
      { cwd: consumerDirectory, encoding: "utf8", shell: process.platform === "win32" }
    );
    if (visible.status === 0 && parseVersion(visible.stdout) === version) pending.delete(name);
  }

  if (pending.size > 0 && attempt < registryAttempts) {
    console.log(
      `Waiting for npm registry visibility (${attempt}/${registryAttempts}): ${[...pending.entries()]
        .map(([name, version]) => `${name}@${version}`)
        .join(", ")}`
    );
    await new Promise((resolveDelay) => setTimeout(resolveDelay, registryPollDelayMs));
  }
}

if (pending.size > 0) {
  throw new Error(
    `Packages are not visible on npm: ${[...pending.entries()]
      .map(([name, version]) => `${name}@${version}`)
      .join(", ")}`
  );
}

const result = spawnSync(
  npm,
  [
    "--cache",
    npmCache,
    "install",
    "--no-audit",
    "--no-fund",
    "--ignore-scripts",
    "--prefer-online",
    ...specifications,
  ],
  { cwd: consumerDirectory, encoding: "utf8", stdio: "inherit", shell: process.platform === "win32" }
);
if (result.error) throw result.error;
if (result.status !== 0) {
  throw new Error(`Clean registry consumer install failed with exit code ${result.status}.`);
}

console.log(`Clean registry consumer installed ${specifications.length} UI packages successfully.`);

function parseVersion(output) {
  try {
    return JSON.parse(output);
  } catch {
    return output.trim();
  }
}
