package app.domain

import jfx.core.state.Property

class InsightRecord(
  var title: Property[String] = Property(""),
  var state: Property[String] = Property(""),
  var steward: Property[String] = Property(""),
  var tension: Property[Int] = Property(0),
  var revisions: Property[Int] = Property(0),
  var summary: Property[String] = Property(""),
  var nextStep: Property[String] = Property(""),
  var updatedAt: Property[String] = Property("")
)
