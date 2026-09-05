package jfx.editor

/** CSS class contract shared by the semantic and enhanced editor presentations. */
private[editor] object EditorStyles {
  val paragraph: String      = "lexical-paragraph"
  val quote: String          = "lexical-quote"
  val horizontalRule: String = "lexical-horizontal-rule"
  val unorderedList: String  = "lexical-list-ul"
  val orderedList: String    = "lexical-list-ol"
  val listItem: String       = "lexical-listitem"
  val bold: String           = "lexical-text-bold"
  val italic: String         = "lexical-text-italic"
  val underline: String      = "lexical-text-underline"
  val strikethrough: String  = "lexical-text-strikethrough"
  val code: String           = "lexical-text-code"

  def heading(level: Int): String = s"lexical-heading-h$level"
}
