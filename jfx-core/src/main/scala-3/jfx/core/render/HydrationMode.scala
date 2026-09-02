package jfx.core.render

enum HydrationMode {

  /** Every server-rendered node must be claimed by the client tree. */
  case Strict

  /** `<head>`: order and completeness are not guaranteed there. */
  case Head

  /** The range is adopted without validating its contents.
    *
    * Intended for when the client cannot yet rebuild the nodes -- for example, because a route
    * loader is still running. SSR content remains visible and is replaced once the client has its
    * own tree. See CHANGE.md P4-1.
    */
  case Adopt
}
