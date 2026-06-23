package jfx.core.context

import jfx.core.request.RequestContext

object ClientDeviceDetector {

  def detect(request: RequestContext): ClientDevice =
    fromUserAgent(request.headers.get("user-agent").getOrElse(""))

  def fromUserAgent(userAgent: String): ClientDevice = {
    val ua = userAgent.toLowerCase

    val isIpad =
      ua.contains("ipad") ||
        (ua.contains("macintosh") && ua.contains("mobile") && ua.contains("safari"))

    val isAndroidTablet =
      ua.contains("android") && !ua.contains("mobile")

    val isTablet =
      isIpad || isAndroidTablet

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

    if (isTablet) ClientDevice.Desktop
    else if (isPhone) ClientDevice.Mobile
    else ClientDevice.Desktop
  }
}