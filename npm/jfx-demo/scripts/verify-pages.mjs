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

    // The stylesheet must be an SSR asset in its own right. If it only enters
    // through entry-client.ts, this HTML looks fine to the test but an actual
    // browser with JavaScript disabled never requests any CSS.
    const homeResponse = await fetch(`http://localhost:${port}/`);
    const homeHtml = await homeResponse.text();
    check(
      "/ -- one semantic H1 introduces the TypeScript API",
      (homeHtml.match(/<h1\b/g) ?? []).length === 1 &&
        homeHtml.includes("JFX 3 · TypeScript") &&
        homeHtml.includes("Build with TypeScript. Run on JFX."),
      "the home route does not expose the requested TypeScript hero hierarchy"
    );
    const homeSequence = [
      "Start with working code",
      "Typed API",
      "Same runtime",
      "SSR + hydration",
      "One component model",
      "Packages support the product structure",
    ].map((copy) => homeHtml.indexOf(copy));
    check(
      "/ -- working code and runtime model precede package reference",
      homeSequence.every((position) => position >= 0) &&
        homeSequence.every((position, index) => index === 0 || position > homeSequence[index - 1]),
      "the TypeScript entry does not follow the code → runtime → component model → packages sequence"
    );
    const stylesheetHref = homeHtml.match(
      /<link[^>]+rel="stylesheet"[^>]+href="([^"]+\.css)"/
    )?.[1];
    check(
      "/ -- production SSR links the built stylesheet",
      stylesheetHref !== undefined,
      "no built CSS link found in the SSR head"
    );
    if (stylesheetHref !== undefined) {
      const stylesheetResponse = await fetch(new URL(stylesheetHref, homeResponse.url));
      const stylesheet = await stylesheetResponse.text();
      check(
        "/ -- production stylesheet is directly loadable",
        stylesheetResponse.status === 200 &&
          stylesheetResponse.headers.get("content-type")?.includes("text/css") === true &&
          stylesheet.includes("--aj-ink"),
        `status ${stylesheetResponse.status}, content-type ${stylesheetResponse.headers.get("content-type")}`
      );
    }

    check(
      "/ -- capability navigation replaces package navigation",
      ["Interaction", "Architecture", "Foundation", "Runtime", "Forms", "Data", "Editor"]
        .every((label) => homeHtml.includes(label)),
      "one or more product capability sections are missing from the server-rendered sidebar"
    );

    const tablePresentation = await fetch(`http://localhost:${port}/controls/table`);
    const tablePresentationHtml = await tablePresentation.text();
    check(
      "/controls/table -- package and import stay visible as secondary metadata",
      tablePresentationHtml.includes("@anjunar/jfx-controls") &&
        tablePresentationHtml.includes("import { tableView, column, remoteSource }"),
      "TableView does not identify its npm package and TypeScript import"
    );
    check(
      "/controls/table -- corresponding Scala page is linked",
      tablePresentationHtml.includes('href="../scala/table"'),
      "TableView has no link to its Scala counterpart"
    );
    check(
      "/controls/table -- shared runtime, SSR and hydration are explained",
      ["Shared engine", "Server first", "Same structure"].every((copy) =>
        tablePresentationHtml.includes(copy)
      ),
      "one or more shared-runtime architecture cards are missing"
    );
    check(
      "/controls/table -- rich remote catalogue is server-rendered",
      tablePresentationHtml.includes("50 initial rows · 1,000 total") &&
        tablePresentationHtml.includes("The Long Route 1") &&
        tablePresentationHtml.includes("Remote catalogue · visible rows load on demand"),
      "the richer remote TableView example is missing from SSR"
    );

    const tabsHtml = await (await fetch(`http://localhost:${port}/controls/tabs`)).text();
    check(
      "/controls/tabs -- both lifecycle modes are demonstrated",
      tabsHtml.includes("Active-only panels") &&
        tabsHtml.includes("Keep-mounted panels") &&
        tabsHtml.includes("Draft state survives tab changes"),
      "active-only and keep-mounted examples are not both present"
    );

    const carouselHtml = await (await fetch(`http://localhost:${port}/controls/carousel`)).text();
    check(
      "/controls/carousel -- controls and all SSR slide states are present",
      ["Previous", "Fast autoplay", "Stop timer", "Architecture that keeps moving", "Wrap-around is part of the contract"]
        .every((copy) => carouselHtml.includes(copy)),
      "the controlled carousel or one of its SSR slide states is missing"
    );

    const gridHtml = await (await fetch(`http://localhost:${port}/controls/data-grid`)).text();
    check(
      "/controls/data-grid -- rich selectable cards are server-rendered",
      gridHtml.includes("180 cards · only the visible rows are mounted") &&
        gridHtml.includes("Atlas Memo 1") &&
        gridHtml.includes("Select a card to inspect its reactive state."),
      "the richer virtualized card example is missing"
    );

    const formHtml = await (await fetch(`http://localhost:${port}/forms/basics`)).text();
    check(
      "/forms/basics -- validation actions and live model values are present",
      ["Validate", "Clear sample", "Restore sample", "Ada Lovelace", "Model email"]
        .every((copy) => formHtml.includes(copy)),
      "the interactive model and validation example is incomplete"
    );

    const editorHtml = await (await fetch(`http://localhost:${port}/editor/basics`)).text();
    check(
      "/editor/basics -- Markdown value feedback and sample actions are present",
      ["Load article", "Clear editor", "Markdown value", "characters"]
        .every((copy) => editorHtml.includes(copy)),
      "the richer editor example is incomplete"
    );

    const germanResponse = await fetch(`http://localhost:${port}/de/core/derived`);
    const germanHtml = await germanResponse.text();
    check(
      "/de/core/derived -- locale-prefixed route",
      germanResponse.status === 200 && germanHtml.includes('<html lang="de">'),
      `got ${germanResponse.status} or missing lang=de`
    );
    check(
      "/de/core/derived -- page catalog resolves German title and summary",
      germanHtml.includes("Abgeleiteter Zustand") &&
        germanHtml.includes("map() leitet eine Property ab"),
      "German page catalog entries were not resolved"
    );

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
        'href="/en/controls/remote?remote-rows.offset=100&amp;remote-rows.limit=50"'
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
