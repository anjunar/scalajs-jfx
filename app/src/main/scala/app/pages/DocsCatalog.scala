package app.pages

final case class DocEntry(
  slug: String,
  category: String,
  name: String,
  packageName: String,
  tagline: String,
  summary: String,
  bullets: Vector[String],
  importCode: String,
  usageCode: String,
  patterns: Vector[(String, String)],
  apiPoints: Vector[(String, String)]
)

object DocsCatalog {

  val entries: Vector[DocEntry] = Vector(
    DocEntry(
      slug = "router",
      category = "Application",
      name = "Router",
      packageName = "jfx.router.Router",
      tagline = "Declarative route handling for page-sized DSL components.",
      summary = "Use the router when the app needs a small, composable navigation model without leaving the Scala DSL.",
      bullets = Vector(
        "Works with scoped route factories and typed route context access.",
        "Fits naturally into the same component tree as forms, windows and data views.",
        "Good foundation for GitHub Pages-style docs, demos and admin tools."
      ),
      importCode =
        """import jfx.dsl.Scope.inject
          |import jfx.dsl.Scope.singleton
          |import jfx.layout.Viewport.viewport
          |import jfx.router.{Route, Router}""".stripMargin,
      usageCode =
        """def appShell(): Unit =
          |  scope {
          |    singleton[Router] {
          |      Router(js.Array(
          |        Route.scoped(path = "/docs", factory = docsIndexPage()),
          |        Route.scoped(path = "/docs/form", factory = componentDocPage(DocsCatalog.find("form").get)())
          |      ))
          |    }
          |
          |    viewport {
          |      mount(inject[Router])
          |    }
          |  }""".stripMargin,
      patterns = Vector(
        "App Shell" -> "Register the router once in application scope and mount it inside the viewport.",
        "Docs And Marketing Sites" -> "Use nested routes to keep overview pages and deeper references in the same shell.",
        "Programmatic Navigation" -> "Trigger route changes from buttons, links or async actions through the injected router."
      ),
      apiPoints = Vector(
        "Router(routes)" -> "Create a router instance inside a scope.",
        "navigate(path)" -> "Move to a new route and update browser history.",
        "reload()" -> "Re-run the current route factory.",
        "Route.scoped(...)" -> "Define route factories with route context and scope support."
      )
    ),
    DocEntry(
      slug = "table-view",
      category = "Data",
      name = "TableView",
      packageName = "jfx.control.TableView",
      tagline = "Structured data presentation with sorting, virtualization and custom cells.",
      summary = "The table API is designed for real application data, not just static samples.",
      bullets = Vector(
        "Column definitions stay close to the model shape.",
        "Works with local collections and remote-backed list properties.",
        "Supports custom cell factories and sort integration."
      ),
      importCode =
        """import jfx.control.TableColumn.column
          |import jfx.control.TableView.tableView
          |import jfx.control.cell.PropertyValueFactory
          |import jfx.core.state.ListProperty""".stripMargin,
      usageCode =
        """val people = ListProperty(js.Array(loadPeople()*))
          |
          |tableView[Person] {
          |  items = people
          |  fixedCellSize = 34
          |
          |  column[Person, String]("First Name") {
          |    cellValueFactory = new PropertyValueFactory[Person, String]("firstName")
          |    sortable = true
          |    sortKey = "firstName"
          |  }
          |
          |  column[Person, String]("City") {
          |    cellValueFactory = features => features.getValue.address.get.city
          |    sortKey = "city"
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Column Ownership" -> "Keep sorting and display decisions near the column so the model mapping stays easy to read.",
        "Remote Friendly" -> "Pair the table with a remote list property when you want pagination, sorting and totals.",
        "Performance" -> "Use fixed row height for larger datasets so virtualization stays predictable."
      ),
      apiPoints = Vector(
        "tableView[T] { ... }" -> "Create a table for a typed model.",
        "column[T, V](label)" -> "Declare columns inline with the table.",
        "fixedCellSize" -> "Tune rendering for larger data sets.",
        "sortKey / sortable" -> "Connect columns to local or remote sorting."
      )
    ),
    DocEntry(
      slug = "remote-list-property",
      category = "Data",
      name = "RemoteListProperty",
      packageName = "jfx.core.state.ListProperty.remote",
      tagline = "A state primitive for lazy, remote-looking list loading.",
      summary = "Use remote list properties when you want a collection API that can page, sort and refetch from a loader.",
      bullets = Vector(
        "Encodes loading, errors, sorting and total counts next to the list.",
        "Supports range loading and scrolling into unloaded sections.",
        "Makes demo data look and behave like a backend integration."
      ),
      importCode =
        """import jfx.core.state.ListProperty
          |import jfx.core.state.ListProperty.RemoteLoader""".stripMargin,
      usageCode =
        """final case class PersonQuery(filter: String = "", sort: Seq[String] = Seq.empty, offset: Int = 0, size: Int = 20)
          |
          |val remotePersons = ListProperty.remote[Person, PersonQuery](
          |  loader = ListProperty.RemoteLoader(query => delayedRemotePage(query)),
          |  initialQuery = PersonQuery(),
          |  sortUpdater = Some((query, sorting) =>
          |    query.copy(sort = sorting.map(_.asQueryValue), offset = 0)
          |  ),
          |  rangeQueryUpdater = Some((query, start, visibleCount) =>
          |    query.copy(offset = start, size = math.max(query.size, visibleCount))
          |  )
          |)""".stripMargin,
      patterns = Vector(
        "Backend Simulation" -> "Model real loading behaviour early so the UI is already ready for an API later.",
        "Shared Query State" -> "Keep filter and sorting in the query object instead of scattering it across widgets.",
        "Composable Consumption" -> "Feed the same property into a table, list or custom overview surface."
      ),
      apiPoints = Vector(
        "ListProperty.remote" -> "Create a remote-aware list property.",
        "reload(...)" -> "Refetch using the current or updated query.",
        "sortingProperty" -> "Observe active sort state.",
        "totalCountProperty" -> "Expose total result count when available."
      )
    ),
    DocEntry(
      slug = "form",
      category = "Forms",
      name = "Form",
      packageName = "jfx.form.Form",
      tagline = "Typed form composition around a model instance.",
      summary = "Forms bind controls to model properties and keep validation, submission and nested composition in one place.",
      bullets = Vector(
        "Works well with scoped model injection.",
        "Nested subforms can bind to object properties without extra ceremony.",
        "A good fit for admin forms, editors and detail pages."
      ),
      importCode =
        """import jfx.dsl.Scope.{inject, scoped}
          |import jfx.form.Form.form
          |import jfx.json.JsonMapper""".stripMargin,
      usageCode =
        """scope {
          |  singleton[JsonMapper] {
          |    new JsonMapper()
          |  }
          |
          |  scoped[Person] {
          |    inject[JsonMapper].deserialize[Person](JSON.parse(json))
          |  }
          |
          |  form(inject[Person]) {
          |    onSubmit = _ => println(JSON.stringify(inject[JsonMapper].serialize(inject[Person])))
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Scoped Model Setup" -> "Create the working model once per page or dialog and inject it where controls need it.",
        "Nested Editing" -> "Compose child objects as subforms so submission still produces one coherent model.",
        "Incremental Richness" -> "Start with basic inputs and add pickers, croppers and selectors without changing the form shell."
      ),
      apiPoints = Vector(
        "form(model) { ... }" -> "Create a typed form for a model instance.",
        "onSubmit" -> "Handle form submission inside the DSL.",
        "valueProperty" -> "Observe the active model bound to the form.",
        "SubForm" -> "Bind nested objects into the same editing flow."
      )
    ),
    DocEntry(
      slug = "input-container",
      category = "Forms",
      name = "InputContainer",
      packageName = "jfx.form.InputContainer",
      tagline = "A layout wrapper that gives controls labels, states and error presentation.",
      summary = "Input containers keep form rows visually consistent while reacting to control state such as focus, invalid state and emptiness.",
      bullets = Vector(
        "Pairs especially well with text inputs and custom controls.",
        "Automatically reflects control error and interaction states.",
        "Keeps larger forms readable without repeating boilerplate."
      ),
      importCode =
        """import jfx.form.Input.input
          |import jfx.form.InputContainer.inputContainer
          |import jfx.form.ComboBox.{comboBox, items}
          |import jfx.core.state.ListProperty""".stripMargin,
      usageCode =
        """val teamItems = ListProperty(js.Array("Platform Engineering", "Design Systems", "Field Research"))
          |
          |div {
          |  inputContainer("First Name") {
          |    input("firstName")
          |  }
          |
          |  inputContainer("Team") {
          |    comboBox[String]("team") {
          |      items = teamItems
          |    }
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Consistent Rows" -> "Wrap every control in the same container to keep spacing, labels and validation behaviour aligned.",
        "Mixed Control Types" -> "Use the same container around text fields, combo boxes and richer editors.",
        "State-Aware Styling" -> "Let the container react to focus and error state instead of wiring those visuals by hand."
      ),
      apiPoints = Vector(
        "inputContainer(label) { ... }" -> "Wrap exactly one nested control.",
        "placeholder" -> "Defines the visible field label.",
        "control binding" -> "Automatically observes dirty, focus and error state.",
        "slotHost" -> "Used internally to mount the child control content."
      )
    ),
    DocEntry(
      slug = "combo-box",
      category = "Forms",
      name = "ComboBox",
      packageName = "jfx.form.ComboBox",
      tagline = "A customizable selection control with renderable values and dropdown rows.",
      summary = "The combo box API supports custom item rendering, custom selected-value rendering and overlay-based dropdown behaviour.",
      bullets = Vector(
        "Can be styled as a product-grade picker instead of a default select.",
        "Selected value and row rendering are both customizable.",
        "Plays well with the viewport and overlay system."
      ),
      importCode =
        """import jfx.core.state.ListProperty
          |import jfx.form.ComboBox.{comboBox, items, itemRenderer, valueRenderer}""".stripMargin,
      usageCode =
        """val teams = ListProperty(js.Array("Platform Engineering", "Design Systems", "Field Research"))
          |
          |comboBox[String]("team") {
          |  items = teams
          |  dropdownHeightPx = 272.0
          |  rowHeightPx = 68.0
          |
          |  itemRenderer = { ...custom row layout... }
          |  valueRenderer = { ...selected value layout... }
          |}""".stripMargin,
      patterns = Vector(
        "Custom Brand Feel" -> "Render both the selected value and the dropdown rows so the control matches the rest of the product.",
        "Overlay Based" -> "Use it when the picker should float cleanly over nearby layout instead of expanding inline.",
        "Typed Selection" -> "Keep the selected model type explicit so form binding remains predictable."
      ),
      apiPoints = Vector(
        "comboBox[T](name) { ... }" -> "Create a typed selection field.",
        "items" -> "Bind selectable items via ListProperty.",
        "itemRenderer" -> "Customize dropdown row content.",
        "valueRenderer" -> "Customize the selected value presentation."
      )
    ),
    DocEntry(
      slug = "image-cropper",
      category = "Forms",
      name = "ImageCropper",
      packageName = "jfx.form.ImageCropper",
      tagline = "Media editing inside a typed form workflow.",
      summary = "The image cropper is meant for workflows where choosing and shaping media should stay inside the same model-editing surface.",
      bullets = Vector(
        "Works on Media-backed model properties.",
        "Supports window-based editing flows.",
        "Pairs naturally with thumbnail generation and preview UIs."
      ),
      importCode =
        """import jfx.form.ImageCropper.imageCropper""".stripMargin,
      usageCode =
        """inputContainer("Avatar") {
          |  imageCropper("media") {
          |    placeholder = "Noch kein Bild ausgewaehlt"
          |    windowTitle = "Bild zuschneiden"
          |    aspectRatio = 1.0
          |    outputMaxWidth = 512
          |    outputMaxHeight = 512
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Profile And CMS Flows" -> "Use it when upload, crop and preview should all stay inside one form experience.",
        "Window Editing" -> "Launch the crop step in a focused window so the main form remains uncluttered.",
        "Controlled Output" -> "Limit output size up front so downstream thumbnails and payload sizes stay predictable."
      ),
      apiPoints = Vector(
        "imageCropper(name) { ... }" -> "Bind the cropper to a media property.",
        "aspectRatio" -> "Control the crop frame ratio.",
        "outputMaxWidth / outputMaxHeight" -> "Limit generated output size.",
        "windowTitle" -> "Label the cropper editing window."
      )
    ),
    DocEntry(
      slug = "viewport",
      category = "Layout",
      name = "Viewport",
      packageName = "jfx.layout.Viewport",
      tagline = "A shared layer for windows, notifications and overlays.",
      summary = "Viewport lets the app keep richer interaction layers inside the same runtime and visual shell.",
      bullets = Vector(
        "Supports desktop-style windows without leaving the page app.",
        "Handles overlays and toast-like notifications from the same API family.",
        "Useful for docs, admin tools, editors and inspection workflows."
      ),
      importCode =
        """import jfx.layout.Viewport
          |import jfx.layout.Viewport.{WindowConf, viewport}
          |import jfx.action.Button.button""".stripMargin,
      usageCode =
        """viewport {
          |  mount(inject[Router])
          |}
          |
          |button("Open Inspector") {
          |  onClick { _ =>
          |    Viewport.addWindow(
          |      WindowConf(
          |        title = "Profile Editor",
          |        width = 480,
          |        height = 280,
          |        component = Viewport.captureComponent { ... }
          |      )
          |    )
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "App-Level Host" -> "Mount the viewport once near the shell so every page can open notifications and windows.",
        "Floating Tools" -> "Use windows for secondary workflows like inspectors, editors or previews.",
        "Reused DSL Content" -> "Capture normal DSL components into a window instead of creating a separate rendering path."
      ),
      apiPoints = Vector(
        "viewport { ... }" -> "Mount the shared viewport layer in the shell.",
        "WindowConf" -> "Describe floating windows declaratively.",
        "captureComponent" -> "Reuse DSL content inside a viewport-managed window.",
        "notify(...)" -> "Show lightweight notifications."
      )
    )
  )

  def find(slug: String): Option[DocEntry] =
    entries.find(_.slug == slug)
}
