package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.core.i18n.i18n

object RenderingPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Rendering, SSR & hydration",
      i18n"The app shell is server-rendered, hydrated on the client and still keeps route loading honest."
    ) {
      Showcase.insightGrid(
        ("SSR", "The initial URL is injected explicitly", "The demo no longer relies on an implicit request header path inside the router call site."),
        ("Hydration", "Routes may load asynchronously", "This page loads with a real delay. Hydration adopts the server-rendered tree and swaps it once the loader delivers."),
        ("Shell", "Toolbar and navigation stay stable", "The visual frame does not reflow unexpectedly while the routed content swaps.")
      )

      Showcase.apiSection(
        i18n"Boot flow",
        i18n"Client and SSR both hand the initial URL to App explicitly."
      ) {
        Showcase.codeBlock(
          "scala",
          """Runtime.renderToStringAsync { cursor =>
            |  render(cursor, request, path)
            |}
            |
            |render(hydratingCursor, request, url)""".stripMargin
        )
      }
    }
  }
}
