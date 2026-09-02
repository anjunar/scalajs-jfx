package jfx.core.render

/**
 * Ein durch Kommentar-Anker begrenzter Bereich.
 *
 * `adopted` traegt die Knoten zwischen den Ankern, wenn der Bereich uebernommen
 * statt beansprucht wurde. Sie gehoeren dann der Komponente und werden mit ihr
 * entfernt -- sonst blieben sie beim Austausch im DOM zurueck.
 */
final case class VirtualRange(
    start: CommentNode,
    end: CommentNode,
    cursor: Cursor,
    adopted: Seq[HostNode] = Nil
)
