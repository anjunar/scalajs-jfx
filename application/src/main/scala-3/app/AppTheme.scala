package app

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.state.{Property, ReadOnlyProperty}
import org.scalajs.dom

import scala.scalajs.js
import scala.util.control.NonFatal

/** Theme state of a single app instance.
  *
  * The SSR bundle is loaded once per Node process and reused for every request, so the mode must
  * not live in an `object`. Every `App` owns its own instance and publishes it through the
  * component context.
  */
final class AppTheme(initialMode: AppTheme.Mode, effects: AppTheme.Effects) {

  private val mode: Property[AppTheme.Mode] =
    Property(initialMode)

  def modeProperty: ReadOnlyProperty[AppTheme.Mode] =
    mode

  def set(value: AppTheme.Mode): Unit = {
    mode.set(value)
    effects.apply(value)
  }
}

object AppTheme {

  enum Mode(val value: String) {
    case Light extends Mode("light")
    case Dark  extends Mode("dark")
  }

  object Mode {
    def parse(value: String | Null): Option[Mode] =
      value match {
        case "light" => Some(Mode.Light)
        case "dark"  => Some(Mode.Dark)
        case _       => None
      }
  }

  /** Everything the theme does outside its own property. */
  trait Effects {

    /** Mode the environment already carries, if any. */
    def initialMode: Option[Mode]

    def apply(mode: Mode): Unit
  }

  object Effects {
    val none: Effects =
      new Effects {
        override def initialMode: Option[Mode] = None
        override def apply(mode: Mode): Unit   = ()
      }
  }

  /** `data-theme`, `meta[theme-color]` and `localStorage`. Only instantiated in the browser. */
  final class BrowserEffects extends Effects {

    override def initialMode: Option[Mode] =
      try Mode.parse(dom.document.documentElement.getAttribute("data-theme"))
      catch { case NonFatal(_) => None }

    override def apply(mode: Mode): Unit = {
      applyToDocument(mode)
      persist(mode)
    }

    // `meta[theme-color]` is not written here: AppHead registers it with the DocumentHead and
    // keeps it in step with this property. Two writers on one head element would overwrite each
    // other on the next reconcile.
    private def applyToDocument(mode: Mode): Unit =
      try dom.document.documentElement.setAttribute("data-theme", mode.value)
      catch { case NonFatal(_) => () }

    private def persist(mode: Mode): Unit =
      try dom.window.localStorage.setItem(SiteConfig.themeStorageKey, mode.value)
      catch { case NonFatal(_) => () }
  }

  /** Browser-backed instance when a DOM is present, a plain state holder otherwise. */
  def forEnvironment(): AppTheme = {
    val effects = if (hasBrowserWindow) new BrowserEffects else Effects.none
    new AppTheme(effects.initialMode.getOrElse(Mode.Light), effects)
  }

  private val Value: Context[AppTheme] =
    Context.create[AppTheme]("AppTheme")

  def provide(value: AppTheme)(using component: AbstractComponent): Unit =
    Value.provide(value)

  def current(using component: AbstractComponent): Option[AppTheme] =
    Value.inject

  def require(using component: AbstractComponent): AppTheme =
    current.getOrElse {
      throw new IllegalStateException("No AppTheme found in the current component tree.")
    }

  private def hasBrowserWindow: Boolean =
    js.typeOf(js.Dynamic.global.window) != "undefined"
}
