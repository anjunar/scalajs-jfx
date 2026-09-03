package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.remote.RemoteSort
import jfx.core.request.RequestContext
import jfx.core.state.ListProperty
import org.scalajs.dom

import scala.scalajs.js

private[control] object CrawlCookieState {

  final case class State(
      offset: Int,
      limit: Int,
      sorting: Option[Vector[RemoteSort]]
  ) {
    def normalized: State =
      copy(offset = math.max(0, offset), limit = math.max(1, limit))

    def withSorting(value: Seq[RemoteSort]): State =
      copy(sorting = Some(value.toVector))
  }

  private val IdPattern    = "[A-Za-z][A-Za-z0-9_-]*".r
  private val CookiePrefix = "jfx-crawl-"

  /** Seven days; previously one year.
    *
    * The value carries scroll offset, page size, and sorting -- a convenience for "the page was
    * reloaded and I want to continue reading", not a preference. A year exceeded its useful life.
    *
    * This has a cost because the cookie uses Path=/ and is created individually per control ID:
    * every crawlable list a visitor has ever scrolled is then attached to *every* request to this
    * origin, including assets. Over a year, this accumulates permanently.
    *
    * Path=/ remains: the SSR page reads the state from the cookie header, while the control does
    * not know its route and therefore cannot narrow the path.
    *
    * See CHANGE.md P3-2.
    */
  private val CookieMaxAgeSeconds = 7L * 24L * 60L * 60L

  def requireId(value: Option[String], componentName: String): String =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(id) if IdPattern.matches(id) => id
      case Some(id)                          =>
        throw new IllegalStateException(
          s"$componentName crawlId '$id' is invalid; use letters, digits, '-' or '_' and start with a letter."
        )
      case None =>
        throw new IllegalStateException(
          s"$componentName requires a stable crawlId when crawlable is enabled."
        )
    }

  def resolve(
      id: String,
      defaultLimit: Int,
      browserRendering: Boolean
  )(using component: AbstractComponent): State = {
    val cookie = read(id, browserRendering)
    State(
      offset = cookie.map(_.offset).getOrElse(0),
      limit = cookie.map(_.limit).getOrElse(defaultLimit),
      sorting = cookie.flatMap(_.sorting)
    ).normalized
  }

  def write(id: String, state: State, browserRendering: Boolean): Unit =
    if (browserRendering) {
      val encoded = encodeCookieValue(state.normalized)
      dom.document.cookie =
        s"${cookieName(id)}=$encoded; Path=/; Max-Age=$CookieMaxAgeSeconds; SameSite=Lax"
    }

  private def read(
      id: String,
      browserRendering: Boolean
  )(using component: AbstractComponent): Option[State] = {
    val rawCookie =
      if (browserRendering) Option(dom.document.cookie).filter(_.nonEmpty)
      else RequestContext.current.flatMap(_.header("cookie"))

    rawCookie
      .flatMap(cookieValue(_, cookieName(id)))
      .flatMap(decodeCookieValue)
  }

  private def cookieName(id: String): String = s"$CookiePrefix$id"

  private def cookieValue(cookie: String, name: String): Option[String] =
    cookie
      .split(";")
      .iterator
      .map(_.trim)
      .flatMap { entry =>
        val separator = entry.indexOf('=')
        if (separator < 0) Iterator.empty
        else Iterator.single(entry.take(separator) -> entry.drop(separator + 1))
      }
      .collectFirst { case (`name`, value) => value }

  private def encodeCookieValue(state: State): String = {
    val sorting = state.sorting
      .getOrElse(Vector.empty)
      .map { sort =>
        s"${encode(sort.field)},${sort.direction}"
      }
      .mkString("|")
    encode(s"${state.offset}:${state.limit}:$sorting")
  }

  private def decodeCookieValue(value: String): Option[State] =
    try {
      decode(value).split(":", 3).toList match {
        case offset :: limit :: sorting :: Nil =>
          for {
            parsedOffset  <- offset.toIntOption
            parsedLimit   <- limit.toIntOption
            parsedSorting <- decodeSorting(sorting)
          } yield State(parsedOffset, parsedLimit, Some(parsedSorting)).normalized
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }

  private def decodeSorting(value: String): Option[Vector[RemoteSort]] =
    if (value.isEmpty) Some(Vector.empty)
    else {
      val parsed = value
        .split("\\|")
        .iterator
        .map { entry =>
          entry.lastIndexOf(',') match {
            case separator if separator > 0 =>
              val field = decode(entry.take(separator))
              entry.drop(separator + 1) match {
                case "asc"  => Some(RemoteSort(field, ascending = true))
                case "desc" => Some(RemoteSort(field, ascending = false))
                case _      => None
              }
            case _ => None
          }
        }
        .toVector

      Option.when(parsed.forall(_.nonEmpty))(parsed.flatten)
    }

  private def encode(value: String): String =
    js.URIUtils.encodeURIComponent(value)

  private def decode(value: String): String =
    js.URIUtils.decodeURIComponent(value)
}
