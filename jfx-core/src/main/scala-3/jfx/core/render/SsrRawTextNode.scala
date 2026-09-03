package jfx.core.render

/** Text that reaches the SSR output unescaped.
  *
  * The content of `<script>` and `<style>` is raw text in HTML: a parser reads it verbatim, so
  * escaping `&` or `<` in there corrupts the payload instead of protecting it. [[SsrTextNode]]
  * escapes, which is right everywhere a component mounts text. Only
  * [[jfx.core.document.HeadSink]] builds these nodes, and it decides per entry whether the value is
  * raw or escaped -- there is no component that mounts one.
  */
final class SsrRawTextNode(val value: String) extends HostNode, SsrNode {
  def renderHtml(): String = value
}
