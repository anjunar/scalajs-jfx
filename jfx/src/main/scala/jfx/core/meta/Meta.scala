package jfx.core.meta

import scala.reflect.ClassTag

object Meta {

  inline def apply[E](factory: () => E)(using classTag : ClassTag[E]): Unit = {
    ClassLoader.register(factory)
  }

}
