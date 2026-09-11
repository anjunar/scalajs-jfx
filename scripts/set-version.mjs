import { readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const arguments_ = process.argv.slice(2);
const checkOnly = arguments_[0] === "--check";
const version = arguments_[checkOnly ? 1 : 0];

if (!version || !isSemVer(version)) {
  console.error("Usage: node scripts/set-version.mjs [--check] <version>");
  console.error("Example: node scripts/set-version.mjs 3.0.1");
  process.exit(2);
}

const packageRoot = resolve(repositoryRoot, "npm");
const packageDirectories = (await readdir(packageRoot, { withFileTypes: true }))
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();

const workspacePackages = [];
for (const directory of packageDirectories) {
  const path = resolve(packageRoot, directory, "package.json");
  let manifest;
  try {
    manifest = JSON.parse(await readFile(path, "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT") continue;
    throw error;
  }

  workspacePackages.push({ directory, manifest, path });
}

const releasePackages = workspacePackages.filter(
  ({ manifest }) => !manifest.private && isUiPackage(manifest.name)
);
if (releasePackages.length === 0) {
  throw new Error("No publishable UI packages were found below npm/.");
}

const releasePackageNames = new Set(releasePackages.map(({ manifest }) => manifest.name));
const changes = [];

const rootManifestPath = resolve(repositoryRoot, "package.json");
const rootManifest = JSON.parse(await readFile(rootManifestPath, "utf8"));
for (const { manifest, path } of [
  { manifest: rootManifest, path: rootManifestPath },
  ...workspacePackages,
]) {
  await updateManifest(path, manifest, releasePackageNames, version);
}

await updatePackageLock(workspacePackages, releasePackageNames, version);
await updateBuild(version);
await updateScalaReadmes(version);
await updateDemos(version);

if (changes.length > 0) {
  const verb = checkOnly ? "need updating" : "updated";
  console.log(`${changes.length} files ${verb} for UI ${version}:`);
  for (const path of changes) console.log(`- ${relative(repositoryRoot, path)}`);
}

if (checkOnly && changes.length > 0) process.exit(1);
if (changes.length === 0) console.log(`All version references already match UI ${version}.`);

function isSemVer(value) {
  return /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.test(value);
}

function isUiPackage(name) {
  return (
    typeof name === "string" &&
    (name.startsWith("@anjunar/scalajs-ui-") ||
      name === "@anjunar/scalajs-ui" ||
      name === "@anjunar/scalajs-ui-bridge")
  );
}

function updateInternalDependencyRanges(manifest, packageNames, nextVersion) {
  for (const section of ["dependencies", "optionalDependencies", "peerDependencies"]) {
    const dependencies = manifest[section];
    if (!dependencies) continue;

    for (const name of Object.keys(dependencies)) {
      if (packageNames.has(name) && dependencies[name] !== "*") {
        dependencies[name] = `^${nextVersion}`;
      }
    }
  }
}

async function updateManifest(path, manifest, packageNames, nextVersion) {
  const current = await readFile(path, "utf8");
  let next = replaceExactlyOnce(
    current,
    /^(\s*"version"\s*:\s*")[^"]+("\s*,?\s*)$/m,
    `$1${nextVersion}$2`,
    `${relative(repositoryRoot, path)} version`
  );

  for (const section of ["dependencies", "optionalDependencies", "peerDependencies"]) {
    const dependencies = manifest[section];
    if (!dependencies) continue;

    for (const name of Object.keys(dependencies)) {
      if (!packageNames.has(name) || dependencies[name] === "*") continue;
      const sectionPattern = new RegExp(`("${section}"\\s*:\\s*\\{)([\\s\\S]*?)(\\n\\s*\\})`);
      const sectionMatch = next.match(sectionPattern);
      if (!sectionMatch) throw new Error(`Could not find ${section} in ${relative(repositoryRoot, path)}.`);

      const dependencyPattern = new RegExp(`("${escapeRegExp(name)}"\\s*:\\s*")[^"]+("\\s*,?)`);
      const updatedBody = replaceExactlyOnce(
        sectionMatch[2],
        dependencyPattern,
        `$1^${nextVersion}$2`,
        `${name} in ${section} of ${relative(repositoryRoot, path)}`
      );
      next = next.replace(sectionPattern, `$1${updatedBody}$3`);
    }
  }

  await record(path, current, next);
}

async function updateJson(path, value) {
  const current = await readFile(path, "utf8");
  const newline = current.includes("\r\n") ? "\r\n" : "\n";
  const next = `${JSON.stringify(value, null, 2).replaceAll("\n", newline)}${newline}`;
  await record(path, current, next);
}

async function updatePackageLock(packages, packageNames, nextVersion) {
  const path = resolve(repositoryRoot, "package-lock.json");
  const lock = JSON.parse(await readFile(path, "utf8"));

  if (!lock.packages?.[""]) throw new Error("package-lock.json has no root package entry.");
  lock.version = nextVersion;
  lock.packages[""].version = nextVersion;

  for (const { directory } of packages) {
    const entry = lock.packages?.[`npm/${directory}`];
    if (!entry) throw new Error(`package-lock.json has no workspace entry for npm/${directory}.`);
    entry.version = nextVersion;
    updateInternalDependencyRanges(entry, packageNames, nextVersion);
  }

  await updateJson(path, lock);
}

async function updateBuild(nextVersion) {
  const path = resolve(repositoryRoot, "build.sbt");
  const current = await readFile(path, "utf8");
  let next = replaceExactlyOnce(
    current,
    /^(\s*version\s*:=\s*")[^"]+("\s*)$/m,
    `$1${nextVersion}$2`,
    "build.sbt version"
  );
  next = next.replace(
    /(scalajs-ui-[^\s\\/]+_sjs\d+_\d+-)\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?(?=\.jar)/g,
    `$1${nextVersion}`
  );
  await record(path, current, next);
}

async function updateScalaReadmes(nextVersion) {
  const candidates = [resolve(repositoryRoot, "README.md")];
  for (const entry of await readdir(repositoryRoot, { withFileTypes: true })) {
    if (entry.isDirectory() && entry.name.startsWith("scalajs-ui-")) {
      candidates.push(resolve(repositoryRoot, entry.name, "README.md"));
    }
  }

  for (const path of candidates) {
    let current;
    try {
      current = await readFile(path, "utf8");
    } catch (error) {
      if (error?.code === "ENOENT") continue;
      throw error;
    }

    let next = current.replace(
      /(libraryDependencies\s*\+=\s*"com\.anjunar"\s*%%\s*"scalajs-ui-[^"]+"\s*%\s*")[^"]+("\s*)/g,
      `$1${nextVersion}$2`
    );
    if (path === candidates[0]) {
      next = next.replace(
        /(The repository is on the `)[^`]+(` release line)/,
        `$1${nextVersion}$2`
      );
    }
    await record(path, current, next);
  }
}

async function updateDemos(nextVersion) {
  const scalaPath = resolve(repositoryRoot, "scalajs-ui-demo/src/main/scala-3/app/App.scala");
  const scalaCurrent = await readFile(scalaPath, "utf8");
  const scalaNext = scalaCurrent
    .replace(/routerLink\("v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?"\)/, `routerLink("v${nextVersion}")`)
    .replace(
      /(repo1\.maven\.org\/maven2\/com\/anjunar\/scalajs-ui-core_sjs1_3\/)\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?(\/)/,
      `$1${nextVersion}$2`
    )
    .replace(/text\("v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?"\)/, `text("v${nextVersion}")`);
  await record(scalaPath, scalaCurrent, scalaNext);

  const typescriptPath = resolve(repositoryRoot, "npm/scalajs-ui-demo/src/app/shell.ts");
  const typescriptCurrent = await readFile(typescriptPath, "utf8");
  const typescriptNext = typescriptCurrent
    .replace(/"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?",/, `"v${nextVersion}",`)
    .replace(
      /(npmjs\.com\/package\/@anjunar\/scalajs-ui-core\/v\/)\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?(?=")/,
      `$1${nextVersion}`
    );
  await record(typescriptPath, typescriptCurrent, typescriptNext);

  const starterPath = resolve(repositoryRoot, "docs/starters/build.sbt");
  const starterCurrent = await readFile(starterPath, "utf8");
  const starterNext = starterCurrent.replace(
    /(libraryDependencies\s*\+=\s*"com\.anjunar"\s*%%\s*"scalajs-ui-[^"]+"\s*%\s*")[^"]+("\s*)/g,
    `$1${nextVersion}$2`
  );
  await record(starterPath, starterCurrent, starterNext);
}

function replaceExactlyOnce(source, pattern, replacement, description) {
  const matches = source.match(new RegExp(pattern.source, pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`));
  if (matches?.length !== 1) {
    throw new Error(`Expected exactly one ${description}, found ${matches?.length ?? 0}.`);
  }
  return source.replace(pattern, replacement);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function record(path, current, next) {
  if (current === next) return;
  changes.push(path);
  if (!checkOnly) await writeFile(path, next, "utf8");
}
