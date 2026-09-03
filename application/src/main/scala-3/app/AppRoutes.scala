package app

import app.pages.*
import jfx.router.Route

import jfx.core.component.AbstractComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.timers.setTimeout

object AppRoutes {

  /** A loader that completes only after `millis`.
    *
    * This exists so at least one demo route actually exercises the asynchronous path. All other
    * routes have their data immediately available and return an already completed Future -- they
    * would never reveal whether hydration could tolerate a running loader.
    */
  private def delayed(millis: Int)(component: => AbstractComponent): Future[AbstractComponent] = {
    val promise = Promise[AbstractComponent]()
    setTimeout(millis.toDouble)(promise.success(component))
    promise.future
  }

  def routes: Seq[Route] =
    Seq(
      Route.view("/") { _ =>
        Future.successful(Route.component {
          OverviewPage.render()
        })
      },
      Route.view("/button") { _ =>
        Future.successful(Route.component {
          ButtonPage.render()
        })
      },
      Route.view("/layout") { _ =>
        Future.successful(Route.component {
          LayoutPage.render()
        })
      },
      Route.view("/window") { _ =>
        Future.successful(Route.component {
          WindowPage.render()
        })
      },
      Route.view("/image") { _ =>
        Future.successful(Route.component {
          ImagePage.render()
        })
      },
      Route.view(
        "/router",
        children = Seq(
          Route.view("user/:id") { context =>
            Future.successful(Route.component {
              RouterUserPage.render(context)
            })
          }
        )
      ) { _ =>
        Future.successful(Route.component {
          RouterPage.render()
        })
      },
      Route.view("/i18n") { _ =>
        Future.successful(Route.component {
          I18nPage.render()
        })
      },
      // Deliberately delayed: this route demonstrates that hydration tolerates a loader still in
      // flight. Before P4-1 the router threw here; the other routes concealed it because their
      // Futures were already completed. See CHANGE.md P4-1.
      Route.view("/rendering") { _ =>
        delayed(120) {
          Route.component {
            RenderingPage.render()
          }
        }
      },
      Route.view("/state") { _ =>
        Future.successful(Route.component {
          StatePage.render()
        })
      },
      Route.view("/forms") { _ =>
        Future.successful(Route.component {
          FormsPage.render()
        })
      },
      Route.view("/image-cropper") { _ =>
        Future.successful(Route.component {
          ImageCropperPage.render()
        })
      },
      Route.view("/editor") { _ =>
        Future.successful(Route.component {
          EditorPage.render()
        })
      },
      Route.view("/tabs") { _ =>
        Future.successful(Route.component {
          TabsPage.render()
        })
      },
      Route.view("/carousel") { _ =>
        Future.successful(Route.component {
          CarouselPage.render()
        })
      },
      Route.view("/combo-box") { _ =>
        Future.successful(Route.component {
          ComboBoxPage.render()
        })
      },
      Route.view("/table") { _ =>
        Future.successful(Route.component {
          TableViewPage.render()
        })
      },
      Route.view("/data-grid") { _ =>
        Future.successful(Route.component {
          DataGridPage.render()
        })
      },
      Route.view("/virtual-list") { _ =>
        Future.successful(Route.component {
          VirtualListViewPage.render()
        })
      },
      Route.view("/viewport") { _ =>
        Future.successful(Route.component {
          ViewportPage.render()
        })
      },
      // The error pages. Declared as routes so they are addressable and prerenderable, and so the
      // status sits next to the page that expresses it. AppRouterBoundaries maps a failure to one
      // of these paths; the router forwards there without changing the URL.
      //
      // Route.error keeps them out of the sitemap: tools/app-routes.mjs collects Route.view only.
      Route.error("/404", status = 404) { _ =>
        Future.successful(NotFoundPage.render())
      },
      Route.error("/500", status = 500) { _ =>
        Future.successful(ErrorPage.render())
      }
    )
}
