# DESIGN.md — Scala JFX Design Guide

Project: Technology Speaks
Principle: Manifest der Stille

---

## 1. Core Philosophy

This UI is not built to optimize speed or engagement.

It is built to support clarity emerging from confusion.

The system must:

* hold tension instead of resolving it prematurely
* allow raw thought without forcing completion
* provide structure without becoming rigid
* remain calm without becoming empty

Silence is not an aesthetic.
Silence is a functional property of the system.

---

## 2. Ontology of States

Every meaningful entity MUST have an explicit state.

States are not visual labels.
States define behavior, visibility, and interaction.

### Canonical States

* RAW
  Unformed, unvalidated, protected
  → no public evaluation

* CLARIFICATION
  Tension is active
  → contradiction allowed and visible

* CONDENSED
  Structure emerges
  → reduced, refined, connected

* ARCHIVED
  Stable, referenceable
  → read-focused, minimal interaction

### Rules

* No direct transition from RAW → ARCHIVED
* Every transition must be explicit
* UI must reflect state structurally, not decoratively

---

## 3. Spatial Design (Layout)

Layout is separation before beauty.

### Principles

* Maximum 2–3 primary zones per screen

* Each zone has a clear semantic role:

    * Orientation
    * Work
    * Context
    * Unresolved

* Hard boundaries between zones

* Soft internal composition within zones

### Constraints

* No mixed states in the same visual layer
* No “flat” layouts where everything competes equally
* White space is functional, not aesthetic

### Implementation Hint (Scala JFX)

* Prefer explicit container hierarchy
* Avoid dynamic layout ambiguity
* Each container represents meaning, not just grouping

---

## 4. Visual Language

### Density

* Low to medium density
* High semantic precision
* No visual noise

### Color

* Base palette: calm, desaturated
* Accent color ONLY for orientation-critical signals
* No decorative color usage

### Contrast

* Used for distinction, not stimulation
* Important > visible
  Not important > quiet or hidden

### Typography

* Few font weights
* Clear hierarchy
* No decorative styles

Text must:

* clarify
* orient
* constrain
* invite

Never:

* impress
* decorate
* dramatize

---

## 5. Components

Components are not UI elements.
They are functional units of clarity.

### Buttons

* Represent thresholds, not actions
* Few primary actions per screen
* Secondary actions visually reduced
* Destructive actions precise, not dramatic

### Inputs

* Spaces for clarification, not data entry
* Must support unfinished thinking
* No forced completion

### Lists / Tables

* Not data grids → meaning fields

* Rows must express:

    * state
    * maturity
    * tension level

* Avoid uniform rendering

### Cards

* Only when semantic independence exists
* Avoid fragmentation of space

---

## 6. Interaction Model

Interaction must slow down impulsive behavior.

### Core Rules

* No instant validation loops (e.g. likes)
* No reward-driven interaction

### Replace with:

* Resonance (reflection)
* Clarification (questions)
* Condensation (synthesis)

### Behavior

* User can:

    * park unfinished thoughts
    * revise anytime
    * move content through states

* System must:

    * not force completion
    * not collapse ambiguity too early

---

## 7. Conflict Handling

Conflict is not an error.
It is a signal.

### System must differentiate:

* misunderstanding
* contradiction
* contextual mismatch
* ego reaction

### UI must:

* make conflict visible
* not resolve automatically
* guide clarification

---

## 8. Revision Model

Revision is first-class.

### Rules

* No destructive overwrite
* Everything versioned
* History always accessible

### UX

* Editing is continuation, not correction
* Old states remain visible when relevant

---

## 9. Focus & Attention

Focus is a structural concept.

### Rules

* Focus ≠ selection
* Focus must be:

    * calm
    * precise
    * visible but not loud

### Avoid

* aggressive highlights
* animation as attention grabber

---

## 10. Animation & Motion

Motion is only allowed if it increases understanding.

### Allowed

* subtle transitions
* spatial continuity

### Forbidden

* decorative animation
* attention-seeking motion

---

## 11. Error Handling

Error is material, not failure.

### Rules

* no panic signals
* no moral tone
* no blame

### System must:

* name the issue precisely
* offer next step
* differentiate error types

---

## 12. Architecture Alignment (Scala JFX)

UI must reflect domain truth.

### Rules

* No hidden state in UI
* All state derived from domain
* Explicit state transitions only

### Components

* Stateless rendering preferred
* State injected, not created internally

### Lifecycle

* Components must:

    * appear
    * serve
    * dispose cleanly

Dispose is not technical.
Dispose is philosophical.

---

## 13. Anti-Patterns

Strictly avoid:

* Over-harmonization
* Pseudo-silence (empty minimalism)
* Instant feedback systems (likes, ranking)
* Mixing states in one view
* Decorative UI without function
* Premature clarity
* Hidden system behavior
* Identity-driven UI (ego amplification)
* Conflict suppression
* Over-animation
* Unbounded components (memory, listeners, scope leaks)

---

## 14. Final Principle

This system is not designed to make users faster.

It is designed to make perception clearer.

If a design decision:

* increases speed but reduces clarity → reject
* increases clarity but reduces speed → accept

---

End of Guide
