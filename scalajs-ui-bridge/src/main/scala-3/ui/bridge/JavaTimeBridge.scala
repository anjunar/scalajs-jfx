package ui.bridge

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.Locale
import scala.scalajs.js.annotation.{JSExport, JSExportAll, JSExportTopLevel}

/** Stable JavaScript boundary for the scala-java-time implementation already
  * linked into the UI runtime. The wrappers deliberately retain the real
  * java.time values; TypeScript never depends on Scala.js linker names.
  */
@JSExportAll
final class JsLocalDate private[bridge] (private val underlying: LocalDate) {
  def year: Int = underlying.getYear
  def monthValue: Int = underlying.getMonthValue
  def dayOfMonth: Int = underlying.getDayOfMonth
  override def toString: String = underlying.toString
  def format(pattern: String, languageTag: String): String =
    underlying.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(languageTag)))
}

@JSExportAll
final class JsInstant private[bridge] (private val underlying: Instant) {
  def epochMilli: Double = underlying.toEpochMilli.toDouble
  override def toString: String = underlying.toString
  def format(pattern: String, languageTag: String, zoneId: String): String =
    DateTimeFormatter
      .ofPattern(pattern, Locale.forLanguageTag(languageTag))
      .withZone(ZoneId.of(zoneId))
      .format(underlying)
}

@JSExportAll
final class JsLocalDateTime private[bridge] (private val underlying: LocalDateTime) {
  def year: Int = underlying.getYear
  def monthValue: Int = underlying.getMonthValue
  def dayOfMonth: Int = underlying.getDayOfMonth
  def hour: Int = underlying.getHour
  def minute: Int = underlying.getMinute
  override def toString: String = underlying.toString
  def format(pattern: String, languageTag: String): String =
    underlying.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(languageTag)))
}

object JavaTimeBridge {
  @JSExportTopLevel("parseLocalDate")
  def parseLocalDate(value: String): JsLocalDate = new JsLocalDate(LocalDate.parse(value))

  @JSExportTopLevel("parseInstant")
  def parseInstant(value: String): JsInstant = new JsInstant(Instant.parse(value))

  @JSExportTopLevel("parseLocalDateTime")
  def parseLocalDateTime(value: String): JsLocalDateTime =
    new JsLocalDateTime(LocalDateTime.parse(value))
}
