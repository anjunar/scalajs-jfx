package jfx.core.remote

import jfx.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

/** Remote paging: HTTP requests, pages, and sorting.
  *
  * It previously lived in jfx.core.state beside Property and Disposable, placing HTTP paging,
  * sorting, and query semantics in the foundation and exposing them to every scalajs-jfx-core
  * consumer. See CHANGE.md P2-5.
  */

/** Loads a page. The result is a Future -- Future is the framework's internal async model (see
  * ARCHITECTURE.md). js.Promise appears only at the JavaScript export boundary and in facades for
  * JavaScript libraries.
  *
  * Loaders targeting a JavaScript API should use [[RemoteLoader.fromPromise]].
  */
trait RemoteLoader[V, Query] {
  def load(query: Query): Future[RemotePage[V, Query]]
}

object RemoteLoader {

  def apply[V, Query](loadFn: Query => Future[RemotePage[V, Query]]): RemoteLoader[V, Query] =
    new RemoteLoader[V, Query] {
      override def load(query: Query): Future[RemotePage[V, Query]] =
        loadFn(query)
    }

  /** JavaScript boundary: adapts a Promise-based loader to the internal Future model. Exactly one
    * conversion at a named location.
    */
  def fromPromise[V, Query](
      loadFn: Query => js.Promise[RemotePage[V, Query]]
  ): RemoteLoader[V, Query] =
    RemoteLoader(query => loadFn(query).toFuture)

  def rest[V, Query](
      requestFor: Query => RestRequest,
      executionContext: ExecutionContext = ExecutionContext.global
  )(decode: (js.Any, Query) => RemotePage[V, Query]): RemoteLoader[V, Query] =
    RemoteLoader(query => fetchPage(requestFor(query), query, decode, executionContext))
}

final case class RemotePage[V, Query](
    items: Seq[V],
    offset: Option[Int] = None,
    nextQuery: Option[Query] = None,
    totalCount: Option[Int] = None,
    hasMore: Option[Boolean] = None
)

final case class RemoteSort(field: String, ascending: Boolean = true) {
  def direction: String    = if (ascending) "asc" else "desc"
  def asQueryValue: String = s"$field,$direction"
}

object RemotePage {

  def fromArray[V, Query](
      items: js.Array[V],
      offset: Option[Int] = None,
      nextQuery: Option[Query] = None,
      totalCount: Option[Int] = None,
      hasMore: Option[Boolean] = None
  ): RemotePage[V, Query] =
    RemotePage(items.toSeq, offset, nextQuery, totalCount, hasMore)
}

final case class RestRequest(
    url: String,
    method: String = "GET",
    queryParams: Map[String, Any] = Map.empty,
    headers: Map[String, String] = Map.empty,
    body: js.UndefOr[js.Any] = js.undefined,
    initOverrides: Map[String, js.Any] = Map.empty,
    signal: Option[dom.AbortSignal] = None,
    timeoutMillis: Option[Int] = None
) {

  require(timeoutMillis.forall(_ > 0), "RestRequest.timeoutMillis must be positive")

  def withQueryParam(name: String, value: Any): RestRequest =
    copy(queryParams = queryParams.updated(name, value))

  def withHeader(name: String, value: String): RestRequest =
    copy(headers = headers.updated(name, value))

  def urlWithQueryString: String = {
    val normalizedParams = normalizeQueryParams(queryParams)
    if (normalizedParams.isEmpty) {
      url
    } else {
      val separator   = if (url.contains("?")) "&" else "?"
      val queryString = normalizedParams
        .map { case (key, value) => s"${encodeURIComponent(key)}=${encodeURIComponent(value)}" }
        .mkString("&")
      s"$url$separator$queryString"
    }
  }

  def toRequestInit: dom.RequestInit =
    toRequestInitWithSignal(signal)

  private[remote] def toRequestInitWithSignal(
      effectiveSignal: Option[dom.AbortSignal]
  ): dom.RequestInit = {
    val init = js.Dynamic.literal(method = method)

    if (headers.nonEmpty) {
      init.updateDynamic("headers")(js.Dictionary(headers.toSeq*))
    }

    if (!js.isUndefined(body)) {
      init.updateDynamic("body")(body)
    }

    initOverrides.foreach { case (key, value) =>
      init.updateDynamic(key)(value.asInstanceOf[js.Any])
    }

    effectiveSignal.foreach(init.updateDynamic("signal")(_))

    init.asInstanceOf[dom.RequestInit]
  }
}

final case class RemoteRequestException(url: String, status: Int, responseBody: String)
    extends RuntimeException(
      s"Request to $url failed with status $status${
          if (responseBody.nonEmpty) s": $responseBody" else ""
        }"
    )

private[remote] def fetchPage[V, Query](
    request: RestRequest,
    query: Query,
    decode: (js.Any, Query) => RemotePage[V, Query],
    executionContext: ExecutionContext
): Future[RemotePage[V, Query]] = {
  given ExecutionContext = executionContext

  val prepared = prepareRequest(request)

  Future
    .fromTry(Try(dom.fetch(request.urlWithQueryString, prepared.init).toFuture))
    .flatten
    .flatMap { response =>
      if (response.ok) {
        response.json().toFuture.map(json => decode(json, query))
      } else {
        response
          .text()
          .toFuture
          .flatMap(body =>
            Future.failed(
              RemoteRequestException(request.urlWithQueryString, response.status.toInt, body)
            )
          )
      }
    }
    .andThen { case _ => prepared.cleanup() }
}

private final case class PreparedRequest(init: dom.RequestInit, cleanup: () => Unit)

private def prepareRequest(request: RestRequest): PreparedRequest =
  request.timeoutMillis match {
    case None =>
      PreparedRequest(request.toRequestInit, () => ())

    case Some(timeoutMillis) =>
      val controller = new dom.AbortController()
      val forwardAbort: js.Function1[dom.Event, Unit] =
        _ => controller.abort()

      request.signal.foreach { signal =>
        if (signal.aborted) controller.abort()
        else signal.addEventListener("abort", forwardAbort)
      }

      val timeout = setTimeout(timeoutMillis.toDouble) {
        controller.abort()
      }

      PreparedRequest(
        request.toRequestInitWithSignal(Some(controller.signal)),
        () => {
          clearTimeout(timeout)
          request.signal.foreach(_.removeEventListener("abort", forwardAbort))
        }
      )
  }

private[remote] def normalizeQueryParams(params: Map[String, Any]): Seq[(String, String)] =
  params.toSeq.flatMap { case (key, value) =>
    expandQueryParamValue(value).map(stringValue => key -> stringValue)
  }

private[remote] def expandQueryParamValue(value: Any): Seq[String] =
  value match {
    case null =>
      Seq.empty
    case None =>
      Seq.empty
    case Some(inner) =>
      expandQueryParamValue(inner)
    case values: js.Array[?] =>
      values.toSeq.flatMap(expandQueryParamValue)
    case values: Iterable[?] =>
      values.toSeq.flatMap(expandQueryParamValue)
    case other =>
      Seq(other.toString)
  }

private[remote] def encodeURIComponent(value: String): String =
  js.URIUtils.encodeURIComponent(value)
