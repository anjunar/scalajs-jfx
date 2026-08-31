package jfx.core.async

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class AsyncRenderContextSpec extends AsyncFlatSpec with Matchers {

  "AsyncRenderContext" should "propagate rendering failures" in {
    val context = new AsyncRenderContext()
    context.add(Future.failed(new IllegalStateException("load failed")))

    recoverToExceptionIf[IllegalStateException](context.drain()).map { error =>
      error.getMessage shouldBe "load failed"
    }
  }

  it should "drain tasks registered by earlier tasks" in {
    val context = new AsyncRenderContext()
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
}
