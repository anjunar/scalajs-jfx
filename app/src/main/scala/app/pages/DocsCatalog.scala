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
          |    placeholder = "Noch kein Bild ausgewählt"
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
      slug = "editor",
      category = "Forms",
      name = "Editor",
      packageName = "jfx.form.Editor",
      tagline = "Rich content editing inside the same form runtime.",
      summary = "The editor keeps structured writing, toolbar state and plugin modules inside one composable control.",
      bullets = Vector(
        "Built on Lexical, but wrapped in the same DSL as the rest of the app.",
        "Supports toolbar modules for headings, lists, links and additional plugins.",
        "A strong fit for product copy, CMS content and internal docs editors."
      ),
      importCode =
        """import jfx.form.Editor.editor
          |import jfx.form.editor.plugins.{basePlugin, codePlugin, headingPlugin, imagePlugin, linkPlugin, listPlugin, tablePlugin}""".stripMargin,
      usageCode =
        """editor("article") {
          |  basePlugin()
          |  headingPlugin()
          |  listPlugin()
          |  linkPlugin()
          |  imagePlugin()
          |  tablePlugin()
          |  codePlugin()
          |}""".stripMargin,
      patterns = Vector(
        "Content Authoring" -> "Use it when the user needs a structured writing surface rather than a plain textarea.",
        "Form Adjacent" -> "Keep the editor inside a larger model-editing flow without switching to another framework.",
        "Extensible Tooling" -> "Add plugins as the product grows without rewriting the editor shell."
      ),
      apiPoints = Vector(
        "editor(name) { ... }" -> "Create a rich editor control.",
        "basePlugin()" -> "Add core formatting controls like undo, redo, bold and italic.",
        "headingPlugin()" -> "Enable heading levels in the editor.",
        "listPlugin()" -> "Add bullet and numbered list support.",
        "tablePlugin()" -> "Add insert/remove table controls and table node support.",
        "codePlugin()" -> "Add a code block editor backed by CodeMirror.",
        "imagePlugin()" -> "Add the image insertion dialog and image node support."
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
    ),
    DocEntry(
      slug = "button",
      category = "Action",
      name = "Button",
      packageName = "jfx.action.Button",
      tagline = "A standard clickable button for triggering actions and form submissions.",
      summary = "Buttons are the primary way for users to interact with your application logic or submit forms.",
      bullets = Vector(
        "Automatically switches type to 'submit' inside forms.",
        "Supports custom click listeners via the onClick DSL.",
        "Easily styled with custom CSS classes."
      ),
      importCode =
        """import jfx.action.Button.{button, onClick}""".stripMargin,
      usageCode =
        """button("Save Changes") {
          |  onClick { _ =>
          |    println("Saving data...")
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Form Submission" -> "Buttons inside a form default to 'submit' type, triggering the form's onSubmit handler.",
        "Icon Buttons" -> "Combine buttons with icon components or custom CSS for compact action triggers.",
        "Scoped Actions" -> "Use buttons to trigger scope-aware actions like opening windows or navigating."
      ),
      apiPoints = Vector(
        "button(label) { ... }" -> "Create a button with a text label.",
        "onClick { event => ... }" -> "Register a click listener.",
        "buttonType" -> "Get or set the underlying HTML button type.",
        "disabled" -> "Control the button's interactive state."
      )
    ),
    DocEntry(
      slug = "link",
      category = "Action",
      name = "Link",
      packageName = "jfx.control.Link",
      tagline = "Client-side navigation links with history support.",
      summary = "Links provide a way to navigate between pages without full browser reloads, integrating with the browser history.",
      bullets = Vector(
        "Prevents default browser navigation for client-side routing.",
        "Supports custom click handlers for additional logic.",
        "Consistent with standard HTML anchor tags for accessibility."
      ),
      importCode =
        """import jfx.control.Link.link""".stripMargin,
      usageCode =
        """link("/docs/router") {
          |  textContent = "Go to Router Docs"
          |}""".stripMargin,
      patterns = Vector(
        "Internal Navigation" -> "Use links for moving between application routes handled by the Router.",
        "Breadcrumbs" -> "Compose links in a horizontal row for clear navigation paths.",
        "Action Links" -> "Use links for lightweight actions that feel more like navigation than primary triggers."
      ),
      apiPoints = Vector(
        "link(href) { ... }" -> "Create a navigation link.",
        "href" -> "Set the target URL for the link.",
        "onClick { event => ... }" -> "Add custom logic to the link click."
      )
    ),
    DocEntry(
      slug = "heading",
      category = "Control",
      name = "Heading",
      packageName = "jfx.control.Heading",
      tagline = "Semantic text headings from H1 to H6.",
      summary = "Headings help structure your content and provide visual hierarchy to your pages.",
      bullets = Vector(
        "Supports levels 1 through 6.",
        "Normalizes out-of-bounds levels to valid HTML heading tags.",
        "Promotes semantic HTML and better accessibility."
      ),
      importCode =
        """import jfx.control.Heading.heading""".stripMargin,
      usageCode =
        """heading(2) {
          |  textContent = "Component Reference"
          |}""".stripMargin,
      patterns = Vector(
        "Page Titles" -> "Use Heading(1) for the main title of a page.",
        "Section Hierarchy" -> "Nest heading levels to reflect the structural depth of your content.",
        "Typography" -> "Apply custom styles to headings to match your brand's visual language."
      ),
      apiPoints = Vector(
        "heading(level) { ... }" -> "Create a heading of a specific level (1-6).",
        "headingLevel" -> "Access the normalized level of the heading."
      )
    ),
    DocEntry(
      slug = "box-layout",
      category = "Layout",
      name = "Box (HBox / VBox)",
      packageName = "jfx.layout.{HBox, VBox}",
      tagline = "Fundamental horizontal and vertical flex-based containers.",
      summary = "HBox and VBox provide a simple way to align children in a row or column using CSS flexbox.",
      bullets = Vector(
        "HBox for horizontal alignment.",
        "VBox for vertical alignment.",
        "Nesting boxes allows for complex grid-like layouts without raw CSS."
      ),
      importCode =
        """import jfx.layout.HBox.hbox
          |import jfx.layout.VBox.vbox""".stripMargin,
      usageCode =
        """vbox {
          |  hbox {
          |    button("Left")
          |    button("Right")
          |  }
          |  vbox {
          |    textContent = "Content below"
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Toolbars" -> "Use HBox to align buttons and controls in a single row.",
        "Form Stacks" -> "Use VBox to stack input containers vertically.",
        "Responsive Flow" -> "Combine with CSS media queries for adaptive layouts."
      ),
      apiPoints = Vector(
        "hbox { ... }" -> "Create a horizontal container.",
        "vbox { ... }" -> "Create a vertical container."
      )
    ),
    DocEntry(
      slug = "drawer",
      category = "Layout",
      name = "Drawer",
      packageName = "jfx.layout.Drawer",
      tagline = "A sliding side panel for navigation and supplemental content.",
      summary = "Drawers slide in from the edge of the screen, providing space for navigation menus or detail views without losing context.",
      bullets = Vector(
        "Supports both 'Start' (Left) and 'End' (Right) sides.",
        "Includes a built-in scrim with click-to-close support.",
        "Separates navigation and content hosts for clean layouts."
      ),
      importCode =
        """import jfx.layout.Drawer.*""".stripMargin,
      usageCode =
        """drawer {
          |  drawerSide = Drawer.Side.Start
          |  drawerWidth = "320px"
          |
          |  drawerNavigation {
          |    vbox {
          |      link("/") { textContent = "Home" }
          |      link("/docs") { textContent = "Docs" }
          |    }
          |  }
          |
          |  drawerContent {
          |    viewport {
          |      mount(inject[Router])
          |    }
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Mobile Navigation" -> "Hide the main menu in a drawer on smaller screens.",
        "Settings Panel" -> "Open a drawer from the right side for supplemental configuration.",
        "Persistent Shell" -> "Keep the drawer at the root of your application layout for global access."
      ),
      apiPoints = Vector(
        "drawer { ... }" -> "Create a drawer component.",
        "drawerNavigation { ... }" -> "Define the content of the sliding panel.",
        "drawerContent { ... }" -> "Define the main content area.",
        "drawerOpen = true / false" -> "Programmatically control the drawer state.",
        "drawerSide" -> "Choose between Start and End sides."
      )
    ),
    DocEntry(
      slug = "virtual-list-view",
      category = "Data",
      name = "VirtualListView",
      packageName = "jfx.control.VirtualListView",
      tagline = "High-performance list rendering for large datasets using virtualization.",
      summary = "VirtualListView only renders the items currently visible in the viewport, allowing it to handle thousands of rows with ease.",
      bullets = Vector(
        "Supports custom row renderers with full DSL access.",
        "Works with both local and remote list properties.",
        "Efficiently measures and caches row heights for smooth scrolling."
      ),
      importCode =
        """import jfx.control.VirtualListView.virtualList
          |import jfx.core.state.ListProperty""".stripMargin,
      usageCode =
        """val items = ListProperty(js.Array((1 to 10000).toSeq*))
          |
          |virtualList(items) { (item, index) =>
          |  div {
          |    textContent = s"Item $index: $item"
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Large Data Sets" -> "Use virtualization when you have more than 50-100 items to keep the DOM lean.",
        "Variable Heights" -> "The list automatically adjusts to rows of different heights.",
        "Remote Loading" -> "Integrate with RemoteListProperty for seamless infinite scrolling."
      ),
      apiPoints = Vector(
        "virtualList(items) { ... }" -> "Create a virtualized list.",
        "estimateHeightPx" -> "Help the list calculate initial scroll space.",
        "scrollTo(index)" -> "Programmatically scroll to a specific item.",
        "refresh()" -> "Force a re-render of the visible items."
      )
    ),
    DocEntry(
      slug = "conditional",
      category = "Logic",
      name = "Conditional (when)",
      packageName = "jfx.statement.Conditional",
      tagline = "Reactive branching in the DSL based on a boolean property.",
      summary = "Use when you need to show or hide parts of the UI based on state changes.",
      bullets = Vector(
        "Supports 'thenDo' and 'elseDo' branches.",
        "Efficiently mounts and unmounts DOM nodes as the condition changes.",
        "Integrates with the DSL scope and form system."
      ),
      importCode =
        """import jfx.statement.Conditional.{conditional, thenDo, elseDo}""".stripMargin,
      usageCode =
        """val showDetails = Property(false)
          |
          |conditional(showDetails) {
          |  thenDo {
          |    div { textContent = "Detailed information here..." }
          |  }
          |  elseDo {
          |    div { textContent = "Click to show details." }
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Feature Toggles" -> "Use conditional to hide features that are not yet enabled for the user.",
        "Loading States" -> "Show a spinner while data is loading and the content once it's ready.",
        "Empty States" -> "Display an empty state message when a list has no items."
      ),
      apiPoints = Vector(
        "conditional(property) { ... }" -> "Start a conditional block.",
        "thenDo { ... }" -> "Define the content when the property is true.",
        "elseDo { ... }" -> "Define the content when the property is false."
      )
    ),
    DocEntry(
      slug = "foreach",
      category = "Logic",
      name = "ForEach",
      packageName = "jfx.statement.ForEach",
      tagline = "Efficiently render a list of items from a ListProperty.",
      summary = "ForEach observes a ListProperty and incrementally updates the DOM as the list changes (add, remove, move).",
      bullets = Vector(
        "Reuses existing DOM nodes where possible.",
        "Handles range updates and clear operations efficiently.",
        "Passes the item and index to the renderer block."
      ),
      importCode =
        """import jfx.statement.ForEach.forEach
          |import jfx.core.state.ListProperty""".stripMargin,
      usageCode =
        """val names = ListProperty(js.Array("Alice", "Bob", "Charlie"))
          |
          |forEach(names) { name =>
          |  div {
          |    textContent = name
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Dynamic Lists" -> "Perfect for todo lists, chat messages or any collection that changes over time.",
        "Nested Lists" -> "ForEach can be nested to render hierarchical data structures.",
        "Filtered Views" -> "Observe a filtered list property to automatically update the UI on filter changes."
      ),
      apiPoints = Vector(
        "forEach(items) { item => ... }" -> "Render items from a list property.",
        "forEach(items) { (item, index) => ... }" -> "Render items with access to their index."
      )
    ),
    DocEntry(
      slug = "observe-render",
      category = "Logic",
      name = "ObserveRender",
      packageName = "jfx.statement.ObserveRender",
      tagline = "Reactive component rebuilding based on a property value.",
      summary = "ObserveRender re-executes its inner DSL block whenever the source property changes, replacing its entire content.",
      bullets = Vector(
        "Ideal for components that need to be fully recreated on state change.",
        "Captures DSL components into its own reactive lifecycle.",
        "Simple alternative to complex state management for small UI sections."
      ),
      importCode =
        """import jfx.statement.ObserveRender.observeRender""".stripMargin,
      usageCode =
        """val selectedId = Property(1)
          |
          |observeRender(selectedId) { id =>
          |  div {
          |    textContent = s"Currently viewing item: $id"
          |    // ... complex UI that depends on id ...
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Detail Views" -> "Rebuild the entire detail pane when the selected item changes.",
        "Configuration UI" -> "Change the available settings based on a selected mode.",
        "Reactive Templates" -> "Use it when simple property binding is not enough for the required UI changes."
      ),
      apiPoints = Vector(
        "observeRender(property) { value => ... }" -> "Start a reactive render block."
      )
    ),
    DocEntry(
      slug = "dynamic-outlet",
      category = "Logic",
      name = "DynamicOutlet (mount)",
      packageName = "jfx.statement.DynamicOutlet",
      tagline = "A mount point for dynamic component instances.",
      summary = "DynamicOutlet allows you to swap out entire component instances at a specific point in the layout.",
      bullets = Vector(
        "Used by the Router to mount page components.",
        "Supports null values for empty mount points.",
        "Handles parent-child lifecycle registration automatically."
      ),
      importCode =
        """import jfx.statement.DynamicOutlet.outlet""".stripMargin,
      usageCode =
        """val activePage = Property[NodeComponent[? <: Node] | Null](null)
          |
          |div {
          |  outlet(activePage)
          |}""".stripMargin,
      patterns = Vector(
        "Page Navigation" -> "The foundation of client-side routing and page transitions.",
        "Modular UIs" -> "Load and mount different tool panels or widgets dynamically.",
        "Placeholder Management" -> "Swap between a loading placeholder and the actual content component."
      ),
      apiPoints = Vector(
        "outlet(property)" -> "Define a mount point for a component property."
      )
    ),
    DocEntry(
      slug = "scope",
      category = "Scope",
      name = "Scope",
      packageName = "jfx.dsl.Scope",
      tagline = "Hierarchical dependency injection and lifecycle management.",
      summary = "Scopes allow you to register and inject services or model instances throughout a component tree.",
      bullets = Vector(
        "Supports Singleton, Scoped and Transient lifetimes.",
        "Prevents circular dependencies with explicit resolution checks.",
        "Fits naturally into the DSL with given-based context propagation."
      ),
      importCode =
        """import jfx.dsl.Scope.{scope, singleton, inject}""".stripMargin,
      usageCode =
        """scope {
          |  singleton[MyService] { new MyService() }
          |
          |  div {
          |    val service = inject[MyService]
          |    // ... use service ...
          |  }
          |}""".stripMargin,
      patterns = Vector(
        "Service Locator" -> "Provide global services like API clients or event buses at the root scope.",
        "Model Injection" -> "Pass domain models down to nested forms and controls without manual drilling.",
        "Resource Lifecycle" -> "Link the lifetime of a service to the scope of a specific page or dialog."
      ),
      apiPoints = Vector(
        "scope { ... }" -> "Create a new nested scope.",
        "singleton[T] { ... }" -> "Register a singleton service.",
        "scoped[T] { ... }" -> "Register a service that is unique to each scope instance.",
        "inject[T]" -> "Retrieve a registered service from the current or parent scope."
      )
    ),
    DocEntry(
      slug = "mounting",
      category = "Scope",
      name = "Mounting",
      packageName = "jfx.core.component.NodeComponent.mount",
      tagline = "Attach pre-existing component instances to the current DSL context.",
      summary = "Use mount when you have a component instance (e.g. from injection) and want to place it in the current layout.",
      bullets = Vector(
        "Connects the component to the parent-child lifecycle.",
        "Ensures proper DOM attachment and mounting hooks.",
        "Works with any NodeComponent implementation."
      ),
      importCode =
        """import jfx.core.component.NodeComponent.mount
          |import jfx.dsl.Scope.inject""".stripMargin,
      usageCode =
        """viewport {
          |  val router = inject[Router]
          |  mount(router)
          |}""".stripMargin,
      patterns = Vector(
        "Injected Components" -> "Mount routers, controllers or shared widgets that were created outside the local DSL block.",
        "Late Binding" -> "Decide which component to mount based on runtime state or permissions.",
        "Component Reuse" -> "Keep a component instance alive across different mount points."
      ),
      apiPoints = Vector(
        "mount(component)" -> "Attach a component instance to the current DSL context."
      )
    ),
    DocEntry(
      slug = "image",
      category = "Control",
      name = "Image",
      packageName = "jfx.control.Image",
      tagline = "Display images with reactive source and styling.",
      summary = "Image provides a way to render images while observing source changes and providing easy styling hooks.",
      bullets = Vector(
        "Supports reactive source (URL) property.",
        "Easy to style with CSS for size, object-fit and borders.",
        "Standard HTML img element under the hood for maximum compatibility."
      ),
      importCode =
        """import jfx.control.Image.image""".stripMargin,
      usageCode =
        """val avatarUrl = Property("https://example.com/avatar.png")
          |
          |image(avatarUrl) {
          |  style = "width: 48px; height: 48px; border-radius: 50%; object-fit: cover;"
          |}""".stripMargin,
      patterns = Vector(
        "User Avatars" -> "Bind an image to a user's profile picture URL.",
        "Product Galleries" -> "Use images within a ForEach block to display a list of products.",
        "Responsive Images" -> "Apply CSS classes to make images scale with their container."
      ),
      apiPoints = Vector(
        "image(srcProperty) { ... }" -> "Create an image bound to a property.",
        "srcProperty" -> "The underlying property holding the image URL."
      )
    ),
    DocEntry(
      slug = "window",
      category = "Layout",
      name = "Window",
      packageName = "jfx.layout.Window",
      tagline = "Floating, draggable windows for secondary workflows.",
      summary = "Windows provide a desktop-like experience within the browser, allowing for multi-tasking and non-blocking workflows.",
      bullets = Vector(
        "Draggable and resizable by the user.",
        "Supports title bars with status indicators and close buttons.",
        "Managed by the Viewport for proper layering and focus handling."
      ),
      importCode =
        """import jfx.layout.Viewport.WindowConf
          |import jfx.layout.Viewport""".stripMargin,
      usageCode =
        """Viewport.addWindow(
          |  WindowConf(
          |    title = "Edit Profile",
          |    width = 400,
          |    height = 300,
          |    component = Viewport.captureComponent {
          |      vbox {
          |        textContent = "Profile Editor Content"
          |      }
          |    }
          |  )
          |)""".stripMargin,
      patterns = Vector(
        "Multi-tasking" -> "Allow users to keep an inspector or chat window open while they work.",
        "Detail Overlays" -> "Open a window to show details about an item from a table or list.",
        "Form Wizards" -> "Guide users through multi-step processes in a focused floating window."
      ),
      apiPoints = Vector(
        "Viewport.addWindow(conf)" -> "Launch a new window in the shared viewport.",
        "WindowConf" -> "Configure title, initial size and content.",
        "Viewport.captureComponent { ... }" -> "Wrap DSL content for window usage."
      )
    ),
    DocEntry(
      slug = "div-span",
      category = "Layout",
      name = "Div / Span",
      packageName = "jfx.layout.{Div, Span}",
      tagline = "Basic HTML block and inline containers.",
      summary = "Div and Span are the fundamental building blocks for custom layouts and text styling.",
      bullets = Vector(
        "Div for block-level containers and layout sections.",
        "Span for inline text styling and small ornaments.",
        "Both provide full access to CSS styling and DSL children."
      ),
      importCode =
        """import jfx.layout.Div.div
          |import jfx.layout.Span.span""".stripMargin,
      usageCode =
        """div {
          |  style = "padding: 16px; border: 1px solid #ccc;"
          |  span {
          |    style = "font-weight: bold; color: blue;"
          |    textContent = "Important:"
          |  }
          |  textContent = " This is a message inside a div."
          |}""".stripMargin,
      patterns = Vector(
        "Custom Containers" -> "Use div to create unique layout structures not covered by HBox/VBox.",
        "Rich Text" -> "Combine spans with different styles to create complex text presentations.",
        "Wrappers" -> "Wrap components in a div to apply specific positioning or spacing."
      ),
      apiPoints = Vector(
        "div { ... }" -> "Create a block-level container.",
        "span { ... }" -> "Create an inline-level container."
      )
    )
  )

  def find(slug: String): Option[DocEntry] =
    entries.find(_.slug == slug)
}
