package jfx.core.state

import scala.collection.mutable

final class CompositeDisposable extends Disposable {
  private val items = mutable.ArrayBuffer.empty[Disposable]
  private var disposed = false

  def add(disposable: Disposable): Unit =
    if (disposed) disposable.dispose()
    else items += disposable

  def remove(disposable: Disposable): Unit = items -= disposable
  
  def dispose(): Unit =
    if (!disposed) {
      disposed = true
      val current = items.toSeq
      items.clear()
      current.foreach(_.dispose())
    }
}
