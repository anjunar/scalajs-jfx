package jfx.json

import scala.scalajs.js

trait JsonRegistry {
  
  val classes : js.Map[String, () => Any]

}
