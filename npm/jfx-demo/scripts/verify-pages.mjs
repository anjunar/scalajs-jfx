/**
 * The acceptance proof for CLAUDE_DEMO_PLAN.md: every catalog entry, plus its
 * two nested children, reached over real HTTP against the production build,
 * checked for the four things E-3/E-6 promise.
 *
 * `routeManifest` comes from the already-built `dist/server/entry-server.js`,
 * not from importing `app/catalog.ts` -- every doc.ts closes over a
 * `?jfx-code` import that only resolves inside Vite's module graph, and this
 * script runs under plain `node`. See app/catalog.ts's own note on
 * `routeManifest` and CLAUDE_DEMO_PLAN.md's S-8 finding for the full
 * reasoning. Run `npm run build` first (`npm run verify` already does).
 */
import { spawn } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const port = 5180;

const { routeManifest } = await import(
  pathToFileURL(resolve(projectRoot, "dist", "server", "entry-server.js")).href
);

const failures = [];

function check(label, condition, detail) {
  if (condition) {
    console.log(`  ok   ${label}`);
  } else {
    console.log(`  FAIL ${label} -- ${detail}`);
    failures.push(label);
  }
}

/** Every `<!--jfx:Name:start-->`/`<!--jfx:Name:end-->` marker balances by name. */
function unpairedMarkers(html) {
  const counts = new Map();
  const pattern = /<!--jfx:([^:]+):(start|end)-->/g;
  let match;
  while ((match = pattern.exec(html)) !== null) {
    const [, name, edge] = match;
    const delta = edge === "start" ? 1 : -1;
    counts.set(name, (counts.get(name) ?? 0) + delta);
  }
  return [...counts.entries()].filter(([, balance]) => balance !== 0).map(([name]) => name);
}

async function checkRoute(entry) {
  const response = await fetch(`http://localhost:${port}${entry.path}`);
  const html = await response.text();

  check(
    `${entry.path} -- status ${entry.status}`,
    response.status === entry.status,
    `got ${response.status}`
  );
  check(`${entry.path} -- title "${entry.title}"`, html.includes(entry.title), "title not found in HTML");

  // Home, search and the 404 page are app chrome, not a library-capability
  // example -- they carry no ?jfx-code block, on purpose (E-3 documents
  // *library usage*, and neither page demonstrates any).
  const isFramePage = entry.path === "/" || entry.path === "/search" || entry.path === "/404";
  if (!isFramePage) {
    const codeBlockMatch = html.match(/<pre class="docs-code">([\s\S]*?)<\/pre>/);
    check(
      `${entry.path} -- non-empty docs-code block`,
      codeBlockMatch !== null && codeBlockMatch[1].trim().length > 0,
      "no <pre class=\"docs-code\"> with content found"
    );
  }

  const unpaired = unpairedMarkers(html);
  check(`${entry.path} -- no unpaired <!--jfx:--> marker`, unpaired.length === 0, `unpaired: ${unpaired.join(", ")}`);
}

async function main() {
  const server = spawn(process.execPath, [resolve(projectRoot, "server.mjs")], {
    cwd: projectRoot,
    env: { ...process.env, NODE_ENV: "production", PORT: String(port) },
    stdio: ["ignore", "pipe", "pipe"],
  });

  const stderr = [];
  server.stderr.on("data", (chunk) => stderr.push(String(chunk)));

  try {
    await new Promise((resolveListening, rejectListening) => {
      const timer = setTimeout(
        () => rejectListening(new Error("production server did not start within 60s")),
        60_000
      );
      server.stdout.on("data", (chunk) => {
        if (String(chunk).includes(`http://localhost:${port}`)) {
          clearTimeout(timer);
          resolveListening();
        }
      });
      server.on("exit", (code) => {
        clearTimeout(timer);
        rejectListening(new Error(`production server exited with code ${code}\n${stderr.join("")}`));
      });
    });

    console.log(`verifying ${routeManifest.length} routes against the production build:`);
    for (const entry of routeManifest) {
      await checkRoute(entry);
    }

    const queryResponse = await fetch(
      `http://localhost:${port}/router/params/42?tab=details&tag=ssr`
    );
    const queryHtml = await queryResponse.text();
    check(
      "/router/params/42?tab=details&tag=ssr -- status 200",
      queryResponse.status === 200,
      `got ${queryResponse.status}`
    );
    check(
      "/router/params/42?tab=details&tag=ssr -- query parameters reach SSR",
      queryHtml.includes('queryParams: {"tab":"details","tag":"ssr"}'),
      "queryParams were not preserved in the server-rendered route context"
    );

    const remotePageResponse = await fetch(
      `http://localhost:${port}/controls/remote?remote-rows.offset=50&remote-rows.limit=50`
    );
    const remotePageHtml = await remotePageResponse.text();
    check(
      "/controls/remote?remote-rows.offset=50&remote-rows.limit=50 -- status 200",
      remotePageResponse.status === 200,
      `got ${remotePageResponse.status}`
    );
    check(
      "/controls/remote -- SSR pager links to the next page",
      remotePageHtml.includes(
        'href="/controls/remote?remote-rows.offset=100&amp;remote-rows.limit=50"'
      ),
      "the server-rendered Next link did not carry the next remote page offset"
    );
    check(
      "/controls/remote -- SSR renders the requested remote page",
      remotePageHtml.includes("Item 0050") &&
        remotePageHtml.includes("Item 0099") &&
        !remotePageHtml.includes("Item 0000"),
      "the requested remote page was not materialized at its absolute offset"
    );
  } finally {
    server.kill();
  }
}

await main();

if (failures.length > 0) {
  console.error(`\n${failures.length} check(s) failed: ${failures.join(", ")}`);
  process.exitCode = 1;
} else {
  console.log("\nall checks passed");
}
