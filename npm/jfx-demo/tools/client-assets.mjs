// The `<link>`/`<script>` head entries for the built client bundle -- the
// only part of the document `src/app/document.ts` cannot render itself: the
// file names carry a build-time content hash, and in development they don't
// exist as files at all. Mirrors the repo root's own `tools/client-assets.mjs`,
// adapted to this app's `HeadEntry` shape (`{ key, tagName, attributes }`)
// since there's no server-side Scala counterpart here to hand entries back.
//
// Passed into `render(path, assets)` and pushed into the head as ordinary
// entries -- no string-splicing into a template.

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

/** Client entries, as configured in vite.config.ts's `build.rollupOptions.input`. */
const scriptEntryId = "src/entry-client.ts";
const stylesheetEntryId = "src/styles/style.css";

/** In the dev server Vite serves the source directly; its own client script is
 * injected separately by `vite.transformIndexHtml`. */
export function developmentAssets() {
  return [
    {
      key: "asset:stylesheet",
      tagName: "link",
      attributes: [
        ["rel", "stylesheet"],
        ["href", `/${stylesheetEntryId}`],
      ],
    },
    {
      key: "asset:script",
      tagName: "script",
      attributes: [
        ["type", "module"],
        ["src", `/${scriptEntryId}`],
      ],
    },
  ];
}

/** In production the file names are in Vite's manifest. */
export async function productionAssets(clientDist) {
  const manifestPath = resolve(clientDist, ".vite", "manifest.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const scriptEntry = requiredEntry(manifest, scriptEntryId, manifestPath);
  const stylesheetEntry = requiredEntry(manifest, stylesheetEntryId, manifestPath);
  const stylesheetFile = stylesheetEntry.file;

  if (!stylesheetFile.endsWith(".css")) {
    throw new Error(
      `${manifestPath} does not map "${stylesheetEntryId}" to CSS: ${stylesheetFile}`
    );
  }

  return [
    {
      key: "asset:stylesheet",
      tagName: "link",
      attributes: [
        ["rel", "stylesheet"],
        ["crossorigin", ""],
        ["href", publicAssetUrl(stylesheetFile)],
      ],
    },
    {
      key: "asset:script",
      tagName: "script",
      attributes: [
        ["type", "module"],
        ["crossorigin", ""],
        ["src", publicAssetUrl(scriptEntry.file)],
      ],
    },
  ];
}

function requiredEntry(manifest, entryId, manifestPath) {
  const entry = manifest[entryId];
  if (entry) return entry;

  throw new Error(
    `${manifestPath} does not know the entry "${entryId}". ` +
      `Present: ${Object.keys(manifest).join(", ")}`
  );
}

function publicBasePath() {
  const value = process.env.JFX_BASE_PATH;
  if (!value || value === "/") return "";
  const withLeadingSlash = value.startsWith("/") ? value : `/${value}`;
  return withLeadingSlash.replace(/\/+$/, "");
}

function publicAssetUrl(file) {
  return `${publicBasePath()}/${file}`.replace(/\/{2,}/g, "/");
}
