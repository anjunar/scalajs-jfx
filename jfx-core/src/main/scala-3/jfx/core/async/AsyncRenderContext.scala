package jfx.core.async

import org.scalajs.dom.console

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

final class AsyncRenderContext(using ExecutionContext) {
  private val tasks = ArrayBuffer.empty[Future[Unit]]

  def add(task: Future[Unit]): Unit =
    tasks += task.recover { case e =>
      console.error(s"AsyncRender failed: $e")
    }

  def drain(): Future[Unit] = {
    def loop(offset: Int, depth: Int = 0): Future[Unit] = {
      if (depth > 100) Future.failed(new Exception("AsyncRender: max depth exceeded"))
      val batch = tasks.drop(offset).toVector

      if (batch.isEmpty) Future.unit
      else Future.sequence(batch).flatMap(_ => loop(offset + batch.size, depth + 1))
    }

    loop(0)
  }
}
