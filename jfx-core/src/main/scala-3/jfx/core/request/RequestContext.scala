package jfx.core.request

import jfx.core.component.AbstractComponent
import jfx.core.context.{ClientDevice, ClientDeviceDetector}
import jfx.core.di.Context

import scala.collection.immutable

final case class RequestContext(headers: RequestHeaders) {

  def header(name: String): Option[String] =
    headers.get(name.toLowerCase)

  lazy val clientDevice: ClientDevice = ClientDeviceDetector.detect(this)

  def isMobile: Boolean =
    clientDevice == ClientDevice.Mobile

  def isDesktop: Boolean =
    clientDevice == ClientDevice.Desktop
}

object RequestContext {

  private val Value: Context[RequestContext] =
    Context.create[RequestContext]("RequestContext")

  val empty: RequestContext =
    RequestContext(
      headers = RequestHeaders.empty
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