# @anjunar/scalajs-jfx

Default CSS for the classes emitted by the Scala.js JFX modules. It supplies component rules and consumes design tokens from `@anjunar/ui`; it does not contain rendering or component logic.

## Overview

The Scala modules render `jfx-*` class names. This package makes those classes visible and keeps the styling contract in one versioned package shared by Scala and TypeScript consumers.

## Installation

```bash
npm install @anjunar/scalajs-jfx @anjunar/ui
```

Import the stylesheet from the application's CSS entry point:

```css
@import "tailwindcss";
@import "@anjunar/ui";
@import "@anjunar/scalajs-jfx/index.css";
```

Alternatively, import `@anjunar/scalajs-jfx` as a JavaScript side effect when your bundler handles CSS side effects.

## Styling ownership

`@anjunar/ui` owns the `--aj-*` design tokens. This package owns the `.jfx-*` component rules and shared component state classes. Tailwind utilities and application CSS own application layout and application-specific class names. Inline styles from the JFX style DSL are for runtime values such as measured dimensions or transforms.

The package styles class names rather than bare elements, so applications should provide an element reset such as Tailwind Preflight. It includes Material Icons typography rules but no font binary.

## Contents

```text
base/     shared icon rules
action/   buttons
control/  carousel, data grid, links, tables, tabs, virtual lists
form/     combo boxes, editor, image cropper, inputs
layout/   drawer, boxes, viewport, windows
```

`form/Editor.css` is included for the editor package even when the Scala editor artifact is released separately.

## API overview

This is a CSS-only package. Its public surface is the stylesheet entry point `index.css` and the class names rendered by the JFX components.

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) emits core elements and state.
- [`@anjunar/jfx-controls`](../jfx-controls/README.md), [`@anjunar/jfx-forms`](../jfx-forms/README.md), and [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) emit feature classes.
