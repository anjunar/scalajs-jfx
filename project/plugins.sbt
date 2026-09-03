// Alle drei Plugins liegen als sbt-2-Build (`_sbt2_3`) auf Maven Central vor.
// sbt 2 loest die passende Variante ueber die sbt-Version selbst auf, die
// Koordinaten bleiben also unveraendert.
addSbtPlugin("org.scala-js"   % "sbt-scalajs"  % "1.22.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.3.2")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt" % "2.6.1")

// Entfallen: sbt-platform-deps wurde nie fuer sbt 2 gebaut und wird auch nicht
// mehr gebraucht — `%%` traegt den Plattform-Suffix in sbt 2 selbst.
// (Es kam bisher transitiv ueber sbt-scalajs herein, nicht als eigener Eintrag.)
