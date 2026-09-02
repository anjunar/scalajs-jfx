package jfx.editor.plugins

import jfx.core.component.Runtime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class PluginDialogSpec extends AnyFlatSpec with Matchers {

  "LinkPlugin dialog" should "compose its content through JFX components" in {
    val context = LinkDialogContext(
      editor = null,
      selection = null,
      currentUrl = "https://example.test/?a=1&b=2",
      dialogTitle = "Insert link",
      urlLabel = "Destination",
      urlPlaceholder = "https://example.test"
    )

    val html = render(new LinkDialogContent(context))

    html should include("class=\"link-plugin-dialog\"")
    html should include("<label for=\"link-url-input\">Destination</label>")
    html should include("type=\"url\"")
    html should include("placeholder=\"https://example.test\"")
    html should include("value=\"https://example.test/?a=1&amp;b=2\"")
  }

  "ImagePlugin dialog" should "compose picker, preview and fields through JFX components" in {
    val plugin = new ImagePlugin()
    plugin.defaultWidthPx = 0
    plugin.previewMaxHeightPx = 0

    val html = render(plugin.createDialogContent(None))

    html should include("class=\"image-plugin-dialog\"")
    html should include("type=\"file\"")
    html should include("accept=\"image/*\"")
    html should include("aria-label=\"Click to select an image\"")
    html should include("id=\"image-preview\"")
    html should include("max-height: 1px")
    html should include("id=\"image-alt-input\"")
    html should include("id=\"image-width-input\"")
    html should include("value=\"1\"")
  }

  private def render(content: DialogContent): String =
    Runtime.renderToString(cursor => Runtime.mount(content, cursor))
}
