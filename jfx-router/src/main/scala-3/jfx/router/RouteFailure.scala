package jfx.router

/** Why the router renders something other than the route the visitor asked for.
  *
  * The failure carries the request unchanged. That is the whole point of the mechanism: the router
  * forwards internally to an error route, it does not navigate to one. `/blog/typo` stays in the
  * address bar and the response keeps its status under that URL -- turning it into a redirect to
  * `/404` would make a missing page indistinguishable from a working one for anything that reads
  * status codes.
  */
sealed trait RouteFailure {

  /** State of the request that failed, not of the error route that renders it. */
  def state: RouterState

  /** Status when the application configured no error route for this failure. An error route's own
    * [[Route.status]] takes precedence.
    */
  def status: Int
}

object RouteFailure {

  /** No route in the table matched the requested path. */
  final case class NotMatched(state: RouterState) extends RouteFailure {
    val status: Int = 404
  }

  /** A route matched, but its loader failed. */
  final case class LoadFailed(error: Throwable, context: RouteContext) extends RouteFailure {
    def state: RouterState = context.state
    val status: Int        = 500
  }
}
