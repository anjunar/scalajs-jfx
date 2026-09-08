# Multi Design in the JFX demos

The Scala.js demo, `npm/jfx-demo` and `npm/jfx-landing` consume the same four designs from `npm/scalajs-jfx/designs`. Metadata and semantic token values originate in the neighbouring `lob-der-reinen-intuition/design` project. The JFX-specific `design.css` files interpret its layout slots for the component showcase.

| ID | Name | Character |
| --- | --- | --- |
| `atlas` | Atlas | Cool blue, sans headings, side navigation |
| `flora` | Flora | Rose/plum, Georgia headings, generous rounded surfaces |
| `terra` | Terra | Stone/green, Cambria headings, restrained rules |
| `ember` | Ember | Paper/copper/green, strong sans headings, clear rules |

Each supports explicit light and dark. There is no System/Auto choice and no operating-system theme query. Atlas/light is the default.

## Ownership

- `design.json` is source metadata; `scripts/build-design-registry.mjs` generates the registry. Complete token roles are checked against `tokens.required.json`.
- `index.css` loads every design in a fixed cascade. `base/Theme.css` adapts the semantic roles to existing JFX/Lexical `--aj-*` names. No `@anjunar/ui` stylesheet is needed.
- Both demos render a semantic header, collapsible navigation, main and footer. Design and scheme are native labelled selects, disabled in JavaScript-free SSR. All four designs use the same Atlas navigation arrangement: a side rail on desktop and a collapsible navigation above the content on narrow screens. Design CSS chooses typography, spacing, surfaces and colors; routes and live examples are mounted once.
- `preferences.js` owns validated URL resolution, browser storage, cross-tab synchronization and root attributes. Scala `AppTheme` and TypeScript `preferenceControls` own per-app subscriptions and dispose them with the component tree.
- The shared showcase rules live in `application/src/main/webapp/src/app/{Main,Demo,Showcase,DesignPatterns}.css`; TypeScript imports those same files. Component-library behavior remains independent of showcase chrome.

## SSR and hydration

Preview any route using `?design=ember&colorScheme=dark`. SSR emits the requested root attributes and selected options. With no preview it emits Atlas/light. A literal head script reads localStorage before paint, without changing the server-rendered component tree. After the controls compose, their DOM values reconcile with the chosen preferences. Changing designs preserves form values, counters, editor state and navigation state.

The demos use separate storage keys `jfx.design` and `jfx.color-scheme`, shared between both APIs on the same origin. URL previews override stored values without persisting merely by visiting. A deliberate user choice persists its own axis and updates that axis in a preview URL, preserving other query parameters, the hash and history state. Invalid values fall back safely. A blocked storage write displays a localized notice while keeping the selection active for the current page.

This is the demo adaptation of the Multi Constitution. Server cookie negotiation and the source project's publishing workflow are not implemented here. The landing page now lives in `npm/jfx-landing`, consumes the same preference and style package, and supplies English and German documents with a language selector. The product page keeps its own layout; its live counter loads the shared runtime on demand.

## Verification

Run `npm ci` at the repository root first: the Scala demo SSR tests import the shared preference module from the npm workspace. Use `sbt --server "Test/testOnly *"` for all Scala modules. Build the bridge before npm verification with `sbt --server "scalajs-jfx-bridge/fullLinkJS"`. Run `npm run verify` in `npm/scalajs-jfx`, `npm/jfx-core` and `npm/jfx-demo`. Avoid running the core build and demo verification concurrently because the demo consumes the core build output.

The automated checks include every design/scheme SSR combination, per-render state isolation, token completeness, invalid/blocked storage, preview precedence and listener disposal. Browser checks cover both demos, live switching without counter resets and responsive layouts. These checks do not constitute a complete accessibility audit of every existing showcase component.
