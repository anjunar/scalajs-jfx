package app

final case class ShowcaseRoute(
  path: String,
  title: String,
  summary: String,
  zone: String,
  section: String,
  note: String
)

object ShowcaseCatalog:
  val manifest: ShowcaseRoute =
    ShowcaseRoute(
      path = "/",
      title = "Start here",
      summary = "Get a quick overview and choose the part of the framework you want to explore.",
      zone = "Overview",
      section = "Start",
      note = "Best first stop if you are new to the demo."
    )

  val formWorkspace: ShowcaseRoute =
    ShowcaseRoute(
      path = "/form",
      title = "Forms Workspace",
      summary = "See typed inputs, nested forms, image editing and revision history on one page.",
      zone = "Example",
      section = "Examples",
      note = "Open this page if you want to understand form binding."
    )

  val dataQueue: ShowcaseRoute =
    ShowcaseRoute(
      path = "/table",
      title = "Data Queue",
      summary = "See loading, filtering, sorting and record selection in a realistic table view.",
      zone = "Example",
      section = "Examples",
      note = "Open this page for async lists and table behavior."
    )

  val windowWorkspace: ShowcaseRoute =
    ShowcaseRoute(
      path = "/window",
      title = "Window Workspace",
      summary = "See floating windows, notifications and side tasks without leaving the main screen.",
      zone = "Example",
      section = "Examples",
      note = "Open this page to see overlay and viewport patterns."
    )

  val referenceAtlas: ShowcaseRoute =
    ShowcaseRoute(
      path = "/docs",
      title = "Component Docs",
      summary = "Browse components with short explanations, imports and live examples.",
      zone = "Reference",
      section = "Reference",
      note = "Use this page when you want to look up an API quickly."
    )

  val navigation: Vector[ShowcaseRoute] =
    Vector(manifest, formWorkspace, dataQueue, windowWorkspace, referenceAtlas)

  val workspaceRoutes: Vector[ShowcaseRoute] =
    Vector(formWorkspace, dataQueue, windowWorkspace, referenceAtlas)

  def descriptorFor(path: String): ShowcaseRoute =
    navigation
      .find(route => route.path == path)
      .orElse {
        Option.when(path.startsWith("/docs/")) {
          ShowcaseRoute(
            path = path,
            title = docTitleFromPath(path),
            summary = "A component reference page with a short explanation and a live example.",
            zone = "Reference",
            section = "Reference",
            note = "Use this page when you want details for one component."
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
