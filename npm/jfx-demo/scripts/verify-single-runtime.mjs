/**
 * The one-runtime proof, for the three environments this app actually has.
 *
 * `installRuntime()` keeps the installed runtime in a single module-level
 * variable inside `@anjunar/jfx-core`. That is safe only as long as there is
 * exactly one *instance* of that module. Two instances are two slots, and the
 * second one has never seen `installRuntime` -- the symptom is "No JFX runtime
 * installed" with the call visibly right above it, which is what happened here
 * under Vite's SSR module runner (JAVASCRIPT_API.md §13) and was worked around
 * for a while with a relative import into the neighbouring package.
 *
 * The workaround is gone; `resolve.dedupe` in vite.config.ts is the fix. This
 * script is what stops that from being a claim.
 *
 *   1. Client build   -- the runtime module appears exactly once in the bundle.
 *   2. SSR build      -- likewise.
 *   3. Dev server     -- boots and answers with server-rendered markup. With
 *                        two module instances it could not: `renderToString`
 *                        would throw before producing any.
 *
 * The marker is a string literal that occurs once in `runtime.ts` and nowhere
 * else in the library. Counting it in the emitted bundle counts copies of the
 * module. A bundler that duplicated jfx-core would duplicate the literal with
 * it; minification does not touch string contents.
 */
import { spawn } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));

/** From `runtime.ts`'s installRuntime() guard. */
const MARKER = "A JFX runtime is already installed";

const failures = [];

function check(label, condition, detail) {
  if (condition) {
    console.log(`  ok   ${label}`);
  } else {
    console.log(`  FAIL ${label} -- ${detail}`);
    failures.push(label);
  }
}

function occurrences(haystack, needle) {
  let count = 0;
  let index = haystack.indexOf(needle);
  while (index !== -1) {
    count += 1;
    index = haystack.indexOf(needle, index + needle.length);
  }
  return count;
}

async function countInBundle(label, file) {
  let source;
  try {
    source = await readFile(file, "utf8");
  } catch {
    check(label, false, `${file} does not exist -- run \`npm run build\` first`);
    return;
  }

  const found = occurrences(source, MARKER);
  check(label, found === 1, `expected exactly 1 copy of the runtime module, found ${found}`);
}

async function clientBundle() {
  const assets = resolve(projectRoot, "dist", "client", "assets");
  let entries;
  try {
    entries = await readdir(assets);
  } catch {
    check("client build", false, `${assets} does not exist -- run \`npm run build\` first`);
    return;
  }

  const scripts = entries.filter((name) => name.endsWith(".js"));
  const sources = await Promise.all(
    scripts.map((name) => readFile(join(assets, name), "utf8"))
  );
  const found = sources.reduce((total, source) => total + occurrences(source, MARKER), 0);

  check(
    "client build",
    found === 1,
    `expected exactly 1 copy of the runtime module across ${scripts.length} chunk(s), found ${found}`
  );
}

async function devServer() {
  const server = spawn(process.execPath, [resolve(projectRoot, "server.mjs")], {
    cwd: projectRoot,
    env: { ...process.env, PORT: "5179" },
    stdio: ["ignore", "pipe", "pipe"],
  });

  const stderr = [];
  server.stderr.on("data", (chunk) => stderr.push(String(chunk)));

  try {
    const listening = new Promise((resolveListening, rejectListening) => {
      const timer = setTimeout(
        () => rejectListening(new Error("dev server did not start within 60s")),
        60_000
      );
      server.stdout.on("data", (chunk) => {
        if (String(chunk).includes("http://localhost:5179")) {
          clearTimeout(timer);
          resolveListening();
        }
      });
      server.on("exit", (code) => {
        clearTimeout(timer);
        rejectListening(new Error(`dev server exited with code ${code}\n${stderr.join("")}`));
      });
    });

    await listening;

    const response = await fetch("http://localhost:5179/");
    const html = await response.text();

    check("dev server responds 200", response.status === 200, `status ${response.status}`);
    check(
      "dev server returns server-rendered markup",
      html.includes("A TypeScript facade over JFX3"),
      "the SSR outlet was not filled -- with two runtime slots, renderToString throws instead"
    );

    // /controls/table pulls in @anjunar/jfx-controls; if that package dragged
    // its own copy of jfx-core, the runtime marker count above would rise and
    // this route would fault on the second, uninstalled slot.
    const controls = await fetch("http://localhost:5179/controls/table");
    const controlsHtml = await controls.text();
    check(
      "dev server server-renders the controls route",
      controls.status === 200 && controlsHtml.includes("jfx-table-view"),
      `status ${controls.status}, table markup ${controlsHtml.includes("jfx-table-view")}`
    );
    check(
      "dev server did not log a runtime-installation fault",
      !stderr.join("").includes("No JFX runtime installed"),
      stderr.join("").slice(0, 400)
    );
  } catch (error) {
    check("dev server", false, String(error));
  } finally {
    server.kill();
  }
}

console.log("one runtime instance, three environments:");
await countInBundle("ssr build", resolve(projectRoot, "dist", "server", "entry-server.js"));
await clientBundle();
await devServer();

if (failures.length > 0) {
  console.error(`\n${failures.length} check(s) failed: ${failures.join(", ")}`);
  process.exitCode = 1;
} else {
  console.log("\nall checks passed");
}
