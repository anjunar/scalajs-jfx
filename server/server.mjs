import express from "express"
import { createServer as createViteServer } from "vite"
import { dirname, resolve } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

import { developmentAssets, productionAssets } from "../tools/client-assets.mjs"
import { siteConfig } from "../tools/site-config.mjs"

const __dirname = dirname(fileURLToPath(import.meta.url))

const isProduction = process.env.NODE_ENV === "production"
const port = Number(process.env.PORT ?? 3000)

const projectRoot = resolve(__dirname, "..")

const clientDist = resolve(projectRoot, "dist/client")
const serverEntry = resolve(projectRoot, "dist/server/entry-server.js")
const viteConfig = resolve(projectRoot, "vite.config.js")

const app = express()

let vite = null
let builtAssets = null

if (!isProduction) {
    vite = await createViteServer({
        configFile: viteConfig,
        server: {
            middlewareMode: true
        },
        appType: "custom"
    })

    app.use(vite.middlewares)
} else {
    app.use(
        `${siteConfig.basePath}/assets` || "/assets",
        express.static(resolve(clientDist, "assets"), {
            immutable: true,
            maxAge: "1y"
        })
    )

    app.use(
        siteConfig.basePath || "/",
        express.static(clientDist, {
            index: false
        })
    )
}

// Das ganze Dokument kommt aus dem SSR-Render -- es gibt keine index.html mehr,
// in die etwas hineinersetzt wird. Uebrig bleiben die Asset-Tags des Bundles,
// deren Namen erst der Build kennt; die gehen als Argument in den Render.
async function clientAssets() {
    if (!isProduction) return developmentAssets()

    if (builtAssets === null) {
        builtAssets = await productionAssets(clientDist)
    }

    return builtAssets
}

async function loadServerModule() {
    if (isProduction) {
        return await import(pathToFileURL(serverEntry).href)
    }

    return await vite.ssrLoadModule("/src/entry-server.js")
}

app.use(async (req, res, next) => {
    const url = req.originalUrl

    // Alles, was wie eine Datei aussieht (Assets, Quelltexte, Sourcemaps, ...),
    // NICHT per SSR rendern - an Vite/static-Handler bzw. 404 durchreichen.
    const isFileRequest = /\.[a-zA-Z0-9]+$/.test(url.split("?")[0])
    if (isFileRequest && !url.endsWith(".html")) {
        return next()
    }

    try {
        const serverModule = await loadServerModule()
        const rendered = await serverModule.render(
            req.originalUrl,
            req.method,
            JSON.stringify(req.headers),
            JSON.stringify(await clientAssets())
        )

        // Im Dev haengt Vite seinen HMR-Client an den Kopf. Die Head-Senke im
        // Browser fasst nur Knoten mit data-jfx-head an, laesst ihn also stehen.
        const html = isProduction
            ? rendered.html
            : await vite.transformIndexHtml(url, rendered.html)

        res
            .status(rendered.status)
            .set(rendered.headers)
            .type("html")
            .end(html)
    } catch (error) {
        if (!isProduction && vite) {
            vite.ssrFixStacktrace(error)
        }

        if (error?.status === 504) {
            return res.status(504).type("text").end("SSR render timed out")
        }

        next(error)
    }
})

app.listen(port, () => {
    console.log(`http://localhost:${port}`)
})
