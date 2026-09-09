# Constitution applied to the JFX landing page

Source: `../../../lob-der-reinen-intuition/design/` (neighbouring project), especially `index.html`, `foundations.html`, `MULTI-DESIGN.md`, `TERRA.md`, `EMBER.md` and the four design packages. Applied on 8 September 2026.

## From principle to implementation

| Principle | Landing-page interpretation |
| --- | --- |
| Form übernimmt Last. | A clear title, named navigation, section headings, readable prose measures, code scroll regions and a semantic comparison table. A compact sticky header keeps navigation reachable; measured header height provides fragment clearance. The native menu consolidates mobile preferences. |
| Das Tempo gehört dem Menschen. | Runtime hydration starts on a deliberate click. Static loading text and an accurate busy state replace the decorative pulse. Hover changes color without moving the target; transitions use the shared response tokens. |
| Unfertiges hat Bleiberecht. | Switching designs preserves the mounted counter and open native details. Starter files and server content are available before activation. There is no new signup or mandatory step. |
| Intuition bekommt ein Gegenüber. | Existing architecture explanations, trade-offs, source links and exact source downloads remain intact. |
| Sorgfalt wird sichtbar. | Standalone controls have 44px minimum targets; fields use stronger semantic borders, focus has a 3px outline with 4px clearance, and errors remain explicit. Metadata and code use the small-text token rather than miniature fixed fonts. |
| Eigenart lässt Inhalt atmen. | Explanatory sections and demo destinations become open groupings. Surfaces enclose actual code and widget previews. All four designs use the same document with separate visual interpretations. |

## Four interpretations

- **Atlas:** compact capability register, clear rules and restrained tool surfaces.
- **Flora:** light system titles, generous intervals and soft surfaces around actual working examples.
- **Terra:** split editorial opening, strong heading hierarchy, brass wayfinding, green actions and open specimen rules.
- **Ember:** strong sans titles, compact monospace context, copper wayfinding and framed working areas.

Decorative accents use `--color-accent`; navigation/actions and focus use `--color-interactive`; syntax uses text, emphasis and success roles; success/error colors describe actual states. The typography and spacing scale is supplied by each complete shared design package. The design review adds landing-only system interface/headline stacks, an interface-font hero lead, and border contrast through the existing muted-text token. No alternative DOM, content order or design-specific JavaScript is introduced.

## Explicit project choices

The existing user choices take precedence: only Light/Dark (no System), and accessible but visually hidden names for the header selectors. English/German selection remains available. This change applies to the landing workspace, not the demos' previously agreed navigation layout. Server cookies remain outside this adaptation.

## Historical verification (8 September 2026)

- Workspace verification: client/server builds, prerendering, source parity, named table region/header semantics, shared preference bootstrap, both languages and all eight design/scheme combinations in development and production.
- Browser: all four desktop designs; 390px views in both schemes; English and German at 320 CSS pixels with no document overflow. Tables remain in their own horizontal scroll region.
- Rendered text sampled across all eight color combinations: lowest measured contrast 5.29:1 against its solid background. This is a text-color check, not a complete accessibility audit.
- Landing text and visible preview labels have no font sizes below the 14px small-text token at the default browser text size. Standalone controls meet 44px height.
- Keyboard focus reaches code regions with the 3px outline and 4px clearance. A hydrated counter stays at 2 through all four design switches; the busy state clears after activation.

No screen-reader study, full print audit or OS/browser matrix is claimed.

The 9 September 2026 review replaces the full-page scrolling contract. Current automated verification and its limits are recorded in [FULLPAGE.md](FULLPAGE.md) and [DESIGN_REVIEW.md](../../DESIGN_REVIEW.md).
