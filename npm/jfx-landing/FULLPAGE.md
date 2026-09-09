# Reading and optional presentation

The design review of 9 September 2026 replaces the former forced full-height
interaction contract. Reading is the default, including the prerendered page and
visits without JavaScript. All six core capabilities now share one section; the
page contains 20 sections with natural document flow.

## Presentation preference

Menu → Page view offers Reading and Presentation. The landing-only storage key
`scalajs-jfx.landing.view` persists the choice. A valid `?view=reading` or
`?view=presentation` overrides storage. The inline bootstrap restores the choice
before the stylesheet loads; the client keeps the select, URL and localized
fragment links synchronized. Blocked storage leaves both views usable and reports
that persistence is unavailable. Language changes preserve the view URL parameter.

At a minimum width of 64rem and height of 40rem, Presentation gives each section a
minimum height of the viewport minus the sticky header. Longer sections, including
expanded starter files, grow naturally. Below either threshold, both views use the
ordinary reading layout. There are no chapter scroll containers or fixed heights.

CSS proximity snapping supplies optional chapter alignment. No wheel, touch or
keyboard event is intercepted, and there is no gesture threshold or inertia lock.
Fragment targets, visible keyboard focus and reduced motion disable snapping.
Print always uses natural content height. Code and table regions keep their own
horizontal scrolling where needed.

## Reachable navigation

The compact sticky header is a sibling of main, with direct Docs and GitHub links.
A native details menu contains eight section links and language, design,
appearance and view controls. It works as a disclosure without JavaScript;
preference controls remain disabled in SSR. With JavaScript, Escape returns focus
to its summary, moving focus or clicking outside closes it, and section navigation
moves focus to the destination. ResizeObserver supplies the actual header height
for fragment clearance and presentation sizing, including font and design changes.
The menu has a bounded scroll area when its own controls exceed the viewport.

## Verification

`npm run verify` runs five deterministic tests of defaults, bootstrap precedence,
URL/storage persistence, blocked storage, history/BFCache and native input. It also
builds and prerenders both languages, verifies section/CTA/navigation structure,
source parity and lazy hydration, and checks all 16 language/design/scheme
combinations in each of the development and production servers, including the
no-JavaScript CSP, CSS delivery, source downloads and 404 responses.

These are automated behavior, document and server checks. They do not claim a new
rendered-browser measurement, physical-trackpad test, screen-reader audit or Core
Web Vitals measurement; the historical browser checks described in DESIGN_REVIEW.md
apply to the reviewed version, not this replacement.
