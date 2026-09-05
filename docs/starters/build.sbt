import org.scalajs.linker.interface.ModuleKind

enablePlugins(ScalaJSPlugin)
name := "jfx-starter"
scalaVersion := "3.3.8"
scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))
Compile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "public"
libraryDependencies += "com.anjunar" %% "scalajs-jfx-core" % "3.0.0"
