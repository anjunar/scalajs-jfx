package jfx.form

import jfx.core.state.PropertyAccess
import scala.scalajs.js

trait Model[M] {
  
  this : M =>
  
    def properties : js.Array[PropertyAccess[M, ?]]
    
    def findProperty[V](name : String) : V = properties.find(_.name == name).get.get(this).get.asInstanceOf[V]

}
