import express from "express"
import { createServer as createViteServer } from "vite"
import { readFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

const __dirname = dirname(fileURLToPath(import.meta.url))

const isProduction = process.env.NODE_ENV === "production"
const port = Number(process.env.PORT ?? 3000)

const projectRoot = resolve(__dirname, "..")

const clientRoot = resolve(projectRoot, "application/src/main/webapp")
const clientDist = resolve(projectRoot, "dist/client")
const serverEntry = resolve(projectRoot, "dist/server/entry-server.js")
const viteConfig = resolve(projectRoot, "vite.config.js")

const app = express()

let vite = null

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
        "/assets",
        express.static(resolve(clientDist, "assets"), {
            immutable: true,
            maxAge: "1y"
        })
    )

    app.use(
        express.static(clientDist, {
            index: false
        })
    )
}

async function loadTemplate(url) {
    if (isProduction) {
        return await readFile(resolve(clientDist, "index.html"), "utf-8")
    }

    const template = await readFile(resolve(clientRoot, "index.html"), "utf-8")
    return await vite.transformIndexHtml(url, template)
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
        const template = await loadTemplate(url)
        const serverModule = await loadServerModule()
        const rendered = await serverModule.render(
            req.originalUrl,
            req.method,
            JSON.stringify(req.headers)
        )
        const html = template.replace("<!--app-html-->", rendered.html)
        res
            .status(rendered.status)
            .set(rendered.headers)
            .type("html")
            .end(html)
    } catch (error) {
        if (!isProduction && vite) {
            vite.ssrFixStacktrace(error)
        }
        next(error)
    }
})

app.listen(port, () => {
    console.log(`http://localhost:${port}`)
})
