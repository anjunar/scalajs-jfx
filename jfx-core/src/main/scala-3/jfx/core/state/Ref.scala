package jfx.core.state

import jfx.core.component.AbstractComponent

class Ref[A <: AbstractComponent](var value: A = null)