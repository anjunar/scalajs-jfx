package jfx.form

import org.scalajs.dom.HTMLElement

trait Formular {

  def addControl(control : Control[?, ? <: HTMLElement]) : Unit

  def removeControl(control : Control[?, ? <: HTMLElement]) : Unit

}
