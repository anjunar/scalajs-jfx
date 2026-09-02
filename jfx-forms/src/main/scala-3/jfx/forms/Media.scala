package jfx.forms

import jfx.core.state.Property

import java.util.UUID
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

final class Media(
    val id: Property[UUID] = Property(MediaId.randomUuid()),
    var thumbnail: Property[Thumbnail] = Property(null),
    var name: Property[String] = Property(""),
    var contentType: Property[String] = Property(""),
    var data: Property[String] = Property("")
)

private[forms] object MediaId {
  def randomUuid(): UUID = {
    val bytes  = new Uint8Array(16)
    val crypto = js.Dynamic.global.selectDynamic("crypto")

    if (
      crypto != null && !js
        .isUndefined(crypto) && !js.isUndefined(crypto.selectDynamic("getRandomValues"))
    )
      crypto.getRandomValues(bytes)
    else {
      var index = 0
      while (index < bytes.length) {
        bytes(index) = (math.random() * 256).toInt.toShort
        index += 1
      }
    }

    bytes(6) = (((bytes(6).toInt & 0x0f) | 0x40) & 0xff).toShort
    bytes(8) = (((bytes(8).toInt & 0x3f) | 0x80) & 0xff).toShort

    def hex(index: Int): String = f"${bytes(index).toInt & 0xff}%02x"

    UUID.fromString(
      s"${hex(0)}${hex(1)}${hex(2)}${hex(3)}-" +
        s"${hex(4)}${hex(5)}-" +
        s"${hex(6)}${hex(7)}-" +
        s"${hex(8)}${hex(9)}-" +
        s"${hex(10)}${hex(11)}${hex(12)}${hex(13)}${hex(14)}${hex(15)}"
    )
  }
}
