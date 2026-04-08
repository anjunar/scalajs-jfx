package app

import scala.scalajs.js

class Table[E](var rows: js.Array[E] = new js.Array[E](), var size: Long = -1)