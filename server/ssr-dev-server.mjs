import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { startSsrDevServer } from "@anjunar/scalajs-jfx2/ssr/dev-server";

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(__dirname, "..");
const webappDir = resolve(rootDir, "application", "src", "main", "webapp");
const templatePath = resolve(webappDir, "index.html");
const basePath = "/scalajs-jfx2";

const port = Number.parseInt(process.env.PORT ?? "5173", 10);

await startSsrDevServer({
  port,
  templatePath,
  entryModule: "/src/main.js",
  normalizeRenderPath,
});

console.log(`JFX2 Vite SSR dev server running at http://localhost:${port}${basePath}/`);
console.log(`Raw SSR fragment: http://localhost:${port}/__ssr?path=${basePath}/`);

function normalizeRenderPath(value) {
  const raw = typeof value === "string" && value.length > 0 ? value : "/";
  const withoutOrigin = raw.replace(/^https?:\/\/[^/]+/i, "");
  const withoutBase = withoutOrigin.startsWith(basePath)
    ? withoutOrigin.slice(basePath.length) || "/"
    : withoutOrigin;
  const pathWithSearch = withoutBase.split("#")[0] || "/";

  return pathWithSearch.startsWith("/") ? pathWithSearch : `/${pathWithSearch}`;
}
