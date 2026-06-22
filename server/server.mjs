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

const app = express()

let vite = null

if (!isProduction) {
    vite = await createViteServer({
        root: clientRoot,
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

    try {
        const template = await loadTemplate(url)
        const serverModule = await loadServerModule()

        const appHtml = await serverModule.render(url)

        const html = template.replace("<!--app-html-->", appHtml)

        res
            .status(200)
            .set({ "Content-Type": "text/html" })
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