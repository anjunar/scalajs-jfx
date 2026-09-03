package jfx.core.remote

import org.scalajs.dom
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class RestRequestSpec extends AnyFlatSpec with Matchers {

  "RestRequest" should "put an AbortSignal into the native request init" in {
    val signal = js.Dynamic.literal(aborted = false).asInstanceOf[dom.AbortSignal]
    val init   = RestRequest("/items", signal = Some(signal)).toRequestInit

    init.signal.toOption.get should be theSameInstanceAs signal
  }

  it should "require a positive timeout" in {
    an[IllegalArgumentException] should be thrownBy RestRequest("/items", timeoutMillis = Some(0))
  }
}
