package jfx.core.macros

import jfx.core.state.PropertyAccess
import scala.quoted.*

inline def property[T, V](inline selector: T => V): PropertyAccess[T, V] =
  ${ propertyImpl[T, V]('selector) }

def propertyImpl[T: Type, V: Type](selector: Expr[T => V])(using Quotes): Expr[PropertyAccess[T, V]] = {
  import quotes.reflect.*
  
  selector.asTerm match {
    case Inlined(_, _, Lambda(_, Select(_, fieldName))) =>

      '{
        new PropertyAccess[T, V] {
          val name: String = ${ Expr(fieldName) }

          def get(obj: T): Option[V] =
            Some($selector(obj))

          def set(obj: T, value: V): Unit =
            ${
              val objTerm = 'obj.asTerm
              val valueTerm = 'value.asTerm

              val tpeSym = TypeRepr.of[T].typeSymbol
              val fieldSym = tpeSym.memberField(fieldName)
              val setterSyms = tpeSym.memberMethod(s"${fieldName}_=")

              if fieldSym != Symbol.noSymbol && fieldSym.flags.is(Flags.Mutable) then
                Assign(Select(objTerm, fieldSym), valueTerm).asExprOf[Unit]
              else if setterSyms.nonEmpty then
                Apply(Select(objTerm, setterSyms.head), List(valueTerm)).asExprOf[Unit]
              else
                report.errorAndAbort(s"Selector '$fieldName' is not writable on ${TypeRepr.of[T].show}")
            }
        }
      }

    case _ =>
      report.error("Selector must be a simple field access like _.fieldName")
      '{???}
  }
}
