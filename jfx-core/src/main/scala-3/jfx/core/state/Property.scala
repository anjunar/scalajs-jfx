package jfx.core.state

import scala.collection.mutable
import scala.compiletime.uninitialized

final class Property[T](private var value: T) extends ReadOnlyProperty[T] {

  private val listeners = mutable.ArrayBuffer.empty[T => Unit]
  private var disposableOwner: CompositeDisposable | Null = null
  private var defaultValue : T = value

  override def get: T =
    value

  def registerDisposableOwner(owner: CompositeDisposable): this.type = {
    disposableOwner = owner
    this
  }

  private[state] def autoRegister(disposable: Disposable): Unit =
    if (disposableOwner != null) {
      disposableOwner.add(disposable)
    }

  private[state] def hasSameDisposableOwnerAs(other: Property[?]): Boolean =
    disposableOwner != null && disposableOwner.eq(other.disposableOwner)

  def set(newValue: T): Unit =
    if (newValue != value) {
      setAlways(newValue)
    }
    
  def setDefault(newValue : T) : Unit =
    if (newValue != defaultValue) {
      defaultValue = newValue
    }  

  def isDirty : Boolean = value != defaultValue
  
  def setAlways(newValue: T): Unit = {
    value = newValue
    listeners.toVector.foreach(_(newValue))
  }

  override def observe(listener: T => Unit): Disposable = {
    listeners += listener
    listener(value)
    () => listeners -= listener
  }

  override def observeWithoutInitial(listener: T => Unit): Disposable = {
    listeners += listener
    () => listeners -= listener
  }

  override def toString: String =
    s"Property($get)"

}

object Property {

  def apply[T](value: T): Property[T] =
    new Property[T](value)

  def owned[T](owner: CompositeDisposable, value: T): Property[T] =
    new Property[T](value).registerDisposableOwner(owner)

  def subscribeBidirectional[T](a: Property[T], b: Property[T]): Disposable = {
    if (a.eq(b)) return () => ()

    b.set(a.get)

    var settingA = false
    var settingB = false

    val da = a.observe { value =>
      if (!settingA) {
        settingB = true
        try b.set(value)
        finally settingB = false
      }
    }

    val db = b.observe { value =>
      if (!settingB) {
        settingA = true
        try a.set(value)
        finally settingA = false
      }
    }

    val composite = new CompositeDisposable()
    composite.add(da)
    composite.add(db)
    a.autoRegister(composite)
    if ((b ne a) && !a.hasSameDisposableOwnerAs(b)) {
      b.autoRegister(composite)
    }
    composite
  }

}
