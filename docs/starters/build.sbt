import org.scalajs.linker.interface.ModuleKind

enablePlugins(ScalaJSPlugin)
name := "ui-starter"
scalaVersion := "3.3.8"
scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))
Compile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "public"
libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.0.0"
