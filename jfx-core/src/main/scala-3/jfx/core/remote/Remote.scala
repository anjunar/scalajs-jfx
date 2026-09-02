package jfx.core.remote

import jfx.core.state.ListProperty
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Remote-Paging: HTTP-Abfrage, Seiten, Sortierung.
  *
  * Lag vorher in jfx.core.state neben Property und Disposable -- HTTP-Paging-, Sortier- und
  * Query-Semantik sass damit im Fundament, und jeder Konsument von scalajs-jfx-core bekam sie mit.
  * Siehe CHANGE.md P2-5.
  */

/** Laedt eine Seite. Das Ergebnis ist ein Future -- Future ist das interne Async-Modell des
  * Frameworks (siehe ARCHITECTURE.md). js.Promise erscheint nur an der JS-Exportgrenze und in
  * Facades gegenueber JS-Bibliotheken.
  *
  * Wer einen Loader gegen eine JS-API schreibt, nimmt [[RemoteLoader.fromPromise]].
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

  /** JS-Grenze: adaptiert einen Promise-basierten Loader auf das interne Future-Modell. Genau eine
    * Konvertierung, an einer benannten Stelle.
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
    initOverrides: Map[String, js.Any] = Map.empty
) {

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

  def toRequestInit: dom.RequestInit = {
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

  dom
    .fetch(request.urlWithQueryString, request.toRequestInit)
    .toFuture
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
