import { defineConfig } from "vite";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../..", import.meta.url));
const read = relative => readFile(new URL(`../../${relative}`, import.meta.url), "utf8");
export default defineConfig(({ isSsrBuild }) => ({
  base: "./",
  plugins: [{
    name: "landing-content",
    resolveId(id) { if (id === "virtual:landing-content") return "\0landing-content"; },
    async load(id) {
      if (id !== "\0landing-content") return;
      const build = await read("build.sbt");
      const version = build.match(/^version\s*:=\s*"([^"]+)"/m)?.[1];
      const scalaVersion = build.match(/^scalaVersion\s*:=\s*"([^"]+)"/m)?.[1];
      const scalaJsVersion = (await read("project/plugins.sbt")).match(/"sbt-scalajs"\s*%\s*"([^"]+)"/)?.[1];
      const sbtVersion = (await read("project/build.properties")).match(/sbt.version\s*=\s*(\S+)/)?.[1];
      if (!version || !scalaVersion || !scalaJsVersion || !sbtVersion) throw Error("Missing landing version metadata");
      for (const name of ["jfx-core", "scalajs-jfx-bridge", "scalajs-jfx"]) {
        if (JSON.parse(await read(`npm/${name}/package.json`)).version !== version) throw Error(`Version mismatch: ${name}`);
      }
      for (const file of ["build.sbt", "project/plugins.sbt", "project/build.properties", "npm/jfx-landing/starters/Counter.scala", "npm/jfx-landing/src/counter.mjs"]) this.addWatchFile(`${root}/${file}`);
      const scalaSource = await read("npm/jfx-landing/starters/Counter.scala");
      const tsSource = await read("npm/jfx-landing/src/counter.mjs");
      return `export default ${JSON.stringify({version, scalaVersion, scalaJsVersion, sbtVersion, scalaSource, tsSource})};`;
    }
  }],
  resolve: { dedupe: ["@anjunar/jfx-core", "@anjunar/jfx-controls", "@anjunar/jfx-forms", "@anjunar/jfx-editor", "@anjunar/scalajs-jfx-bridge"] },
  server: { fs: { allow: [root] } },
  build: { manifest: !isSsrBuild, sourcemap: true, ...(isSsrBuild ? {} : { rollupOptions: { input: "src/client.mjs" } }) }
}));
