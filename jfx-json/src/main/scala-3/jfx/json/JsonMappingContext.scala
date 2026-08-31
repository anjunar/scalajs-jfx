package jfx.json

import reflect.{ParameterizedTypeDescriptor, TypeDescriptor}

private[json] final case class JsonMappingContext(
    expectedType: TypeDescriptor,
    bindings: Map[String, TypeDescriptor],
    schemas: JsonSchemaCatalog
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

    JsonMappingContext(resolved, bindings ++ childBindings, schemas)
  }
}

private[json] object JsonMappingContext {

  def root(meta: TypeDescriptor, schemas: JsonSchemaCatalog): JsonMappingContext =
    JsonMappingContext(meta, JsonTypeModel.typeBindings(meta), schemas)
}
