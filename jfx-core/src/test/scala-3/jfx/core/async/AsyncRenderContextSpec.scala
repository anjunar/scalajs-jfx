package jfx.core.async

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, HostNode}
import jfx.core.state.Disposable
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class AsyncRenderContextSpec extends AsyncFlatSpec with Matchers {

  "AsyncRenderContext" should "propagate rendering failures" in {
    val context = new AsyncRenderContext()
    context.add(Future.failed(new IllegalStateException("load failed")))

    recoverToExceptionIf[IllegalStateException](context.drain()).map { error =>
      error.getMessage shouldBe "load failed"
    }
  }

  it should "drain tasks registered by earlier tasks" in {
    val context   = new AsyncRenderContext()
    var completed = false

    context.add(Future.successful {
      context.add(Future.successful {
        completed = true
      })
    })

    context.drain().map { _ =>
      completed shouldBe true
    }
  }

  it should "stop retaining tasks after the initial drain" in {
    val directExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        runnable.run()

      override def reportFailure(cause: Throwable): Unit =
        throw cause
    }
    val context = new AsyncRenderContext(using directExecutionContext)
    val pending = Promise[Unit]()

    context.drain().value shouldBe Some(Success(()))

    context.add(pending.future)

    context.drain().value shouldBe Some(Success(()))
  }

  it should "report failures from tasks added after the initial drain" in {
    val failures               = ArrayBuffer.empty[Throwable]
    val directExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        runnable.run()

      override def reportFailure(cause: Throwable): Unit =
        failures += cause
    }
    val context = new AsyncRenderContext(using directExecutionContext)
    val error   = new IllegalStateException("late failure")

    context.drain().value shouldBe Some(Success(()))
    context.add(Future.failed(error))

    failures should contain only error
  }

  it should "fail deterministically when nested tasks exceed the maximum depth" in {
    val pending                    = ArrayBuffer.empty[Runnable]
    val controlledExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        pending += runnable

      override def reportFailure(cause: Throwable): Unit =
        throw cause
    }
    val context = new AsyncRenderContext(using controlledExecutionContext)

    def nestedTask(remaining: Int): Future[Unit] =
      Future.unit.map { _ =>
        if (remaining > 0) context.add(nestedTask(remaining - 1))
      }(using controlledExecutionContext)

    context.add(nestedTask(102))
    val drained = context.drain()

    while (pending.nonEmpty) {
      pending.remove(pending.size - 1).run()
    }

    val error = drained.value match {
      case Some(Failure(error: IllegalStateException)) => error
      case result => fail(s"Expected depth failure, got $result")
    }

    error.getMessage shouldBe "AsyncRender: max depth exceeded"
  }

  "Runtime.renderToStringAsync" should "dispose the rendered tree after success" in {
    var disposed = false

    Runtime
      .renderToStringAsync { cursor =>
        Runtime.mount(new AsyncRoot(() => disposed = true), cursor)
      }
      .map { html =>
        html shouldBe "<main>rendered</main>"
        disposed shouldBe true
      }
  }

  it should "dispose the rendered tree after an async rendering failure" in {
    var disposed = false

    val rendered = Runtime.renderToStringAsync { cursor =>
      Runtime.mount(
        new AsyncRoot(
          () => disposed = true,
          Some(new IllegalStateException("async render failed"))
        ),
        cursor
      )
    }

    recoverToExceptionIf[IllegalStateException](rendered).map { error =>
      error.getMessage shouldBe "async render failed"
      disposed shouldBe true
    }
  }

  it should "dispose the rendered tree after HTML serialization fails" in {
    var disposed = false

    val rendered = Runtime.renderToStringAsync { cursor =>
      Runtime.mount(
        new AsyncRoot(
          () => disposed = true,
          failDuringRendering = true
        ),
        cursor
      )
    }

    rendered
      .map(_ => fail("Expected HTML serialization to fail"))
      .recover { case _ =>
        disposed shouldBe true
      }
  }

  private final class AsyncRoot(
      onDispose: () => Unit,
      failure: Option[Throwable] = None,
      failDuringRendering: Boolean = false
  ) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit = {
      addDisposable(Disposable(onDispose()))
      if (failDuringRendering) {
        host.insertChild(
          0,
          new HostNode {
            override def renderHtml(): String =
              throw new IllegalStateException("HTML serialization failed")
          }
        )
      } else {
        Runtime.mount(new TextComponent("rendered"), cursor, Some(this))
      }
      failure.foreach { error =>
        cursor.asyncContext.get.add(Future.failed(error))
      }
    }
  }
}
