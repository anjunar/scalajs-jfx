package jfx.core.render

import scala.collection.mutable

/** Gemeinsame Basis der server-seitigen Knoten.
  *
  * Traegt einen Hinweis auf die eigene Position in der Geschwisterliste. Ohne ihn muss jede
  * Einfuegemarke linear gesucht werden, was den Aufbau grosser Listen quadratisch macht -- siehe
  * CHANGE.md P4-2.
  */
private[render] trait SsrNode {
  private[render] var siblingHint: Int = -1
}

private[render] object SsrNode {

  def hintOf(node: HostNode): Int = node match {
    case ssr: SsrNode => ssr.siblingHint
    case _            => -1
  }

  def setHint(node: HostNode, index: Int): Unit = node match {
    case ssr: SsrNode => ssr.siblingHint = index
    case _            => ()
  }

  /** Position von `node` in `nodes`, ueber den Hinweis abgekuerzt.
    *
    * Der Hinweis wird beim Einfuegen gesetzt, wo die Position ohnehin bekannt ist. Er veraltet nur
    * nach hinten -- Einfuegungen vor einem Knoten schieben ihn weiter, nie zurueck -- und ist damit
    * eine untere Schranke. Deshalb genuegt eine Vorwaertssuche ab dort, ueber die wenigen
    * Positionen, um die er seitdem gerueckt ist.
    *
    * Gefunden wird gegen die tatsaechliche Liste; schlaegt die Vorwaertssuche fehl, folgt die volle
    * Suche.
    */
  def indexIn(nodes: mutable.ArrayBuffer[HostNode], node: HostNode): Int = {
    val hint  = hintOf(node)
    var index = if (hint > 0 && hint < nodes.length) hint else 0

    while (index < nodes.length && !(nodes(index) eq node)) index += 1

    val found = if (index < nodes.length) index else nodes.indexOf(node)
    if (found >= 0) setHint(node, found)
    found
  }

  /** Fuegt `child` an `index` ein und haelt den Hinweis nach. */
  def insertInto(
      nodes: mutable.ArrayBuffer[HostNode],
      index: Int,
      child: HostNode
  ): Unit = {
    val safeIndex = index.max(0).min(nodes.length)
    if (safeIndex == nodes.length) nodes += child
    else nodes.insert(safeIndex, child)
    setHint(child, safeIndex)
  }

  /** Haengt `child` hinten an und haelt den Hinweis nach. */
  def appendTo(nodes: mutable.ArrayBuffer[HostNode], child: HostNode): Unit = {
    nodes += child
    setHint(child, nodes.length - 1)
  }
}
