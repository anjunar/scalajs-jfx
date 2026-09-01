package jfx.control.carousel

import jfx.core.state.Disposable

import scala.scalajs.js.timers.{clearInterval, setInterval}

private[carousel] trait IntervalScheduler {
  def schedule(intervalMs: Int)(action: () => Unit): Disposable
}

private[carousel] object IntervalScheduler {
  val browser: IntervalScheduler = new IntervalScheduler {
    override def schedule(intervalMs: Int)(action: () => Unit): Disposable = {
      val handle = setInterval(math.max(1, intervalMs)) {
        action()
      }
      Disposable(clearInterval(handle))
    }
  }
}
