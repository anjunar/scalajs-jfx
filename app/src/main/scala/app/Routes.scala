package app

import app.pages.DocsCatalog
import app.pages.HomePage.homePage
import app.pages.FormPage.formPage
import app.pages.TablePage.tablePage
import app.pages.WindowPage.windowPage
import app.pages.ComponentDocPage.componentDocPage
import app.pages.DocsIndexPage.docsIndexPage
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.layout.Div.div
import jfx.router.Route
import jfx.router.RouteContext.routeContext

import scala.scalajs.js

object Routes {

  private val childRoutes = {
    val routes = js.Array[Route]()

    routes.push(
      Route.scoped(
        path = "/table",
        factory = {
          tablePage()
        }
      )
    )

    routes.push(
      Route.scoped(
        path = "/form",
        factory = {
          formPage()
        }
      )
    )

    routes.push(
      Route.scoped(
        path = "/window",
        factory = {
          windowPage()
        }
      )
    )

    routes.push(
      Route.scoped(
        path = "/docs",
        factory = {
          docsIndexPage()
        }
      )
    )

    DocsCatalog.entries.foreach { entry =>
      routes.push(
        Route.scoped(
          path = s"/docs/${entry.slug}",
          factory = {
            componentDocPage(entry)()
          }
        )
      )
    }

    routes.push(
      Route.scoped(
        path = "/person",
        factory = {
          val context = routeContext
          div {
            text = s"Person ${context.queryParams.get("id").getOrElse("")}".trim
          }
        }
      )
    )

    routes
  }

  val routes = js.Array[Route](

    Route.scoped(
      path = "/",
      factory = {
        homePage()
      },
      children = childRoutes
    )

  )

}
