package jfx.bridge

import jfx.core.component.AbstractComponent

/** A plain element for an arbitrary tag name.
  *
  * `jfx-core`'s own elements (`Div`, `Span`, ...) hardcode `tagName`, one class per tag, because
  * Scala's DSL spells the tag as a function name (`div { ... }`). `ScopeHandle.child(tagName, body)`
  * takes the tag as a runtime string instead -- that is the whole point of keeping `element()` in
  * `dsl.ts` a one-line factory rather than a hardcoded list -- so the bridge needs exactly one
  * component class parametrized by it, not a matching Scala class per tag.
  */
final class GenericElement(val tagName: String) extends AbstractComponent
