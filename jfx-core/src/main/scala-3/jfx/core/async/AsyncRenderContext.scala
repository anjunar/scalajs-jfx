package jfx.core.async

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

final class AsyncRenderContext(using ec: ExecutionContext) {
  private val tasks      = ArrayBuffer.empty[Future[Unit]]
  private var collecting = true
  private var cancelled  = false

  private val MaxDrainDepth = 100

  /** Whether async work is still allowed to complete a render. */
  def isActive: Boolean =
    collecting && !cancelled

  def add(task: Future[Unit]): Unit = {
    if (collecting) tasks += task
    else if (!cancelled) task.failed.foreach(ec.reportFailure)(ec)
  }

  /** Stops accepting render work after an aborted render.
    *
    * The underlying futures cannot be cancelled by Scala's Future API. This closes the render
    * context, drops its bookkeeping and lets framework-owned continuations guard their mutations
    * with [[isActive]].
    */
  def cancel(): Unit = {
    cancelled = true
    collecting = false
    tasks.clear()
  }

  def drain(): Future[Unit] = {
    if (!collecting) return Future.unit

    def loop(offset: Int, depth: Int = 0): Future[Unit] = {
      if (depth > MaxDrainDepth) {
        Future.failed(new IllegalStateException("AsyncRender: max depth exceeded"))
      } else {
        val batch = tasks.drop(offset).toVector

        if (batch.isEmpty) Future.unit
        else Future.sequence(batch).flatMap(_ => loop(offset + batch.size, depth + 1))
      }
    }

    loop(0).transform { result =>
      collecting = false
      tasks.clear()
      result
    }(ec)
  }
}
