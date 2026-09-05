// Sammelt jede `i18n`/`i18nc`-Nachricht aus TypeScript-Quellen und schreibt einen Katalog-
// Entwurf -- Schritt 7 aus JAVASCRIPT_API.md §9, das TypeScript-Gegenstueck zu dem, was
// `I18nInterpolator.scala`s Makro fuer Scala zur Compilezeit erledigt.
//
// `npm/jfx-core/src/i18n.ts`s `i18n`/`i18nc` sind reine Laufzeitfunktionen: ohne Makro gibt es
// keinen Compilefehler, wenn ein Platzhalter kein `named("name", value)` ist -- das faellt sonst
// erst beim ersten Rendern auf, per `I18nError`. Dieses Skript findet solche Stellen vorher, indem
// es den TypeScript-AST liest statt die App auszufuehren, und schreibt dabei gleich den Katalog-
// Entwurf, den ein Uebersetzer sonst von Hand aus jedem Aufruf abtippen muesste.
//
// Aufruf: node tools/i18n-extract.mjs <verzeichnis>... [--out <datei.json>]
// Ohne Argumente: npm/jfx-demo/src, kein --out (nur Bericht auf stdout, Exitcode bei Fehlern).

import { readFileSync, readdirSync, statSync, writeFileSync } from "node:fs"
import { dirname, extname, join, relative, resolve } from "node:path"
import { fileURLToPath } from "node:url"

import ts from "typescript"

import { fingerprintOf } from "../npm/jfx-core/dist/i18n.js"

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")

const SKIP_DIRS = new Set(["node_modules", "dist", ".git"])
const SOURCE_EXTENSIONS = new Set([".ts", ".tsx"])

function walk(root) {
  const files = []
  const stack = [root]
  while (stack.length > 0) {
    const dir = stack.pop()
    for (const entry of readdirSync(dir)) {
      if (SKIP_DIRS.has(entry)) continue
      const path = join(dir, entry)
      const stats = statSync(path)
      if (stats.isDirectory()) stack.push(path)
      else if (SOURCE_EXTENSIONS.has(extname(path))) files.push(path)
    }
  }
  return files
}

/** One `named("x", value)` substitution's static name, or `null` if it isn't one. */
function namedPlaceholder(expression) {
  if (
    ts.isCallExpression(expression) &&
    ts.isIdentifier(expression.expression) &&
    expression.expression.text === "named" &&
    expression.arguments.length >= 1 &&
    ts.isStringLiteralLike(expression.arguments[0])
  ) {
    return expression.arguments[0].text
  }
  return null
}

/** The literal parts and substitution expressions of a template literal, `i18n.ts`'s own shape. */
function templateParts(template) {
  if (ts.isNoSubstitutionTemplateLiteral(template)) {
    return { strings: [template.text], expressions: [] }
  }
  const strings = [template.head.text, ...template.templateSpans.map((span) => span.literal.text)]
  const expressions = template.templateSpans.map((span) => span.expression)
  return { strings, expressions }
}

function locationOf(sourceFile, node) {
  const { line, character } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile))
  return { line: line + 1, column: character + 1 }
}

/**
 * Walks one source file for `i18n\`...\`` and `i18nc\`...\`(context)` calls.
 *
 * `context` must be a string literal to be scaffolded here -- a dynamic context is legal at
 * runtime (`i18nc.ts` accepts any `string`) but has no fixed catalog entry to generate, so it is
 * reported as found and skipped rather than guessed at.
 */
function extractFromFile(filePath, projectRoot) {
  const relativePath = relative(projectRoot, filePath).split("\\").join("/")
  const source = readFileSync(filePath, "utf8")
  const sourceFile = ts.createSourceFile(filePath, source, ts.ScriptTarget.Latest, true)

  const entries = []
  const errors = []

  function reportTemplate(template, context, contextIsStatic, callNode) {
    const { strings, expressions } = templateParts(template)
    const placeholders = []

    for (const expression of expressions) {
      const name = namedPlaceholder(expression)
      if (name === null) {
        const { line, column } = locationOf(sourceFile, expression)
        errors.push(
          `${relativePath}:${line}:${column}: i18n placeholder is not named("name", value) -- ` +
            `this throws I18nError at runtime.`
        )
        placeholders.push("?")
      } else {
        placeholders.push(name)
      }
    }

    if (!contextIsStatic) {
      const { line, column } = locationOf(sourceFile, callNode)
      console.warn(
        `${relativePath}:${line}:${column}: i18nc's context is not a string literal -- skipped ` +
          `from the scaffold (it still works at runtime).`
      )
      return
    }

    const sourceText = strings.reduce(
      (acc, part, index) => acc + part + (index < placeholders.length ? `{${placeholders[index]}}` : ""),
      ""
    )
    const { line, column } = locationOf(sourceFile, template)

    entries.push({
      source: sourceText,
      context: context ?? undefined,
      fingerprint: fingerprintOf(sourceText, context),
      placeholders,
      file: relativePath,
      line,
      column,
    })
  }

  function visit(node) {
    if (ts.isTaggedTemplateExpression(node) && ts.isIdentifier(node.tag) && node.tag.text === "i18n") {
      reportTemplate(node.template, undefined, true, node)
    } else if (
      ts.isCallExpression(node) &&
      ts.isTaggedTemplateExpression(node.expression) &&
      ts.isIdentifier(node.expression.tag) &&
      node.expression.tag.text === "i18nc" &&
      node.arguments.length === 1
    ) {
      const [contextArg] = node.arguments
      const isStatic = ts.isStringLiteralLike(contextArg)
      reportTemplate(node.expression.template, isStatic ? contextArg.text : undefined, isStatic, node)
    }
    ts.forEachChild(node, visit)
  }

  visit(sourceFile)
  return { entries, errors }
}

function parseArgs(argv) {
  const roots = []
  let out = null
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--out") {
      out = argv[++i]
    } else {
      roots.push(argv[i])
    }
  }
  return { roots: roots.length > 0 ? roots : ["npm/jfx-demo/src"], out }
}

const { roots, out } = parseArgs(process.argv.slice(2))

const allEntries = []
const allErrors = []

for (const root of roots) {
  const absoluteRoot = join(projectRoot, root)
  for (const file of walk(absoluteRoot)) {
    const { entries, errors } = extractFromFile(file, projectRoot)
    allEntries.push(...entries)
    allErrors.push(...errors)
  }
}

// Same fingerprint, different source text is a real collision (or a copy-paste bug); same
// fingerprint and same source text is one message used twice, kept once.
const byFingerprint = new Map()
for (const entry of allEntries) {
  const existing = byFingerprint.get(entry.fingerprint)
  if (existing === undefined) {
    byFingerprint.set(entry.fingerprint, entry)
  } else if (existing.source !== entry.source) {
    allErrors.push(
      `Fingerprint collision between "${existing.source}" (${existing.file}:${existing.line}) ` +
        `and "${entry.source}" (${entry.file}:${entry.line}).`
    )
  }
}

const scaffold = [...byFingerprint.values()].sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line)

console.log(`Found ${scaffold.length} distinct i18n message(s) across ${roots.join(", ")}.`)

if (out) {
  writeFileSync(out, JSON.stringify(scaffold, null, 2) + "\n", "utf8")
  console.log(`Wrote scaffold: ${out}`)
}

if (allErrors.length > 0) {
  console.error(`\n${allErrors.length} problem(s):`)
  for (const error of allErrors) console.error(`  ${error}`)
  process.exitCode = 1
}
