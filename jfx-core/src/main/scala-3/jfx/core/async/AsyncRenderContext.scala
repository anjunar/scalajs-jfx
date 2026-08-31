package jfx.core.async

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

final class AsyncRenderContext(using ExecutionContext) {
  private val tasks = ArrayBuffer.empty[Future[Unit]]

  private val MaxDrainDepth = 100

  def add(task: Future[Unit]): Unit =
    tasks += task

  def drain(): Future[Unit] = {
    def loop(offset: Int, depth: Int = 0): Future[Unit] = {
      if (depth > MaxDrainDepth) {
        Future.failed(new IllegalStateException("AsyncRender: max depth exceeded"))
      } else {
        val batch = tasks.drop(offset).toVector

        if (batch.isEmpty) Future.unit
        else Future.sequence(batch).flatMap(_ => loop(offset + batch.size, depth + 1))
      }
    }

    loop(0)
  }
}
