import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("..", import.meta.url));
for (const mode of ["production", "development"]) {
  const server = spawn(process.execPath, ["server.mjs"], { cwd: root, env: { ...process.env, NODE_ENV: mode, PORT: "0", HMR_PORT: "0" }, stdio: ["ignore", "pipe", "pipe"] });
  let log = "";
  server.stderr.on("data", data => { log += data; });
  try {
    const origin = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(Error("Server startup timeout: " + log)), 30000);
      server.stdout.on("data", data => { const match = String(data).match(/http:\/\/127\.0\.0\.1:\d+/); if (match) { clearTimeout(timeout); resolve(match[0]); } });
      server.once("exit", code => { clearTimeout(timeout); reject(Error(`Server exited ${code}: ${log}`)); });
    });
    for (const lang of ["en", "de"]) for (const design of ["atlas", "flora", "terra", "ember"]) for (const scheme of ["light", "dark"]) {
      const response = await fetch(`${origin}/${lang}/?design=${design}&colorScheme=${scheme}`);
      const html = await response.text();
      assert.equal(response.status, 200, log);
      assert(html.includes(`lang="${lang}" data-design="${design}" data-color-scheme="${scheme}"`));
      assert(html.includes(lang === "de" ? "Eine Runtime." : "One runtime."));
      assert(html.includes('id="counter-root"'));
    }
    const response = await fetch(`${origin}/?nojs`);
    const html = await response.text();
    assert(html.includes('data-design="ember" data-color-scheme="dark"'));
    assert.equal(response.headers.get("content-security-policy"), "script-src 'none'");
    const css = html.match(/<link rel="stylesheet" href="([^"]+)"/)[1];
    const stylesheet = await fetch(new URL(css, origin), { headers: { accept: "text/css" } });
    assert.equal(stylesheet.status, 200);
    assert((await stylesheet.text()).includes('--color-canvas'));
    assert.equal((await fetch(`${origin}/missing`)).status, 404);
    assert((await (await fetch(`${origin}/starters/Counter.scala`)).text()).includes('class Counter'));
    assert(!log.includes("No JFX runtime installed"), log);
    console.log(`${mode}: 16 locale/design/scheme combinations, CSS without JavaScript, downloads and 404 verified.`);
  } finally { server.kill(); await once(server, "exit"); }
}
