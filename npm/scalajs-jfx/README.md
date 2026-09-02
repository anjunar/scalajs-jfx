# @anjunar/scalajs-jfx

Default component CSS for the Scala.js modules published as
`com.anjunar::scalajs-jfx-*`.

The Scala modules render class names; this package renders those class names
visible. Both halves are versioned together — the npm major matches the Maven
major.

## Install

```bash
npm install @anjunar/scalajs-jfx @anjunar/ui
```

Import the shared Anjunar UI grammar and this package from your application
stylesheet:

```css
@import "tailwindcss";
@import "@anjunar/ui";
@import "@anjunar/scalajs-jfx/index.css";
```

Or import the package root for the same effect as a side effect:

```javascript
import '@anjunar/scalajs-jfx'
```

## Requirements

- **A modern element reset.** This package styles class names only, never bare
  elements, so it never touches `fieldset`, `button` or `input` defaults. If you
  do not use Tailwind Preflight, bring an equivalent reset.
- **Design tokens.** Colours, surfaces, lines and shadows are read from the
  `--aj-*` custom properties defined by `@anjunar/ui`. Override them to theme
  the components; do not override the component rules.
- **An icon font**, if you want glyphs. The components render
  `.material-icons`, and this package sets the typography for that class but
  ships no font binary. Provide a font family named `Material Icons` — via
  Google Fonts, the `material-symbols` package or your own `@font-face`.
  Without one, the components lay out correctly and show the ligature text.

## Which styling system owns what

Four systems can style a JFX application. The boundaries:

| System | Owns | Never |
| --- | --- | --- |
| `@anjunar/ui` | Design tokens (`--aj-*`), the shared grammar | Component rules |
| This package | Every `.jfx-*` class a published module renders, plus the shared state classes (`.is-active`, `.is-open`, …) scoped to a component | Bare element selectors, application layout, page-specific looks |
| Tailwind utilities | Application layout and one-off spacing in *application* markup | Anything a library component renders — a utility cannot reach into it |
| Application CSS | The application's own class names, and deliberate overrides of component classes | Redefining tokens per component instead of theming `--aj-*` |

Two consequences worth stating outright:

- A rule for a class that a Scala module renders belongs **here**, not in the
  application. If it lives only in the application, every other consumer of the
  Maven artifact gets an unstyled component.
- An application override of a `.jfx-*` class is legitimate, but it is an
  override: keep it in one place, and prefer moving the change into a token.

Inline styles from `jfx.core.dsl.StyleDsl` are the fourth path. They are for
values only known at runtime (a measured width, a computed transform), not for
appearance.

## Contents

```
base/     rules shared by every component (icon typography)
action/   Button
control/  Carousel, DataGrid, Link, TableCell, TableView, Tabs, VirtualListView
form/     ComboBox, Editor, ImageCropper, Input, InputContainer
layout/   Drawer, HBox, HorizontalLine, VBox, Viewport, Window
```

`form/Editor.css` covers `scalajs-jfx-editor`, which is not published to Maven
Central yet. The CSS ships anyway so the module can be published without a
second release here.
