package app.components

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty
import jfx.core.layout.TextComponent
import jfx.core.text.TextValue

final class Anchor extends AbstractComponent {
  val tagName = "a"

  def href: String =
    host.attribute("href").getOrElse("#")

  def href_=(value: String): Unit =
    host.setAttribute("href", Option(value).getOrElse("#"))

  def target: String =
    host.attribute("target").getOrElse("")

  def target_=(value: String): Unit =
    host.setAttribute("target", value)

  def rel: String =
    host.attribute("rel").getOrElse("")

  def rel_=(value: String): Unit =
    host.setAttribute("rel", value)
}

object Anchor {
  def anchor()(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor =
    DslLayerTwo.child(new Anchor()) {
      body
    }

  def anchor[T](
      label: T
  )(body: Anchor ?=> Cursor ?=> Unit = {})(using
      parent: AbstractComponent,
      cursor: Cursor,
      textValue: TextValue[T]
  ): Anchor = {
    val link = new Anchor()

    DslLayerTwo.child(link) {
      TextComponent.text(label) {}
      body
    }
  }

  def href_=(value: String)(using anchor: Anchor): Unit =
    anchor.href_=(value)

  def href(using anchor: Anchor): String =
    anchor.href

  def target_=(value: String)(using anchor: Anchor): Unit =
    anchor.target_=(value)

  def target(using anchor: Anchor): String =
    anchor.target

  def rel_=(value: String)(using anchor: Anchor): Unit =
    anchor.rel_=(value)

  def rel(using anchor: Anchor): String =
    anchor.rel

}

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

    DslLayerTwo.child(link) {
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
    val link = new Anchor()
    val activeMatcher = defaultActiveMatcher(to)

    DslLayerTwo.child(link) {
      RouterLinkHandler.inject(using link) match {
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
    val pathname = Option(path).getOrElse("/").takeWhile(ch => ch != '?' && ch != '#')
    val segments = pathname.split("/").filter(_.nonEmpty).toVector
    val withoutLocale =
      segments.headOption match {
        case Some("de" | "en") => segments.drop(1)
        case _                 => segments
      }

    if (withoutLocale.isEmpty) "/"
    else s"/${withoutLocale.mkString("/")}"
  }
}

final class Image extends AbstractComponent {
  val tagName = "img"

  def src: String =
    host.attribute("src").getOrElse("")

  def src_=(value: String): Unit =
    host.setAttribute("src", value)

  def alt: String =
    host.attribute("alt").getOrElse("")

  def alt_=(value: String): Unit =
    host.setAttribute("alt", value)
}

object Image {
  def image(
      body: Image ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Image =
    DslLayerTwo.child(new Image()) {
      body
    }

  def src_=(value: String)(using image: Image): Unit =
    image.src_=(value)

  def src(using image: Image): String =
    image.src

  def alt_=(value: String)(using image: Image): Unit =
    image.alt_=(value)

  def alt(using image: Image): String =
    image.alt
}
