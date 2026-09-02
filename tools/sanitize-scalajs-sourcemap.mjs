// Fuellt `sourcesContent` fuer die Sourcemap-Eintraege, deren Pfad hier nicht
// existiert: Abhaengigkeiten ohne `-scalajs-mapSourceURI` liefern die absoluten
// Pfade ihrer Buildmaschine aus. Der Quelltext kommt aus dem passenden
// `-sources.jar` im Coursier-Cache. Ursache und Abschaltbedingung stehen bei
// `ScalaJsViteSupport.sanitizeScalaJsSourceMap`.

import {
  existsSync,
  readFileSync,
  readdirSync,
  writeFileSync
} from "node:fs"
import { spawnSync } from "node:child_process"
import { basename, dirname, resolve } from "node:path"
import { homedir } from "node:os"
import { argv, exit } from "node:process"

const sourceMapPath = argv[2]

if (!sourceMapPath) {
  console.error("Usage: node tools/sanitize-scalajs-sourcemap.mjs <path-to-main.js.map>")
  exit(1)
}

if (!existsSync(sourceMapPath)) {
  console.error(`Sourcemap not found: ${sourceMapPath}`)
  exit(1)
}

const sourceMapDirectory = dirname(sourceMapPath)
const sourceMap = JSON.parse(readFileSync(sourceMapPath, "utf8"))
const sourceJars = findSourceJars()
const jarEntriesCache = new Map()
const extractedSourceCache = new Map()

let rewrittenSources = 0

if (!Array.isArray(sourceMap.sources)) {
  console.error(`Invalid sourcemap: ${sourceMapPath}`)
  exit(1)
}

if (!Array.isArray(sourceMap.sourcesContent)) {
  sourceMap.sourcesContent = Array(sourceMap.sources.length).fill(null)
}

for (const [index, originalSource] of sourceMap.sources.entries()) {
  if (isRemoteSource(originalSource) || sourceExists(originalSource)) {
    continue
  }

  const content =
    sourceMap.sourcesContent[index] ?? resolveMissingSourceContent(originalSource)

  if (content == null) {
    continue
  }

  sourceMap.sourcesContent[index] = content
  rewrittenSources += 1
}

if (rewrittenSources > 0) {
  writeFileSync(sourceMapPath, JSON.stringify(sourceMap))
}

console.log(
  rewrittenSources > 0
    ? `Sanitized ${rewrittenSources} missing sourcemap source(s) in ${basename(sourceMapPath)}.`
    : `No sourcemap source rewrite needed for ${basename(sourceMapPath)}.`
)

function isRemoteSource(source) {
  return /^(?:[a-z]+:)?\/\//i.test(source) || source.startsWith("data:")
}

function sourceExists(source) {
  return existsSync(resolve(sourceMapDirectory, source))
}

function resolveMissingSourceContent(source) {
  const candidateSuffixes = buildCandidateSuffixes(source)

  for (const sourceJar of sourceJars) {
    const matchingEntry = listJarEntries(sourceJar).find((entry) =>
      candidateSuffixes.some((suffix) => entry.endsWith(suffix))
    )

    if (matchingEntry) {
      return readJarEntry(sourceJar, matchingEntry)
    }
  }

  return null
}

function buildCandidateSuffixes(source) {
  const normalized = source.replace(/\\/g, "/")
  const segments = normalized
    .split("/")
    .filter((segment) => segment.length > 0 && segment !== "." && segment !== "..")

  const suffixes = new Set()

  for (let size = 2; size <= Math.min(segments.length, 8); size += 1) {
    suffixes.add(segments.slice(-size).join("/"))
  }

  if (segments.length > 0) {
    suffixes.add(segments[segments.length - 1])
  }

  return [...suffixes]
}

function findSourceJars() {
  const roots = [
    process.env.LOCALAPPDATA && resolve(process.env.LOCALAPPDATA, "Coursier", "Cache"),
    resolve(homedir(), ".ivy2", "cache")
  ].filter(Boolean)

  const jars = []

  for (const root of roots) {
    if (!existsSync(root)) {
      continue
    }

    const stack = [root]

    while (stack.length > 0) {
      const current = stack.pop()

      for (const entry of readdirSync(current, { withFileTypes: true })) {
        const fullPath = resolve(current, entry.name)

        if (entry.isDirectory()) {
          stack.push(fullPath)
        } else if (entry.isFile() && entry.name.endsWith("-sources.jar")) {
          jars.push(fullPath)
        }
      }
    }
  }

  return jars
}

function listJarEntries(sourceJar) {
  if (jarEntriesCache.has(sourceJar)) {
    return jarEntriesCache.get(sourceJar)
  }

  const listed =
    process.platform === "win32"
      ? spawnSync(
          "powershell",
          [
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            `Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = [IO.Compression.ZipFile]::OpenRead('${escapePowerShellString(sourceJar)}'); try { $zip.Entries | ForEach-Object { $_.FullName } } finally { $zip.Dispose() }`
          ],
          { encoding: "utf8" }
        )
      : spawnSync("jar", ["tf", sourceJar], { encoding: "utf8" })

  if (listed.status !== 0) {
    jarEntriesCache.set(sourceJar, [])
    return []
  }

  const entries = listed.stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.endsWith("/"))

  jarEntriesCache.set(sourceJar, entries)
  return entries
}

function readJarEntry(sourceJar, entry) {
  const cacheKey = `${sourceJar}::${entry}`

  if (extractedSourceCache.has(cacheKey)) {
    return extractedSourceCache.get(cacheKey)
  }

  const extracted =
    process.platform === "win32"
      ? spawnSync(
          "powershell",
          [
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            `Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = [IO.Compression.ZipFile]::OpenRead('${escapePowerShellString(sourceJar)}'); try { $zipEntry = $zip.GetEntry('${escapePowerShellString(entry)}'); if ($null -eq $zipEntry) { exit 2 }; $reader = [IO.StreamReader]::new($zipEntry.Open()); try { [Console]::Out.Write($reader.ReadToEnd()) } finally { $reader.Dispose() } } finally { $zip.Dispose() }`
          ],
          { encoding: "utf8", maxBuffer: 1024 * 1024 * 8 }
        )
      : spawnSync("jar", ["xOf", sourceJar, entry], {
          encoding: "utf8",
          maxBuffer: 1024 * 1024 * 8
        })

  if (extracted.status !== 0) {
    throw new Error(extracted.stderr || `Unable to extract ${entry} from ${sourceJar}`)
  }

  extractedSourceCache.set(cacheKey, extracted.stdout)
  return extracted.stdout
}

function escapePowerShellString(value) {
  return value.replace(/'/g, "''")
}
