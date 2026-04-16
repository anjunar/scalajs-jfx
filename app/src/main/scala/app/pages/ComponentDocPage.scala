package app.pages
import app.domain.{Address, Email, Person}
import jfx.action.Button.{button, onClick}
import jfx.control.TableColumn
import jfx.control.TableColumn.column
import jfx.control.TableView
import jfx.control.TableView.tableView
import jfx.control.cell.PropertyValueFactory
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.core.state.ListProperty
import jfx.core.state.Property
import jfx.dsl.*
import jfx.dsl.Scope.inject
import jfx.form.ComboBox.{comboBox, comboItem, comboItemSelected, comboRenderedSelectedItem, items, itemRenderer, valueRenderer}
import jfx.form.ImageCropper.*
import jfx.form.Input.input
import jfx.form.InputContainer.inputContainer
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.layout.Span.span
import jfx.layout.Viewport
import jfx.layout.Viewport.WindowConf
import jfx.router.Router
import org.scalajs.dom.HTMLDivElement

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.setTimeout
import scala.util.control.NonFatal

class ComponentDocPage(entry: DocEntry) extends CompositeComponent[HTMLDivElement] {

  private final case class DocQuery(filter: String = "", sort: Seq[String] = Seq.empty, offset: Int = 0, size: Int = 3)

  override val element: HTMLDivElement = newElement("div")
  private given ExecutionContext = ExecutionContext.global

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given ComponentDocPage = this

