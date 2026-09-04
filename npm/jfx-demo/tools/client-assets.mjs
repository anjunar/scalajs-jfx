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

/** Client entry, as configured in vite.config.ts's `build.rollupOptions.input`. */
const entryId = "src/entry-client.ts";

/** In the dev server Vite serves the source directly; its own client script is
 * injected separately by `vite.transformIndexHtml`. */
export function developmentAssets() {
  return [
    {
      key: "asset:stylesheet",
      tagName: "link",
      attributes: [
        ["rel", "stylesheet"],
        ["href", "/src/styles/style.css"],
      ],
    },
    {
      key: "asset:script",
      tagName: "script",
      attributes: [
        ["type", "module"],
        ["src", `/${entryId}`],
      ],
    },
  ];
}

/** In production the file names are in Vite's manifest. */
export async function productionAssets(clientDist) {
  const manifestPath = resolve(clientDist, ".vite", "manifest.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const entry = manifest[entryId];

  if (!entry) {
    throw new Error(
      `${manifestPath} does not know the entry "${entryId}". ` +
        `Present: ${Object.keys(manifest).join(", ")}`
    );
  }

  const stylesheets = (entry.css ?? []).map((file, index) => ({
    key: `asset:stylesheet:${index}`,
    tagName: "link",
    attributes: [
      ["rel", "stylesheet"],
      ["crossorigin", ""],
      ["href", `/${file}`],
    ],
  }));

  return [
    ...stylesheets,
    {
      key: "asset:script",
      tagName: "script",
      attributes: [
        ["type", "module"],
        ["crossorigin", ""],
        ["src", `/${entry.file}`],
      ],
    },
  ];
}
