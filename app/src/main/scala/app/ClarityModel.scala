package app

enum ClarityState(
  val label: String,
  val cssName: String,
  val summary: String,
  val discipline: String
):
  case Raw
      extends ClarityState(
        label = "RAW",
        cssName = "raw",
        summary = "Unformed, protected and not yet ready for evaluation.",
        discipline = "Keep intake safe and incomplete."
      )
  case Clarification
      extends ClarityState(
        label = "CLARIFICATION",
        cssName = "clarification",
        summary = "Tension stays visible while contradictions are worked through.",
        discipline = "Hold conflict without rushing resolution."
      )
  case Condensed
      extends ClarityState(
        label = "CONDENSED",
        cssName = "condensed",
        summary = "Structure emerges and decisions begin to connect.",
        discipline = "Reduce without flattening meaning."
      )
  case Archived
      extends ClarityState(
        label = "ARCHIVED",
        cssName = "archived",
        summary = "Stable, referenceable and optimized for reading.",
        discipline = "Keep knowledge quiet, findable and intact."
      )

object ClarityState:
  val ordered: Vector[ClarityState] =
    Vector(Raw, Clarification, Condensed, Archived)

  def canTransition(from: ClarityState, to: ClarityState): Boolean =
    (from, to) match
      case (Raw, Clarification)                     => true
      case (Clarification, Raw | Condensed)        => true
      case (Condensed, Clarification | Archived)   => true
      case (Archived, Clarification)               => true
      case _                                       => false

  def transitionTargets(from: ClarityState): Vector[ClarityState] =
    ordered.filter(target => canTransition(from, target))

  def transitionNarrative(from: ClarityState, to: ClarityState): String =
    (from, to) match
      case (Raw, Clarification) =>
        "The record leaves protected intake and enters explicit clarification."
      case (Clarification, Raw) =>
        "The record returns to intake because it still needs protection."
      case (Clarification, Condensed) =>
        "The active tension has been reduced into a more coherent structure."
      case (Condensed, Clarification) =>
        "The structure is reopened because important conflict remains unresolved."
      case (Condensed, Archived) =>
        "The record is stable enough to become quiet reference material."
      case (Archived, Clarification) =>
        "The archive is reopened because context changed or contradiction surfaced."
      case _ =>
        s"${from.label} cannot move directly to ${to.label}."

  def invalidTransitionMessage(from: ClarityState, to: ClarityState): String =
    s"${from.label} cannot move directly to ${to.label}. Every transition must stay explicit."

final case class ShowcaseRoute(
  path: String,
  title: String,
  summary: String,
  zone: String,
  section: String,
  state: ClarityState,
  note: String
)

object ShowcaseCatalog:
  val manifest: ShowcaseRoute =
    ShowcaseRoute(
      path = "/",
      title = "Technology Speaks",
      summary = "The shell turns the design manifesto into route structure, state visibility and calm guidance.",
      zone = "Orientation",
      section = "Manifest",
      state = ClarityState.Clarification,
      note = "Start with tension made visible, not flattened."
    )

  val formWorkspace: ShowcaseRoute =
    ShowcaseRoute(
      path = "/form",
      title = "Raw Workspace",
      summary = "Typed forms become a protected intake surface with revision logs and explicit transitions.",
      zone = "Work",
      section = "Workspace",
      state = ClarityState.Raw,
      note = "No forced completion, no silent overwrite."
    )

  val clarificationQueue: ShowcaseRoute =
    ShowcaseRoute(
      path = "/table",
      title = "Clarification Queue",
      summary = "Remote data stays meaningful by exposing state, maturity and tension in the same field.",
      zone = "Work",
      section = "Workspace",
      state = ClarityState.Clarification,
      note = "Conflict is a signal, not an error."
    )

  val condensedContext: ShowcaseRoute =
    ShowcaseRoute(
      path = "/window",
      title = "Condensed Context",
      summary = "Windows and notifications support secondary work without collapsing the main surface.",
      zone = "Context",
      section = "Workspace",
      state = ClarityState.Condensed,
      note = "Use motion only when it improves understanding."
    )

  val referenceAtlas: ShowcaseRoute =
    ShowcaseRoute(
      path = "/docs",
      title = "Reference Atlas",
      summary = "Component knowledge enters a quiet archived layer without losing live examples.",
      zone = "Reference",
      section = "Reference",
      state = ClarityState.Archived,
      note = "Archive is stable, but still connected to working examples."
    )

  val navigation: Vector[ShowcaseRoute] =
    Vector(manifest, formWorkspace, clarificationQueue, condensedContext, referenceAtlas)

  val workspaceRoutes: Vector[ShowcaseRoute] =
    Vector(formWorkspace, clarificationQueue, condensedContext, referenceAtlas)

  def descriptorFor(path: String): ShowcaseRoute =
    navigation
      .find(route => route.path == path)
      .orElse {
        Option.when(path.startsWith("/docs/")) {
          ShowcaseRoute(
            path = path,
            title = docTitleFromPath(path),
            summary = "A stable component reference page with live embedded examples.",
            zone = "Reference",
            section = "Reference",
            state = ClarityState.Archived,
            note = "Archived knowledge stays readable, but never disconnected from behavior."
          )
        }
      }
      .getOrElse(manifest)

  private def docTitleFromPath(path: String): String =
    path.stripPrefix("/docs/")
      .split("-")
      .iterator
      .filter(_.nonEmpty)
      .map(segment => segment.head.toUpper + segment.drop(1))
      .mkString(" ")
