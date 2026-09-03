package jfx.core.request

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RequestContextSpec extends AnyFlatSpec with Matchers {

  "RequestContext.withUserAgent" should "match server-side device detection for the same user agent" in {
    val mobileUserAgent =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1"

    val serverRequest = RequestContext(
      RequestHeaders(Map("user-agent" -> Vector(mobileUserAgent)))
    )
    val hydrationRequest = RequestContext.withUserAgent(mobileUserAgent)

    hydrationRequest.header("User-Agent") shouldBe Some(mobileUserAgent)
    hydrationRequest.clientDevice shouldBe serverRequest.clientDevice
    hydrationRequest.isMobile shouldBe true
    hydrationRequest.isDesktop shouldBe false
  }

  it should "treat tablet user agents as desktop in the binary device model" in {
    val ipad = RequestContext.withUserAgent(
      "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"
    )
    val androidTablet = RequestContext.withUserAgent(
      "Mozilla/5.0 (Linux; Android 14; Pixel Tablet) AppleWebKit/537.36 Safari/537.36"
    )

    ipad.isDesktop shouldBe true
    androidTablet.isDesktop shouldBe true
  }
}
