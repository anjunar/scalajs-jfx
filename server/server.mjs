import express from "express"
import { createServer as createViteServer } from "vite"
import { readFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

const __dirname = dirname(fileURLToPath(import.meta.url))
const isProduction = process.env.NODE_ENV === "production"
const port = Number(process.env.PORT ?? 3000)

const app = express()

let vite = null

if (!isProduction) {
    vite = await createViteServer({
        server: {
            middlewareMode: true
        },
        appType: "custom"
    })

    app.use(vite.middlewares)
} else {
    app.use(
        "/assets",
        express.static(resolve(__dirname, "dist/client/assets"), {
            immutable: true,
            maxAge: "1y"
        })
    )
}

async function clientAssets() {
    if (!isProduction) {
        return {
            script: "/src/main.js",
            css: []
        }
    }

    const manifestPath = resolve(__dirname, "dist/client/.vite/manifest.json")
    const manifest = JSON.parse(await readFile(manifestPath, "utf-8"))
    const entry = manifest["src/main.js"]

    if (!entry) {
        throw new Error("Vite manifest entry missing: src/main.js")
    }

    return {
        script: `/${entry.file}`,
        css: entry.css?.map(file => `/${file}`) ?? []
    }
}

function injectClientAssets(html, assets) {
    let result = html.replace(
        /src="\/src\/main\.js"/,
        `src="${assets.script}"`
    )

    if (assets.css.length > 0) {
        const links = assets.css
            .map(href => `<link rel="stylesheet" href="${href}">`)
            .join("")

        result = result.replace("</head>", `${links}</head>`)
    }

    return result
}

app.use(async (req, res, next) => {
    const path = req.originalUrl

    try {
        const serverModule = isProduction
            ? await import(pathToFileURL(resolve(__dirname, "dist/server/entry-server.js")).href)
            : await vite.ssrLoadModule("/src/entry-server.js")

        let html = await serverModule.render(path)

        if (!html.trimStart().toLowerCase().startsWith("<!doctype")) {
            html = `<!doctype html>${html}`
        }

        html = injectClientAssets(html, await clientAssets())

        if (!isProduction) {
            html = await vite.transformIndexHtml(path, html)
        }

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