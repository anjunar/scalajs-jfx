package jfx.core.context

import jfx.core.request.RequestContext

object ClientDeviceDetector {

  def detect(request: RequestContext): ClientDevice =
    fromUserAgent(request.headers.get("user-agent").getOrElse(""))

  def fromUserAgent(userAgent: String): ClientDevice = {
    val ua = userAgent.toLowerCase

    val isPhone =
      ua.contains("iphone") ||
        ua.contains("ipod") ||
        ua.contains("windows phone") ||
        ua.contains("blackberry") ||
        ua.contains("bb10") ||
        ua.contains("iemobile") ||
        ua.contains("opera mini") ||
        ua.contains("opera mobi") ||
        (ua.contains("android") && ua.contains("mobile"))

    // The public model is deliberately binary: phone user agents are mobile; tablets use the
    // desktop layout. In particular, Android tablets omit the "mobile" token and iPads therefore
    // fall through to Desktop as well.
    if (isPhone) ClientDevice.Mobile
    else ClientDevice.Desktop
  }
}
