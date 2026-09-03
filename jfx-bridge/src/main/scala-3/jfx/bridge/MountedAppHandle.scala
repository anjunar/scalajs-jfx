package jfx.bridge

import jfx.core.component.{AbstractComponent, Runtime}

import scala.scalajs.js

/** Mirrors `contract.ts`'s `MountedApp`. */
final class MountedAppHandle(private[bridge] final val root: AbstractComponent) extends js.Object {
  def dispose(): Unit = Runtime.unmount(root)
}
