package app

import app.pages.*
import jfx.router.Route

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AppRoutes {

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
      Route.view("/router") { _ =>
        Future.successful(Route.component {
          RouterPage.render()
        })
      },
      Route.view("/router/user/:id") { context =>
        Future.successful(Route.component {
          RouterUserPage.render(context)
        })
      },
      Route.view("/i18n") { _ =>
        Future.successful(Route.component {
          I18nPage.render()
        })
      },
      Route.view("/rendering") { _ =>
        Future.successful(Route.component {
          RenderingPage.render()
        })
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
      }
    )
}
