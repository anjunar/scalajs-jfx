package jfx.json

import reflect.{ParameterizedTypeDescriptor, TypeDescriptor}

private[json] final case class JsonMappingContext(
    expectedType: TypeDescriptor,
    bindings: Map[String, TypeDescriptor]
) {

  def resolvedType: TypeDescriptor =
    JsonTypeModel.substitute(bindings, expectedType)

  def child(childType: TypeDescriptor): JsonMappingContext = {
    val resolved      = JsonTypeModel.substitute(bindings, childType)
    val childBindings = resolved match {
      case parameterized: ParameterizedTypeDescriptor =>
        JsonTypeModel.typeBindings(parameterized)
      case _ =>
        Map.empty
    }

    JsonMappingContext(resolved, bindings ++ childBindings)
  }
}

private[json] object JsonMappingContext {

  def root(meta: TypeDescriptor): JsonMappingContext =
    JsonMappingContext(meta, JsonTypeModel.typeBindings(meta))
}
