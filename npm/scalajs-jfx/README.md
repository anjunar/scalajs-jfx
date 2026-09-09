# @anjunar/scalajs-jfx

Component CSS, four design packages and design roles for the classes emitted by the Scala.js JFX modules. The package supplies four independent light/dark palettes and component rules; it does not contain rendering or component logic.

## Overview

The Scala modules render `jfx-*` class names. This package makes those classes visible and keeps the styling contract in one versioned package shared by Scala and TypeScript consumers.

## Installation

```bash
npm install @anjunar/scalajs-jfx
```

Import the stylesheet from the application's CSS entry point:

```css
@import "@anjunar/scalajs-jfx/index.css";
```

Alternatively, import `@anjunar/scalajs-jfx` as a JavaScript side effect when your bundler handles CSS side effects.

## Styling ownership

`designs/*/tokens.css` owns the semantic roles from the neighbouring `lob-der-reinen-intuition/design` Multi Constitution. The four designs are `atlas`, `flora`, `terra` and `ember`. Set `data-design` and `data-color-scheme="light"` or `"dark"` on `<html>`. The defaults are Ember and dark, independent of the operating system.

`base/Theme.css` maps these roles to the existing `--aj-*` interface shared with Lexical. `base/Tokens.css` supplies structural aliases. Cascade layers separate component patterns, design decisions, utilities and accessibility invariants. Application styles belong in `layer(patterns)` so they do not accidentally override the chosen design.

Application CSS owns application layout and application-specific class names. Inline styles from the JFX style DSL carry runtime values such as measured dimensions, transforms, or the drawer's width custom properties. Static layout belongs in CSS. Accessibility rules preserve visible keyboard focus and reduced motion across visual designs.

Applications should provide an element reset; Tailwind Preflight is optional. This package contains no Tailwind directives and does not load `@anjunar/ui`. It includes Material Icons typography rules but no font binary. The currently published `@anjunar/scalajs-lexical` package still declares an `@anjunar/ui` peer dependency, so editor consumers may see that unused package in their installation tree.

## Contents

```text
designs/  metadata, generated registry, complete palettes and design rules
base/     palette, structural roles, accessibility and icon rules
action/   buttons
control/  carousel, data grid, links, tables, tabs, virtual lists
form/     combo boxes, editor, image cropper, inputs
layout/   drawer, boxes, viewport, windows
```

`form/Editor.css` is included for the editor package even when the Scala editor artifact is released separately.

## API overview

The stylesheet entry point is `index.css`. The optional `@anjunar/scalajs-jfx/preferences` module exports `designs`, `serverPreferences(url)`, `bootstrapScript(legacyKey)` and `createPreferences(legacyKey, url)`.

Place the bootstrap in the document head before paint. It resolves validated URL previews (`?design=flora&colorScheme=dark`), then browser storage, then defaults. Preview URLs do not write storage. The controller updates only the document attributes; it does not remount application content. Its `subscribe` method returns an unsubscribe function which the app must dispose. Each server render creates its own state.

User choices are stored separately in `jfx.design` and `jfx.color-scheme`; the optional legacy key is only used to read an earlier light/dark choice. Storage failures leave the current page usable and are reported in controller state. The demos use localStorage, not cookies: SSR uses URL preferences or defaults; the pre-paint bootstrap restores browser preferences before hydration.

Regenerate the registry after metadata changes with `npm run build:registry`. `npm run verify` checks registry drift, complete token coverage in each design, standalone CSS and preference behavior. See the repository's [MULTI_DESIGN.md](../../MULTI_DESIGN.md) for integration details.

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) emits core elements and state.
- [`@anjunar/jfx-controls`](../jfx-controls/README.md), [`@anjunar/jfx-forms`](../jfx-forms/README.md), and [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) emit feature classes.
