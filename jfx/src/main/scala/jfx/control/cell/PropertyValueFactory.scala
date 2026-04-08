package jfx.control.cell

import reflect.macros.PropertySupport
import jfx.control.TableColumn
import jfx.core.state.{Property, ReadOnlyProperty}
import reflect.ClassDescriptor

class PropertyValueFactory[S, T](val property: String)
  extends (TableColumn.CellDataFeatures[S, T] => ReadOnlyProperty[T] | Null) {

  override def apply(features: TableColumn.CellDataFeatures[S, T]): ReadOnlyProperty[T] | Null = {
    val rowValue = features.getValue
    if (rowValue == null) null
    else resolveValue(rowValue)
  }

  private def resolveValue(rowValue: S): ReadOnlyProperty[T] | Null = {
    val typeName = rowValue.getClass.getName
    ClassDescriptor.maybeForName(typeName) match {
      case Some(descriptor) =>
        descriptor.properties.find(_.name == property) match {
          case Some(prop) =>
            descriptor.getProperty(property).flatMap(_.accessor) match {
              case Some(accessor) => wrapValue(accessor.get(rowValue.asInstanceOf[Any]))
              case None => null
            }
          case None => null
        }
      case None => null
    }
  }

  private def wrapValue(value: Any): ReadOnlyProperty[T] | Null =
    value match {
      case property: ReadOnlyProperty[?] =>
        property.asInstanceOf[ReadOnlyProperty[T]]

      case null =>
        Property(null.asInstanceOf[T])

      case other =>
        Property(other.asInstanceOf[T])
    }
}
