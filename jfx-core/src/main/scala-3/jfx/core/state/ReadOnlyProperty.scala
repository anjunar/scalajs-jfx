package jfx.core.state

trait ReadOnlyProperty[V] {

  def get: V

  def observe(observer: V => Unit): Disposable

  def observeWithoutInitial(observer: V => Unit): Disposable

  def map[T](transform: V => T): ReadOnlyProperty[T] = {
    val source = this

    new ReadOnlyProperty[T] {
      override def get: T =
        transform(source.get)

      override def observe(observer: T => Unit): Disposable =
        source.observe(value => observer(transform(value)))

      override def observeWithoutInitial(observer: T => Unit): Disposable =
        source.observeWithoutInitial(value => observer(transform(value)))
    }
  }

  def flatMap[T](transform: V => ReadOnlyProperty[T]): ReadOnlyProperty[T] = {
    val source = this

    new ReadOnlyProperty[T] {
      override def get: T =
        transform(source.get).get

      override def observe(observer: T => Unit): Disposable = {
        val composite = new CompositeDisposable()
        var currentSub: Disposable = null

        val mainSub = source.observe { v =>
          if (currentSub != null) {
            currentSub.dispose()
            composite.remove(currentSub)
          }
          currentSub = transform(v).observe(observer)
          composite.add(currentSub)
        }

        composite.add(mainSub)
        composite
      }

      override def observeWithoutInitial(observer: T => Unit): Disposable = {
        val composite = new CompositeDisposable()
        var currentSub: Disposable = null

        val mainSub = source.observeWithoutInitial { v =>
          if (currentSub != null) {
            currentSub.dispose()
            composite.remove(currentSub)
          }
          currentSub = transform(v).observe(observer)
          composite.add(currentSub)
        }

        composite.add(mainSub)
        composite
      }
    }
  }

}
