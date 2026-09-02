package jfx.router

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
import jfx.core.layout.Anchor
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

final case class RouterLinkHandler(
    navigate: String => Unit,
    currentPath: ReadOnlyProperty[String],
    hrefForAppPath: String => String
)

object RouterLinkHandler {
  private val RouterLinkContext =
    Context.create[RouterLinkHandler]("AppRouterLink")

  def provide(handler: RouterLinkHandler)(using component: AbstractComponent): Unit =
    RouterLinkContext.provide(handler)

  def inject(using component: AbstractComponent): Option[RouterLinkHandler] =
    RouterLinkContext.inject
}

object RouterLink {

  def routerLink()(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor = {
    val link = new Anchor()

    DslLayer.child(link) {
      body
      installNavigation(link, None)
    }
  }

  def routerLink(
      to: String,
      activeClass: String = "active"
  )(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor = {
    val link          = new Anchor()
    val activeMatcher = defaultActiveMatcher(to)

    DslLayer.child(link) {
      RouterLinkHandler.inject(using link).orElse(routerHandler(using link)) match {
        case Some(handler) if isAppPath(to) =>
          link.href = handler.hrefForAppPath(to)
          link.classCondition(activeClass, handler.currentPath.map(activeMatcher))
          body
          installNavigation(link, Some(handler.navigate))

        case _ =>
          link.href = to
          body
          installNavigation(link, None)
      }
    }
  }

  private def installNavigation(link: Anchor, navigate: Option[String => Unit]): Unit =
    navigate.foreach { runNavigate =>
      link.onClickHandler { event =>
        val destination = link.href

        if (isInternalDestination(destination) && link.target.isEmpty) {
          event.preventDefault()
          runNavigate(destination)
        }
      }
    }

  private def routerHandler(using link: Anchor): Option[RouterLinkHandler] =
    Router.current(using link).map { router =>
      RouterLinkHandler(
        navigate = path => router.navigate(path),
        currentPath = router.state.map(_.path),
        hrefForAppPath = path => router.hrefFor(path)
      )
    }

  private def isInternalDestination(destination: String): Boolean =
    destination.startsWith("/")

  private def isAppPath(destination: String): Boolean =
    destination.startsWith("/") && !destination.startsWith("//")

  private def defaultActiveMatcher(to: String): String => Boolean = {
    val normalized = normalizeInternalPath(to)

    currentPath =>
      if (normalized == "/") currentPath == "/"
      else currentPath == normalized || currentPath.startsWith(s"$normalized/")
  }

  private def normalizeInternalPath(path: String): String = {
    val pathname      = Option(path).getOrElse("/").takeWhile(ch => ch != '?' && ch != '#')
    val segments      = pathname.split("/").filter(_.nonEmpty).toVector
    val withoutLocale =
      segments.headOption match {
        case Some("de" | "en") => segments.drop(1)
        case _                 => segments
      }

    if (withoutLocale.isEmpty) "/"
    else s"/${withoutLocale.mkString("/")}"
  }
}
