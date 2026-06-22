package jfx.core.state

trait Disposable {
  def dispose(): Unit
}

object Disposable {
  val empty: Disposable = new Disposable {
    def dispose(): Unit = ()
  }

  def apply(run: => Unit): Disposable = new Disposable {
    private var disposed = false

    def dispose(): Unit =
      if (!disposed) {
        disposed = true
        run
      }
  }
}
