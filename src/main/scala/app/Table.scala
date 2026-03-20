package app

import jfx.core.state.PropertyAccess
import jfx.form.Model

import scala.scalajs.js

class Table[E](var rows: js.Array[E], var size: Long) extends Model[Table[E]] {
  override def properties: js.Array[PropertyAccess[Table[E], ?]] = Table.properties[E]
}

object Table {

  def properties[E]: js.Array[PropertyAccess[Table[E], ?]] = js.Array(
    new PropertyAccess[Table[E], js.Array[E]] {
      override val name: String = "rows"

      override def get(obj: Table[E]): Option[js.Array[E]] =
        Some(obj.rows)

      override def set(obj: Table[E], value: js.Array[E]): Unit =
        obj.rows = value
    },
    new PropertyAccess[Table[E], Long] {
      override val name: String = "size"

      override def get(obj: Table[E]): Option[Long] =
        Some(obj.size)

      override def set(obj: Table[E], value: Long): Unit =
        obj.size = value
    }
  )

}

