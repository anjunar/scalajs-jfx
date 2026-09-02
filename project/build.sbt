// Dieselbe Ursache wie das `exportJars := false` in der Build-Wurzel, nur eine
// Ebene hoeher: aendert man build.sbt oder eine Datei in project/, packt sbt die
// Meta-Build-JAR (scalajs-jfx-build_sbt2_3) neu und scheitert unter Windows am
// Rename auf die noch offene Datei. Das blanke Setting der Wurzel gilt fuer die
// Meta-Build nicht -- sie ist ein eigener Build.
exportJars := false
