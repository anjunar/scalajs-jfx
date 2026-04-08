package app.domain

import jfx.core.meta.PackageClassLoader
import jfx.domain.{Media, Thumbnail}
import app.Table

object DomainRegistry {

  def init(): Unit = {
    val loader = PackageClassLoader("app.domain")

    loader.register(() => new Address(), classOf[Address])
    loader.register(() => new Email(), classOf[Email])
    loader.register(() => new Person(), classOf[Person])

    loader.register(() => new Media(), classOf[Media])
    loader.register(() => new Thumbnail(), classOf[Thumbnail])

    loader.register(() => new Table[Any](), classOf[Table[Any]])


  }
}
