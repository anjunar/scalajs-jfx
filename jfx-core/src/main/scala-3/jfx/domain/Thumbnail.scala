package jfx.domain

import jfx.core.state.Property

import java.util.UUID

final class Thumbnail(
    val id: Property[UUID] = Property(MediaId.randomUuid()),
    var name: Property[String] = Property(""),
    var contentType: Property[String] = Property(""),
    var data: Property[String] = Property("")
)
