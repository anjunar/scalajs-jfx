# Full-height presentation scrolling

## Structure and decision

The ten original topics are divided at editorial content boundaries into 21 slides. This is server-rendered structure, not client-side pagination; resizing, changing designs and hydration never move content between slides.

1. One runtime. Two APIs.
2. Same UI. Two languages.
3. From server HTML to interaction
4. What you get
5. Built for application UI
6. Application building blocks
7. Forms that connect to your model
8. Data views with room to grow
9. Rich editing. A Markdown value.
10. Routes are application structure
11. A different trade-off
12. Compare the approaches
13. Get started
14. Add JFX to your project
15. Mount your component
16. Complete starter files
17. Run your application
18. Two ways in. One implementation.
19. Go beyond the first example
20. Why JFX exists
21. Explore JFX

The final user requirement is `height: 100%` for every station. The stylesheet sets that height on `html`, `body`, `main` and each direct presentation child. Each slide therefore occupies exactly the viewport height, including its padding and borders. No JavaScript sizing pass or content scaling is involved. Print restores ordinary content height.

The header belongs to the opening slide, and the footer belongs to the closing slide. Their existing content and landmark roles are retained. Every slide has native internal overflow; long examples and expanded starters remain completely reachable. A stable scrollbar gutter prevents newly overflowing content from changing its width.

Short slides use `align-content: safe center`; content that exceeds the available space aligns at the start. The opening slide keeps its header at the top and centers the hero in the remaining height with flex auto margins. Section padding uses the existing spacing tokens, and component previews sit beside their explanations on desktop. No whole-slide colored card surrounds the code or final CTA.

Native CSS scroll snap supplies the baseline on the document. It does not define gesture grouping or animation physics, so it cannot alone promise one trackpad burst per slide. A small module supplements it on desktop; no Fullpage library, extra runtime or application-wide service is involved. See [MDN scroll-snap-type](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-snap-type), [wheel events](https://developer.mozilla.org/en-US/docs/Web/API/Element/wheel_event) and [scrollend](https://developer.mozilla.org/en-US/docs/Web/API/Document/scrollend_event).

## Input contract

- Desktop means a fine pointer with hover and at least 64rem viewport width. Smaller/zoomed layouts keep native scrolling. Pinch-zoom wheel events are not captured.
- An outer vertical wheel gesture moves one slide when the current slide has no more content in that direction. A 28px accumulated threshold ignores tiny incidental deltas. Pixel, line and page wheel units are normalized.
- Within an overflowing slide, wheel deltas scroll the slide's contents and stop at the reading boundary. Leaving requires a fresh gesture. Reading positions survive returning to a slide.
- All further events of a turn are consumed until its native animation ends and the wheel stream has been quiet for 220ms. Inertia remains owned by its original turn even if a code block moves under the pointer. There is no queued next turn or wraparound at the end.
- ArrowDown/ArrowUp change slides at boundaries and otherwise move 80px through the current content. PageDown/PageUp and Space/Shift+Space use reading pages with 15% overlap. Key repeats cannot queue transitions.
- Links, buttons, summaries, inputs, selects, editable content, selected text, dialogs and nested scroll regions retain the relevant native input. Horizontal gestures do not become vertical navigation. `data-presentation-native` can reserve a future widget's input explicitly.
- Touch stays native. There is no custom touch gesture recognizer. Scrollbar dragging, Escape, Tab, focus movement, resizing and anchor/history navigation cancel an outstanding transition. The controller never changes focus or the URL.
- Native fragment links and visible keyboard focus take precedence over outer snapping, including code targets inside disclosures. No-JS mode retains all content, native scroll regions, anchors, disclosures and source downloads.
- Reduced motion uses immediate movement. Otherwise `scrollTo({ behavior: 'smooth' })` lets the browser perform the animation; `scrollend` and a bounded position check guard the transition. Event handlers and observers have a disposal function; BFCache suspension cancels motion without discarding page state.

Wheel events do not expose a portable physical-gesture boundary. The quiet interval is a deliberately small heuristic; it cannot certify every mouse driver's or trackpad's momentum profile.

## Verification

`npm test` runs eleven deterministic interaction tests with JSDOM. `npm run verify` includes those tests, both builds, prerendering, source and document checks, and the existing development/production checks for every language/design/scheme combination.

Browser checks used a separate headless Chrome instance and actual browser wheel/keyboard input:

- All four designs in light/dark: exactly 21 full-height slides, with no internal overflow in the default collapsed state at 1280 × 720 and 1440 × 900 CSS pixels; forward/backward navigation.
- Thirty small wheel events: one slide, including inertia after animation.
- Expanded starter files: complete internal scroll range, burst containment, fresh gesture at the boundary and retained reading position on return.
- PageDown/ArrowUp inside expanded starter files; native code focus, disclosures and direct anchors.
- 640 × 450 CSS viewport: full-height slides, native internal scrolling; no forced desktop controller.
- Reduced motion: immediate transition.
- JavaScript disabled: anchors, expanded starters and internal reading remain available.
- 390 × 844 touch viewport: browser-native swipe and no document-width overflow.
- Last slide and footer reachable without wraparound; no browser page errors.

On mobile, narrow/zoomed layouts and very short viewports, text may still need native internal scrolling. Expanded source disclosures deliberately remain readable in their slide rather than causing dynamic slide insertion.

These are Chromium and simulated input checks, not a physical-trackpad or screen-reader device matrix. The complete assembled Pages output is link/asset-validated after updating the landing bundle.
