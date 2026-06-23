package jfx.core.request

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

final case class RequestContext(
                                 path: String,
                                 url: String,
                                 method: String,
                                 headers: RequestHeaders,
                                 serverSide: Boolean
                               )

object RequestContext {

  private val Value: Context[RequestContext] =
    Context.create[RequestContext]("RequestContext")

  val empty: RequestContext =
    RequestContext(
      path = "/",
      url = "/",
      method = "GET",
      headers = RequestHeaders.empty,
      serverSide = false
    )

  def provide(value: RequestContext)(using component: AbstractComponent): Unit =
    Value.provide(value)

  def current(using component: AbstractComponent): Option[RequestContext] =
    Value.inject

  def require(using component: AbstractComponent): RequestContext =
    current.getOrElse {
      throw new IllegalStateException("Kein RequestContext im aktuellen Komponentenbaum gefunden.")
    }
}

final class RequestHeaders private (
                                     private val values: Map[String, Vector[String]]
                                   ) {

  def get(name: String): Option[String] =
    values.get(normalize(name)).flatMap(_.headOption)

  def getAll(name: String): Vector[String] =
    values.getOrElse(normalize(name), Vector.empty)

  def contains(name: String): Boolean =
    values.contains(normalize(name))

  def asMap: Map[String, Vector[String]] =
    values

  private def normalize(name: String): String =
    name.toLowerCase
}

object RequestHeaders {

  val empty: RequestHeaders =
    new RequestHeaders(Map.empty)

  def apply(values: Map[String, Vector[String]]): RequestHeaders =
    new RequestHeaders(values.map { case (key, value) =>
      key.toLowerCase -> value
    })
}