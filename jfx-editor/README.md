# scalajs-jfx-editor

Rich-text editing for JFX3, backed by Lexical and exposed as a regular
`Control[js.Any | Null]`. The value is Lexical `EditorState` JSON as a JavaScript
object; it is neither HTML nor a string serialization.

```scala
import jfx.forms.Editor.*
import jfx.forms.editor.plugins.*

editor("body") {
  placeholder = "Write the article..."
  ribbonToolbar()

  basePlugin()
  headingPlugin()
  listPlugin()
  linkPlugin()
  imagePlugin()
  tablePlugin()
  codePlugin()
  horizontalRulePlugin()
}
```

The component renders a deterministic semantic preview for SSR and hydration.
Lexical owns only the dedicated client-side editing surface and toolbar hosts.
All editor/plugin registrations are tied to the component lifecycle.

Link and image plugins use `DefaultDialogService` unless a service is assigned
to the editor or provided through `Editor.DialogServiceContext`. The default
service bridges Lexical's foreign `HTMLElement` dialog content into a JFX3
`Viewport.WindowConf`; window presentation and lifecycle remain owned by the
global JFX3 viewport.
