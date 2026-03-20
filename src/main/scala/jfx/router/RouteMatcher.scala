package jfx.router

import scala.scalajs.js

object RouteMatcher {

  private def normalize(path: String): String = {
    if (path == null || path.trim.isEmpty) return "/"

    var p = path.trim
    if (!p.startsWith("/")) p = "/" + p

    if (p.length > 1 && p.endsWith("/")) p = p.dropRight(1)

    p
  }

  private def join(parent: String, child: String): String = {
    val p = normalize(parent)
    val c = if (child == null) "" else child.trim

    if (c.isEmpty || c == "/") return p

    val effectiveChild =
      if (p != "/" && c.startsWith("/")) c.drop(1)
      else c

    normalize {
      if (p == "/") ("/" + effectiveChild).replace("//", "/")
      else s"$p/$effectiveChild"
    }
  }

  private def splitSegments(path: String): List[String] = {
    val normalized = normalize(path).stripPrefix("/").stripSuffix("/")
    if (normalized.isBlank) Nil else normalized.split("/").toList
  }

  private def matchPattern(pattern: String, actual: String): js.Map[String, String] | Null = {
    val pSeg = splitSegments(pattern)
    val aSeg = splitSegments(actual)

    val wildcardIndex = pSeg.indexOf("*")
    if (wildcardIndex >= 0) {
      val prefix = pSeg.take(wildcardIndex)
      if (aSeg.size < prefix.size) {
        null
      } else {
        val params = js.Map.empty[String, String]
        var matched = true
        var i = 0

        while (i < prefix.length && matched) {
          val ps = prefix(i)
          val asg = aSeg(i)

          if (ps.startsWith(":")) params += ps.drop(1) -> asg
          else if (ps != asg) matched = false

          i += 1
        }

        if (!matched) null
        else {
          params += "*" -> aSeg.drop(prefix.size).mkString("/")
          params
        }
      }
    } else if (pSeg.size != aSeg.size) {
      null
    } else {
      val params = js.Map.empty[String, String]
      var matched = true
      var i = 0

      while (i < pSeg.length && matched) {
        val ps = pSeg(i)
        val asg = aSeg(i)

        if (ps.startsWith(":")) params += ps.drop(1) -> asg
        else if (ps != asg) matched = false

        i += 1
      }

      if (matched) params else null
    }
  }

  private def prefixMatches(routeFull: String, target: String): Boolean = {
    val routeSegments = splitSegments(routeFull)
    val targetSegments = splitSegments(target)
    val isRoot = routeFull == "/"

    if (isRoot) true
    else if (targetSegments.size < routeSegments.size) false
    else {
      var matched = true
      var i = 0

      while (i < routeSegments.length && matched) {
        val routeSegment = routeSegments(i)
        val targetSegment = targetSegments(i)

        if (routeSegment == "*") {
          i = routeSegments.length
        } else if (routeSegment.startsWith(":")) {
          i += 1
        } else if (routeSegment != targetSegment) {
          matched = false
        } else {
          i += 1
        }
      }

      matched
    }
  }

  def resolveRoutes(routes: js.Array[Route], path: String): RouterState =
    resolveRoutes(routes.toList, path)

  def resolveRoutes(routes: List[Route], path: String): RouterState = {
    val target = normalize(path)

    def dfs(parentFull: String, route: Route): Option[List[RouteMatch]] = {
      val routeFull = normalize(join(parentFull, route.path))

      if (!prefixMatches(routeFull, target)) None
      else {
        val exactMatch = matchPattern(routeFull, target)

        if (exactMatch != null) {
          Some(List(RouteMatch(route, routeFull, exactMatch)))
        } else {
          route.children.iterator
            .map(child => dfs(routeFull, child))
            .collectFirst {
              case Some(childChain) =>
                RouteMatch(route, routeFull, js.Map.empty[String, String]) :: childChain
            }
        }
      }
    }

    val chains = routes.iterator.flatMap(route => dfs("/", route).iterator).toList

    val best =
      if (chains.isEmpty) Nil
      else chains.maxBy(_.size)

    RouterState(path = target, matches = best)
  }
}
