package app

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.state.{Disposable, Property, ReadOnlyProperty}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Per-application preference state; the small shared controller owns browser effects. */
final class AppTheme private (initialUrl: String) {
  private val controller = DesignPreferences.createPreferences(SiteConfig.themeStorageKey, initialUrl)
  val serverState: PreferenceState = DesignPreferences.serverPreferences(initialUrl)
  private val mode = Property(AppTheme.Mode.parse(controller.getState().colorScheme).getOrElse(AppTheme.Mode.Light))
  private val design = Property(controller.getState().design)
  private val storageAvailable = Property(true)
  def modeProperty: ReadOnlyProperty[AppTheme.Mode] = mode
  def designProperty: ReadOnlyProperty[String] = design
  def storageAvailableProperty: ReadOnlyProperty[Boolean] = storageAvailable

  def install(owner: AbstractComponent): Unit = {
    val cancel = controller.subscribe { state =>
      mode.set(AppTheme.Mode.parse(state.colorScheme).getOrElse(AppTheme.Mode.Light))
      design.set(state.design)
      storageAvailable.set(state.storageAvailable)
    }
    owner.addDisposable(Disposable(cancel()))
  }
  def set(value: AppTheme.Mode): Unit = controller.setColorScheme(value.value)
  def setDesign(value: String): Unit = controller.setDesign(value)
}

object AppTheme {
  enum Mode(val value: String) {
    case Light extends Mode("light")
    case Dark extends Mode("dark")
  }
  object Mode {
    def parse(value: String | Null): Option[Mode] = value match {
      case "light" => Some(Mode.Light)
      case "dark" => Some(Mode.Dark)
      case _ => None
    }
  }
  def forEnvironment(initialUrl: String = "/"): AppTheme = new AppTheme(initialUrl)
  private val Value: Context[AppTheme] = Context.create[AppTheme]("AppTheme")
  def provide(value: AppTheme)(using component: AbstractComponent): Unit = Value.provide(value)
  def current(using component: AbstractComponent): Option[AppTheme] = Value.inject
  def require(using component: AbstractComponent): AppTheme = current.getOrElse {
    throw new IllegalStateException("No AppTheme found in the current component tree.")
  }
}

@js.native trait DesignEntry extends js.Object {
  val id: String = js.native
  val name: String = js.native
}
@js.native trait PreferenceState extends js.Object {
  val design: String = js.native
  val colorScheme: String = js.native
  val storageAvailable: Boolean = js.native
}
@js.native trait PreferenceController extends js.Object {
  def getState(): PreferenceState = js.native
  def setDesign(value: String): Unit = js.native
  def setColorScheme(value: String): Unit = js.native
  def subscribe(listener: js.Function1[PreferenceState, Unit]): js.Function0[Unit] = js.native
}
@js.native @JSImport("@anjunar/scalajs-jfx/preferences",JSImport.Namespace)
object DesignPreferences extends js.Object {
  val designs: js.Array[DesignEntry] = js.native
  def createPreferences(legacyKey: String, url: String): PreferenceController = js.native
  def serverPreferences(url: String): PreferenceState = js.native
  def bootstrapScript(legacyKey: String): String = js.native
}
