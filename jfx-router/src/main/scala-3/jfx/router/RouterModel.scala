package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.di.Context
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

final case class RouteContext(
                               path: String,
                               url: String,
                               fullPath: String,
                               pathParams: Map[String, String],
                               queryParams: Map[String, String],
                               state: RouterState,
                               routeMatch: RouteMatch
                             )

final case class RouteMatch(
                             route: Route,
                             fullPath: String,
                             params: Map[String, String]
                           )

final case class RouterState(
                              path: String,
                              matches: List[RouteMatch],
                              queryParams: Map[String, String],
                              search: String
                            ) {
  def url: String =
    s"$path$search"

  def currentMatchOption: Option[RouteMatch] =
    matches.lastOption
}

final case class Route(
                        path: String,
                        load: RouteContext => Future[AbstractComponent],
                        constraints: Map[String, String => Boolean] = Map.empty,
                        children: Seq[Route] = Nil
                      )

object Route {

  private[router] val RouteContextValue: Context[RouteContext] =
    Context.create[RouteContext]("RouteContext")

  final class BlockComponent(
                              context: RouteContext,
                              renderBlock: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit
                            ) extends AbstractCustomComponent {

    override def compose(cursor: Cursor): Unit = {
      RouteContextValue.provide(context)(using this)

      DslLayerTwo.render(this, cursor) {
        renderBlock(using context)
      }
    }
  }

  final class Factory(
                       renderBlock: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit
                     ) {
    def create(context: RouteContext): AbstractComponent =
      new BlockComponent(context, renderBlock)
  }

  def route(
             path: String,
             constraints: Map[String, String => Boolean] = Map.empty,
             children: Seq[Route] = Nil
           )(load: RouteContext => Future[AbstractComponent]): Route =
    Route(
      path = path,
      load = load,
      constraints = constraints,
      children = children
    )

  def view(
            path: String,
            constraints: Map[String, String => Boolean] = Map.empty,
            children: Seq[Route] = Nil
          )(render: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit): Route =
    route(path, constraints, children) { context =>
      Future.successful(new BlockComponent(context, render))
    }

  def asyncView(
                 path: String,
                 constraints: Map[String, String => Boolean] = Map.empty,
                 children: Seq[Route] = Nil
               )(load: RouteContext => Future[Factory])(using ExecutionContext): Route =
    route(path, constraints, children) { context =>
      load(context).map(_.create(context))
    }

  def promiseView(
                   path: String,
                   constraints: Map[String, String => Boolean] = Map.empty,
                   children: Seq[Route] = Nil
                 )(load: RouteContext => js.Promise[Factory])(using ExecutionContext): Route =
    asyncView(path, constraints, children) { context =>
      load(context).toFuture
    }

  def factory(render: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit): Factory =
    new Factory(render)

  def context(using component: AbstractComponent): Option[RouteContext] =
    RouteContextValue.inject

  def requireContext(using component: AbstractComponent): RouteContext =
    context.getOrElse {
      throw new IllegalStateException("Kein RouteContext im aktuellen Komponentenbaum gefunden.")
    }
}

object RouteMatcher {

  def resolve(routes: Seq[Route], path: String): List[RouteMatch] = {
    val normalized = normalize(path)

    resolvePath(routes, normalized).getOrElse(Nil)
  }

  private def resolvePath(routes: Seq[Route], path: String): Option[List[RouteMatch]] =
    routes.iterator
      .map(route => resolveRoute("/", route, path))
      .collectFirst {
        case Some(matches) => matches
      }

  private def resolveRoute(parentPath: String, route: Route, targetPath: String): Option[List[RouteMatch]] = {
    val routePath = join(parentPath, route.path)

    if (!prefixMatches(route, routePath, targetPath)) {
      None
    } else {
      matches(route, routePath, targetPath) match {
        case Some(params) =>
          Some(List(RouteMatch(route, routePath, params)))

        case None =>
          route.children.iterator
            .map(child => resolveRoute(routePath, child, targetPath))
            .collectFirst {
              case Some(childMatches) =>
                RouteMatch(route, routePath, Map.empty) :: childMatches
            }
      }
    }
  }

  private def matches(route: Route, routePath: String, requestPath: String): Option[Map[String, String]] = {
    val routeSegments = segments(normalize(routePath))
    val requestSegments = segments(normalize(requestPath))

    val wildcardIndex = routeSegments.indexOf("*")

    if (wildcardIndex >= 0) {
      matchSegments(routeSegments.take(wildcardIndex), requestSegments.take(wildcardIndex), route.constraints)
        .filter(_ => requestSegments.length >= wildcardIndex)
        .map(_ + ("*" -> requestSegments.drop(wildcardIndex).map(decode).mkString("/")))
    } else if (routeSegments.length != requestSegments.length) {
      None
    } else {
      matchSegments(routeSegments, requestSegments, route.constraints)
    }
  }

  private def matchSegments(
                             routeSegments: Vector[String],
                             requestSegments: Vector[String],
                             constraints: Map[String, String => Boolean]
                           ): Option[Map[String, String]] =
    routeSegments.zip(requestSegments).foldLeft(Option(Map.empty[String, String])) {
      case (None, _) =>
        None

      case (Some(params), (routeSegment, requestSegment)) =>
        if (routeSegment.startsWith(":") && routeSegment.length > 1) {
          val name = routeSegment.drop(1)
          val value = decode(requestSegment)

          if (constraints.get(name).forall(_(value))) {
            Some(params.updated(name, value))
          } else {
            None
          }
        } else if (routeSegment == requestSegment) {
          Some(params)
        } else {
          None
        }
    }

  private def prefixMatches(route: Route, routePath: String, requestPath: String): Boolean = {
    val routeSegments = segments(normalize(routePath))
    val requestSegments = segments(normalize(requestPath))

    routePath == "/" ||
      routeSegments.indexOf("*") >= 0 ||
      (requestSegments.length >= routeSegments.length &&
        routeSegments.zip(requestSegments).forall {
          case (routeSegment, requestSegment) =>
            if (routeSegment.startsWith(":") && routeSegment.length > 1) {
              val name = routeSegment.drop(1)
              route.constraints.get(name).forall(_(decode(requestSegment)))
            } else {
              routeSegment == requestSegment
            }
        })
  }

  private def join(parentPath: String, childPath: String): String = {
    val parent = normalize(parentPath)
    val child = Option(childPath).getOrElse("").trim

    if (child.isEmpty || child == "/") {
      parent
    } else if (parent == "/") {
      normalize(s"/${child.stripPrefix("/")}")
    } else {
      normalize(s"$parent/${child.stripPrefix("/")}")
    }
  }

  private def segments(path: String): Vector[String] =
    if (path == "/") Vector.empty
    else path.stripPrefix("/").split("/").iterator.filter(_.nonEmpty).toVector

  private def decode(value: String): String =
    try js.URIUtils.decodeURIComponent(value)
    catch {
      case _: Throwable => value
    }

  private def normalize(path: String): String = {
    if (path == null || path.isEmpty || path == "/") {
      "/"
    } else {
      val p = path.takeWhile(_ != '?').takeWhile(_ != '#')
      val s = if (p.endsWith("/")) p.dropRight(1) else p
      if (s.isEmpty) "/" else s
    }
  }
}