      classes = "clarity-page component-doc"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "20px"
        maxWidth = "1080px"
        margin = "0 auto"
      }

      div {
        classes = "clarity-hero clarity-hero--reference"

        div {
          classes = "clarity-hero__eyebrow"
          text = entry.category
        }

        div {
          classes = "docs-hero__package"
          text = entry.packageName
        }

        div {
          classes = "docs-hero__title"
          text = entry.name
        }

        div {
          classes = "clarity-hero__copy"
          text = entry.tagline + " " + entry.summary
        }

        hbox {
          classes = "docs-hero__actions"

          button("Back To Docs") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              inject[Router].navigate("/docs")
            }
          }

          button("Open Live Workspace") {
            classes = Seq("calm-action", "calm-action--quiet")
            onClick { _ =>
              inject[Router].navigate(liveWorkspacePath(entry.slug))
            }
          }

        }
      }

      div {
        classes = "component-doc__grid"

        div {
          classes = "component-doc__panel"

          div {
            classes = "component-doc__panel-title"
            text = "Operational Fit"
          }

          entry.bullets.foreach { bullet =>
            div {
              classes = "component-doc__bullet"
              text = bullet
            }
          }
        }

        div {
          classes = "component-doc__panel"

          div {
            classes = "component-doc__panel-title"
            text = "API Snapshot"
          }

          entry.apiPoints.foreach { case (name, description) =>
            div {
              classes = "component-doc__api-row"

              div {
                classes = "component-doc__api-name"
                text = name
              }

              div {
                classes = "component-doc__api-copy"
                text = description
              }
            }
          }
        }
      }

      div {
        classes = "component-doc__panel"

        div {
          classes = "component-doc__panel-title"
          text = "Import Surface"
        }

        div {
          classes = "component-doc__code"
          text = entry.importCode
        }
      }

      div {
        classes = "component-doc__panel"

        div {
          classes = "component-doc__panel-title"
          text = "Usage Skeleton"
        }

        div {
          classes = "component-doc__code"
          text = entry.usageCode
        }
      }

      div {
        classes = "component-doc__panel"

        div {
          classes = "component-doc__panel-title"
          text = "Integration Patterns"
        }

        div {
          classes = "component-doc__patterns"

          entry.patterns.foreach { case (title, copy) =>
            div {
              classes = "component-doc__pattern"

              div {
                classes = "component-doc__pattern-title"
                text = title
              }

              div {
                classes = "component-doc__pattern-copy"
                text = copy
              }
            }
          }
        }
      }

      div {
        classes = "component-doc__panel"

        div {
          classes = "component-doc__panel-title"
          text = "Live Sandbox"
        }

        div {
          classes = "component-doc__interactive"
          renderInteractiveDemo(entry.slug)
        }
      }
    }

  private def renderInteractiveDemo(slug: String)(using CompositeComponent.DslContext): Unit =
    slug match {
      case "router" =>
        docsPreviewGrid(
          "Single shell",
          "The same router can host overview pages, detail pages and embedded tools without leaving the DSL.",
          "Typed route context",
          "Route factories receive path and query state in the same component model as the rest of the app.",
          "Docs-ready navigation",
          "This docs section is itself a working example of nested routes and reusable page components."
        )
        docsActionRow(
          "Navigate between documentation pages with the same router instance that powers the docs shell."
        )(
          "Docs Index" -> (() => inject[Router].navigate("/docs")),
          "TableView Docs" -> (() => inject[Router].navigate("/docs/table-view")),
          "Form Docs" -> (() => inject[Router].navigate("/docs/form")),
          "Viewport Docs" -> (() => inject[Router].navigate("/docs/viewport"))
        )
        div {
          classes = "component-doc__status"
          text = "routerMode=docs-shell | nested-routes=enabled | navigation=programmatic"
        }

      case "table-view" =>
        docsPreviewGrid(
          "Column-driven structure",
          "Table columns stay close to the model type and sorting metadata.",
          "Embedded reference dataset",
          "The docs page renders a small real table so the explanation and behaviour stay together.",
          "Composable with remote data",
          "Pair the same table structure with RemoteListProperty when you want paging and refetch behaviour."
        )
        renderStaticTableDemo()

      case "remote-list-property" =>
        docsPreviewGrid(
          "Loader",
          "A query object drives what data should be fetched next.",
          "State",
          "Loading, errors, sorting and totals live next to the collection itself.",
          "View",
          "Tables and virtual lists can consume the property without custom glue code."
        )
        renderRemoteListDemo()

      case "form" =>
        docsPreviewGrid(
          "Typed model",
          "The form binds directly to a scoped model instance.",
          "Nested editing",
          "Subforms let child objects remain part of the same save flow.",
          "Submission",
          "Submission can serialize the current model or call a backend action."
        )
        docsActionRow("Go deeper into related form controls without leaving the docs section.")(
          "InputContainer Docs" -> (() => inject[Router].navigate("/docs/input-container")),
          "ComboBox Docs" -> (() => inject[Router].navigate("/docs/combo-box"))
        )

      case "input-container" =>
        div {
          classes = "component-doc__hint"
          text = "This embedded example mixes plain text inputs with a richer picker to show how one container keeps the whole form row system visually consistent."
        }

        div {
          classes = "component-doc__mini-form"

          inputContainer("First Name") {
            input("first-name-doc")
          }

          inputContainer("Team") {
            comboBox[String]("team-inline-doc") {
              items = ListProperty(js.Array("Platform Engineering", "Design Systems", "Field Research"))
            }
          }

          inputContainer("City") {
            input("city-doc")
          }
        }

      case "combo-box" =>
        div {
          classes = "component-doc__hint"
          text = "The docs example uses custom row and selected-value rendering so the combo box feels like a product component instead of a default select."
        }

        div {
          classes = "component-doc__mini-form"

          inputContainer("Team") {
            comboBox[String]("team-doc") {
              val currentCombo = summon[jfx.form.ComboBox[String]]
              currentCombo.setItems(ListProperty(js.Array("Platform Engineering", "Design Systems", "Field Research", "Customer Success")))
              currentCombo.dropdownHeightPxProperty.set(272.0)
              currentCombo.rowHeightPxProperty.set(68.0)

              currentCombo.setItemRenderer(jfx.form.ComboBox.ItemRenderer[String] {
                val label: String = comboItem[String]

                div {
                  classes = Vector("component-doc__combo-item") ++ Option.when(comboItemSelected)("is-selected").toSeq

                  div {
                    classes = "component-doc__combo-copy"

                    div {
                      classes = "component-doc__combo-title"
                      text = label
                    }

                    div {
                      classes = "component-doc__combo-subtitle"
                      text = s"Docs example for $label"
                    }
                  }

                  span {
                    classes = "component-doc__combo-pill"
                    text = if (label.startsWith("Platform")) "Core" else "Docs"
                  }
                }
              })

              currentCombo.setValueRenderer(jfx.form.ComboBox.ValueRenderer[String] {
                Option(comboRenderedSelectedItem[String]) match {
                  case Some(selected) =>
                    div {
                      classes = "component-doc__combo-item component-doc__combo-item--value"

                      div {
                        classes = "component-doc__combo-copy"

                        div {
                          classes = "component-doc__combo-title"
                          text = selected
                        }

                        div {
                          classes = "component-doc__combo-subtitle"
                          text = "Selected inside the embedded docs example"
                        }
                      }
                    }

                  case None =>
                    div {
                      classes = "component-doc__combo-item component-doc__combo-item--value"

                      div {
                        classes = "component-doc__combo-copy"

                        div {
                          classes = "component-doc__combo-title"
                          text = "Choose a team"
                        }

                        div {
                          classes = "component-doc__combo-subtitle"
                          text = "Custom selected-value rendering can introduce richer guidance."
                        }
                      }
                    }
                }
              })
            }
          }
        }

      case "image-cropper" =>
        div {
          classes = "component-doc__mini-form"

          imageCropper("media-doc") {
            placeholder = "Choose an image to preview the cropper flow"
            windowTitle = "Crop Demo Asset"
            aspectRatio = 1.0
            outputMaxWidth = 256
            outputMaxHeight = 256
          }
        }

        docsActionRow("Continue with nearby form building blocks inside the docs section.")(
          "Form Docs" -> (() => inject[Router].navigate("/docs/form")),
          "InputContainer Docs" -> (() => inject[Router].navigate("/docs/input-container"))
        )

      case "viewport" =>
        docsPreviewGrid(
          "One shared layer",
          "Notifications and windows come from the same runtime surface instead of ad hoc DOM overlays.",
          "Composable content",
          "The example window is built from the same DSL components as the page itself.",
          "Low-friction tooling",
          "This is a strong fit for inspectors, editors, previews and docs-side utilities."
        )
        docsActionRow("Open runtime layers without leaving the docs page.")(
          "Show Notification" -> (() =>
            Viewport.notify(
              message = "Viewport notifications work from docs pages too.",
              kind = Viewport.NotificationKind.Success,
              durationMs = 2400
            )
          ),
          "Open Sample Window" -> (() =>
            Viewport.addWindow(
              WindowConf(
                title = "Docs Preview Window",
                width = 400,
                height = 220,
                resizable = true,
                component = Viewport.captureComponent {
                  div {
                    classes = "window-demo-card"

                    div {
                      classes = "window-demo-card__title"
                      text = "Viewport Preview"
                    }

                    div {
                      classes = "window-demo-card__copy"
                      text = "This floating window was launched from a component reference page."
                    }

                    div {
                      classes = "window-demo-card__meta"
                      text = "Use this pattern for inspectors, editors and contextual tools."
                    }
                  }
                }
              )
            )
          )
        )
        div {
          classes = "component-doc__status"
          text = "viewportActions=notification | sampleWindow"
        }

      case _ =>
        docsPreviewGrid(
          "Reference first",
          "Use this page as the stable landing spot for usage and API notes.",
          "Embedded example",
          "Keep the example on the docs page so the explanation and behaviour stay together.",
          "Expandable",
          "Each page is ready for more examples, edge cases and API coverage."
        )
    }

  private def docsActionRow(copy: String)(actions: (String, () => Unit)*)(using CompositeComponent.DslContext): Unit = {
    div {
      classes = "component-doc__hint"
      text = copy
    }

    hbox {
      classes = "component-doc__action-row"

      actions.foreach { case (label, run) =>
        button(label) {
          classes = Seq("calm-action", "calm-action--quiet")
          onClick { _ =>
            run()
          }
        }
      }
    }
  }

  private def docsPreviewGrid(
    titleOne: String,
    bodyOne: String,
    titleTwo: String,
    bodyTwo: String,
    titleThree: String,
    bodyThree: String
  ): Unit =
    div {
      classes = "component-doc__preview-grid"

      previewCard(titleOne, bodyOne)
      previewCard(titleTwo, bodyTwo)
      previewCard(titleThree, bodyThree)
    }

  private def previewCard(title: String, body: String): Unit =
    div {
      classes = "component-doc__preview-card"

      div {
        classes = "component-doc__preview-title"
        text = title
      }

      div {
        classes = "component-doc__preview-copy"
        text = body
      }
    }

  private def renderStaticTableDemo(): Unit = {
    val people = ListProperty(js.Array(samplePeople().take(4)*))

    div {
      classes = "component-doc__mini-table"

      tableView[Person] {
        val table = summon[TableView[Person]]
        table.items = people
        table.setFixedCellSize(34)

        style {
          height = "220px"
        }

        column[Person, String]("First Name") {
          val currentColumn = summon[TableColumn[Person, String]]
          currentColumn.setCellValueFactory(new PropertyValueFactory[Person, String]("firstName"))
          currentColumn.prefWidth = 160
        }

        column[Person, String]("Last Name") {
          val currentColumn = summon[TableColumn[Person, String]]
          currentColumn.setCellValueFactory(new PropertyValueFactory[Person, String]("lastName"))
          currentColumn.prefWidth = 160
        }

        column[Person, String]("City") {
          val currentColumn = summon[TableColumn[Person, String]]
          currentColumn.setCellValueFactory(features => {
            val address = features.getValue.address.get
            if (address == null) Property("")
            else address.city
          })
          currentColumn.prefWidth = 160
        }
      }
    }
  }

  private def renderRemoteListDemo(): Unit = {
    val remotePeople = ListProperty.remote[Person, DocQuery](
      loader = ListProperty.RemoteLoader(query => delayedDocRemotePage(query)),
      initialQuery = DocQuery(),
      sortUpdater = Some((query, sorting) =>
        query.copy(sort = sorting.map(_.asQueryValue), offset = 0)
      ),
      rangeQueryUpdater = Some((query, start, visibleCount) =>
        query.copy(offset = start, size = math.max(query.size, visibleCount))
      )
    )

    var statusNode: jfx.layout.Div | Null = null

    def updateStatus(): Unit = {
      val query = remotePeople.query
      val sortText =
        if (remotePeople.getSorting.isEmpty) "none"
        else remotePeople.getSorting.map(_.asQueryValue).mkString(", ")
      val total = remotePeople.totalCountProperty.get.map(_.toString).getOrElse("?")
      Option(statusNode).foreach(_.textContent = s"loaded=${remotePeople.length} total=$total sort=$sortText offset=${query.offset} size=${query.size}")
    }

    hbox {
      classes = "component-doc__action-row"

      button("Reload") {
        classes = Seq("calm-action", "calm-action--quiet")
        onClick { _ =>
          discard(remotePeople.reload())
        }
      }

      button("Sort By City") {
        classes = Seq("calm-action", "calm-action--quiet")
        onClick { _ =>
          discard(remotePeople.applySorting(Seq(ListProperty.RemoteSort("city", ascending = true))))
        }
      }

      button("Load More") {
        classes = Seq("calm-action", "calm-action--quiet")
        onClick { _ =>
          discard(remotePeople.loadMore())
        }
      }
    }

    statusNode = div {
      classes = "component-doc__status"
      text = "loaded=0 total=? sort=none offset=0 size=3"
    }

    div {
      classes = "component-doc__mini-table"

      tableView[Person] {
        val table = summon[TableView[Person]]
        table.items = remotePeople
        table.setFixedCellSize(34)

        style {
          height = "220px"
        }

        column[Person, String]("First Name") {
          val currentColumn = summon[TableColumn[Person, String]]
          currentColumn.setCellValueFactory(new PropertyValueFactory[Person, String]("firstName"))
          currentColumn.prefWidth = 150
          currentColumn.setSortable(true)
          currentColumn.setSortKey("firstName")
        }

        column[Person, String]("City") {
          val currentColumn = summon[TableColumn[Person, String]]
          currentColumn.setCellValueFactory(features => {
            val address = features.getValue.address.get
            if (address == null) Property("")
            else address.city
          })
          currentColumn.prefWidth = 150
          currentColumn.setSortable(true)
          currentColumn.setSortKey("city")
        }
      }
    }

    addDisposable(remotePeople.observe(_ => updateStatus()))
    addDisposable(remotePeople.loadingProperty.observe(_ => updateStatus()))
    addDisposable(remotePeople.totalCountProperty.observe(_ => updateStatus()))
    addDisposable(remotePeople.sortingProperty.observe(_ => updateStatus()))
  }

  private def delayedDocRemotePage(query: DocQuery): js.Promise[ListProperty.RemotePage[Person, DocQuery]] = {
    val promise = scala.concurrent.Promise[ListProperty.RemotePage[Person, DocQuery]]()

    setTimeout(180) {
      try promise.success(loadDocRemotePage(query))
      catch {
        case NonFatal(error) => promise.failure(error)
      }
    }

    promise.future.toJSPromise
  }

  private def loadDocRemotePage(query: DocQuery): ListProperty.RemotePage[Person, DocQuery] = {
    val sorted = query.sort.headOption match {
      case Some("city,asc")      => samplePeople().sortBy(_.address.get.city.get)
      case Some("city,desc")     => samplePeople().sortBy(_.address.get.city.get).reverse
      case Some("firstName,asc") => samplePeople().sortBy(_.firstName.get)
      case Some("firstName,desc") => samplePeople().sortBy(_.firstName.get).reverse
      case _                     => samplePeople()
    }

    val offset = math.max(0, query.offset)
    val size = math.max(1, query.size)
    val slice = sorted.slice(offset, math.min(sorted.length, offset + size))
    val nextOffset = offset + slice.length

    ListProperty.RemotePage(
      items = slice,
      offset = Some(offset),
      nextQuery = Option.when(nextOffset < sorted.length)(query.copy(offset = nextOffset)),
      totalCount = Some(sorted.length),
      hasMore = Some(nextOffset < sorted.length)
    )
  }

  private def samplePeople(): Vector[Person] =
    Vector(
      samplePerson("Mina", "Hartmann", "Berlin", "Oak Street 11"),
      samplePerson("Jon", "Fischer", "Hamburg", "River Road 8"),
      samplePerson("Lea", "Novak", "Munich", "Hill Avenue 4"),
      samplePerson("Omar", "Santos", "Cologne", "Market Street 18"),
      samplePerson("Iris", "Bauer", "Leipzig", "Sunset Lane 2"),
      samplePerson("Noah", "Klein", "Bremen", "Park Row 15")
    )

  private def samplePerson(firstName: String, lastName: String, city: String, street: String): Person =
    new Person(
      firstName = Property(firstName),
      lastName = Property(lastName),
      address = Property(new Address(Property(street), Property(city))),
      emails = ListProperty(js.Array(new Email(Property(s"${firstName.toLowerCase}.${lastName.toLowerCase}@docs.dev"))))
    )

  private def liveWorkspacePath(slug: String): String =
    slug match {
      case "router" => "/"
      case "table-view" | "remote-list-property" => "/table"
      case "form" | "input-container" | "combo-box" | "image-cropper" => "/form"
      case "viewport" => "/window"
      case _ => "/docs"
    }

  private def discard(promise: js.Promise[?]): Unit = {
    promise.toFuture.recover { case NonFatal(_) => () }
    ()
  }
}

object ComponentDocPage {
  def componentDocPage(entry: DocEntry)(init: ComponentDocPage ?=> Unit = {}): ComponentDocPage =
    composite(new ComponentDocPage(entry))
}
