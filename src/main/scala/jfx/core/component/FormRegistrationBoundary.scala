package jfx.core.component

import org.scalajs.dom.Node

/**
 * Marker trait: if a component mixes this in, its subtree will not be registered into any enclosing
 * {@link jfx.form.Formular}. Useful for components that manage binding manually (e.g. array repeaters).
 */
trait FormRegistrationBoundary { self: NodeComponent[? <: Node] => }
