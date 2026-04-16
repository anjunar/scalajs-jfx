package app.pages
import app.domain.InsightRecord
import jfx.action.Button.*
import jfx.control.{TableCell, TableColumn, TableRow, TableView}
import jfx.control.cell.PropertyValueFactory
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.core.state.{ListProperty, Property}
import jfx.dsl.*
import jfx.form.Input
import jfx.form.Input.{input, placeholder, placeholder_=}
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.layout.Viewport
import jfx.layout.Viewport.WindowConf
import jfx.statement.ObserveRender.observeRender
import org.scalajs.dom
import org.scalajs.dom.{HTMLDivElement, KeyboardEvent}

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.control.NonFatal

class TablePage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  private given ExecutionContext = ExecutionContext.global

  final case class InsightQuery(
    search: String = "",
    stage: Option[String] = None,
    sort: Seq[String] = Seq("tension,desc"),
    offset: Int = 0,
    size: Int = 18
  )

  private final case class QueueTelemetry(
    filteredCount: Int,
    totalCount: Int,
    loadedCount: Int,
    loading: Boolean,
    error: Option[String],
    activeSort: String,
    query: InsightQuery,
    topicCounts: Map[String, Int]
  )

  private val topics = Vector("Forms", "Tables", "Windows", "Docs")
  private val themes = Vector(
    "Onboarding voice",
    "Viewport memory",
    "Form trust boundary",
    "Archive discoverability",
    "Review prompts",
    "Window persistence",
    "List maturity signal",
    "Routing narrative",
    "Field note continuity",
    "Docs package context"
  )
  private val stewards = Vector(
    "Runtime Systems",
    "Research",
    "Design Systems",
    "Documentation",
    "Product Strategy",
    "Forms Infrastructure"
  )
  private val summaries = Vector(
    "Conflicting notes are still present and should remain legible.",
    "The current implementation works, but the context around it is still incomplete.",
    "Several routes point at the same concern and need a calmer narrative.",
    "The framework primitive is stable, yet its place in the showcase still needs refinement."
  )
  private val nextSteps = Map(
    "Forms" -> "Keep the intake steady until the record has enough shape to work with.",
    "Tables" -> "Keep the queue readable while you sort, filter and compare entries.",
    "Windows" -> "Use the side surface when a secondary task should stay nearby.",
    "Docs" -> "Keep the reference stable and reopen it only when you need a detail."
  )

  private val demoData: Vector[InsightRecord] = buildDemoData(total = 192)

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given TablePage = this

      classes = "clarity-page table-page"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "20px"
        maxWidth = "1240px"
        margin = "0 auto"
      }

      val remoteRecords = ListProperty.remote[InsightRecord, InsightQuery](
        loader = ListProperty.RemoteLoader(query => delayedRemotePage(query)),
        initialQuery = InsightQuery(),
        sortUpdater = Some((query, sorting) =>
          query.copy(
            sort = sorting.map(_.asQueryValue),
            offset = 0
          )
        ),
        rangeQueryUpdater = Some((query, startIndex, visibleCount) =>
          query.copy(
            offset = startIndex,
            size = math.max(query.size, visibleCount)
          )
        )
      )

      val selectedRecord = Property[InsightRecord | Null](null)
      val telemetry = Property(computeTelemetry(remoteRecords.query, remoteRecords))

      val filterDebounceMs = 260
      var pendingFilterHandle: Option[SetTimeoutHandle] = None
      var queuedFilterReload: Option[String] = None
      var filterInput: Input | Null = null

      def refreshTelemetry(): Unit =
        telemetry.setAlways(computeTelemetry(remoteRecords.query, remoteRecords))

      def currentFilterValue: String =
        Option(filterInput).map(_.element.value.trim).getOrElse("")

      def cancelPendingFilterReload(): Unit = {
        pendingFilterHandle.foreach(clearTimeout)
        pendingFilterHandle = None
      }

      def shouldReloadSearch(search: String): Boolean = {
        val currentQuery = remoteRecords.query
        currentQuery.search != search ||
        currentQuery.offset != 0 ||
        remoteRecords.errorProperty.get.nonEmpty
      }

      def runSearchReload(search: String): Unit = {
        queuedFilterReload = None
        if (shouldReloadSearch(search)) {
          discard(remoteRecords.reload(current => current.copy(search = search, offset = 0)))
        } else {
          refreshTelemetry()
        }
      }

      def requestSearchReload(search: String): Unit =
        if (remoteRecords.loadingProperty.get) {
          queuedFilterReload = Some(search)
        } else {
          runSearchReload(search)
        }

      def scheduleSearchReload(): Unit = {
        val nextValue = currentFilterValue
        cancelPendingFilterReload()
        pendingFilterHandle = Some(setTimeout(filterDebounceMs) {
          pendingFilterHandle = None
          requestSearchReload(nextValue)
        })
      }

      def applyStageFilter(stage: Option[String]): Unit = {
        cancelPendingFilterReload()
        queuedFilterReload = None
        discard(remoteRecords.reload(current =>
          current.copy(
            search = currentFilterValue,
            stage = stage,
            offset = 0
          )
        ))
      }

      def clearFilters(): Unit = {
        cancelPendingFilterReload()
        queuedFilterReload = None
        Option(filterInput).foreach(_.element.value = "")
        discard(remoteRecords.reload(current => current.copy(search = "", stage = None, offset = 0)))
      }

      def openSelectedWindow(): Unit =
        Option(selectedRecord.get) match
          case Some(record) =>
            Viewport.addWindow(
              WindowConf(
                title = record.title.get,
                width = 480,
                height = 320,
                resizable = true,
                component = Viewport.captureComponent {
                  div {
                    classes = "window-demo-card window-demo-card--record"

                    div {
                      classes = "window-demo-card__title"
                      text = record.title.get
                    }

                    div {
                      classes = "window-demo-card__copy"
                      text = record.summary.get
                    }

                    div {
                      classes = "window-demo-card__meta"
                      text = s"Next step: ${record.nextStep.get}"
                    }
                  }
                }
              )
            )
          case None =>
            Viewport.notify(
              message = "Select a row before opening a focused context window.",
              kind = Viewport.NotificationKind.Info,
              durationMs = 2400
            )

      div {
        classes = "clarity-hero clarity-hero--table"

        div {
          classes = "clarity-hero__eyebrow"
          text = "Data table"
        }

        div {
          classes = "clarity-hero__title"
          text = "Load, filter and sort data in one place."
        }

        div {
          classes = "clarity-hero__copy"
          text = "This page shows remote loading, topic filters, row selection and a detail view."
        }
      }

      val table = buildTable(remoteRecords)

      div {
        classes = "table-page__layout"

        div {
          classes = "table-page__main"

          div {
            classes = "clarity-zone table-page__surface"

            div {
              classes = "clarity-zone-heading"

              div {
                classes = "clarity-zone-heading__label"
                text = "Work Surface"
              }

              div {
                classes = "clarity-zone-heading__title"
                text = "Filter the queue, keep topic visible and select one record for deeper context."
              }
            }

            hbox {
              classes = "table-page__toolbar"

              filterInput = input("queue-filter") {
                placeholder = "Search title, steward or unresolved summary"
                classes = "table-page__search"

                element.oninput = _ => scheduleSearchReload()
                element.onkeydown = (event: KeyboardEvent) =>
                  if (event.key == "Enter") {
                    event.preventDefault()
                    cancelPendingFilterReload()
                    requestSearchReload(currentFilterValue)
                  }
              }

              button("Reset") {
                buttonType = "button"
                classes = Seq("calm-action", "calm-action--quiet")

                onClick { _ =>
                  clearFilters()
                }
              }

              button("Sort by tension") {
                buttonType = "button"
                classes = Seq("calm-action", "calm-action--quiet")

                onClick { _ =>
                  discard(remoteRecords.applySorting(Seq(ListProperty.RemoteSort("tension", ascending = false))))
                }
              }

              button("Sort by revisions") {
                buttonType = "button"
                classes = Seq("calm-action", "calm-action--quiet")

                onClick { _ =>
                  discard(remoteRecords.applySorting(Seq(ListProperty.RemoteSort("revisions", ascending = false))))
                }
              }
            }

            observeRender(telemetry) { current =>
              div {
                classes = "table-page__state-filter"

                stageFilterButton("All topics", None, current.query.stage)(applyStageFilter)
                topics.foreach { topic =>
                  stageFilterButton(topic, Some(topic), current.query.stage)(applyStageFilter)
                }
              }
            }

            observeRender(telemetry) { current =>
              div {
                classes = "table-page__status"
                text = queueStatusCopy(current)
              }
            }

            table
          }
        }

        div {
          classes = "table-page__aside"

          observeRender(telemetry) { current =>
            div {
              classes = "clarity-zone"

              div {
                classes = "clarity-zone-heading"

                div {
                  classes = "clarity-zone-heading__label"
                  text = "Queue Reading"
                }

                div {
                  classes = "clarity-zone-heading__title"
                  text = s"${current.filteredCount} relevant records out of ${current.totalCount}"
                }

                div {
                  classes = "clarity-zone-heading__copy"
                  text = s"Loaded ${current.loadedCount}. Sort: ${current.activeSort}."
                }
              }

              div {
                classes = "table-page__count-grid"

                topics.foreach { topic =>
                  div {
                    classes = "table-page__count-card"

                    div {
                      classes = Seq("clarity-state-chip", s"is-${stateCss(topic)}")
                      text = topic
                    }

                    div {
                      classes = "table-page__count-value"
                      text = current.topicCounts.getOrElse(topic, 0).toString
                    }

                    div {
                      classes = "table-page__count-copy"
                      text = nextSteps(topic)
                    }
                  }
                }
              }
            }
          }

          observeRender(selectedRecord) { maybeRecord =>
            div {
              classes = "clarity-zone"

              div {
                classes = "clarity-zone-heading"

                div {
                  classes = "clarity-zone-heading__label"
                  text = "Selected Record"
                }

                div {
                  classes = "clarity-zone-heading__title"
                  text = Option(maybeRecord).map(_.title.get).getOrElse("No record selected yet")
                }

                div {
                  classes = "clarity-zone-heading__copy"
                  text = Option(maybeRecord).map(_.summary.get).getOrElse("Pick a row to inspect the current tension, stewardship and next step.")
                }
              }

              Option(maybeRecord) match
                case Some(record) =>
                  div {
                    classes = "table-page__detail-list"

                    detailRow("Topic", record.state.get, record.state.get)
                    detailRow("Steward", record.steward.get)
                    detailRow("Tension", tensionLabel(record.tension.get))
                    detailRow("Revisions", record.revisions.get.toString)
                    detailRow("Updated", record.updatedAt.get)
                  }

                  div {
                    classes = "table-page__detail-next"
                    text = s"Next step: ${record.nextStep.get}"
                  }

                  button("Open Focus Window") {
                    buttonType = "button"
                    classes = Seq("calm-action", "calm-action--secondary")

                    onClick { _ =>
                      openSelectedWindow()
                    }
                  }

                case None =>
                  div {
                    classes = "table-page__detail-next"
                    text = "A focused window can open once a specific queue record is selected."
                  }
            }
          }
        }
      }

      addDisposable(table.getSelectionModel.selectedItemProperty.observe(item => selectedRecord.set(item)))
      addDisposable(remoteRecords.observe(_ => refreshTelemetry()))
      addDisposable(remoteRecords.queryProperty.observe(_ => refreshTelemetry()))
      addDisposable(remoteRecords.loadingProperty.observe { loading =>
        refreshTelemetry()
        if (!loading) {
          queuedFilterReload.foreach { search =>
            queuedFilterReload = None
            setTimeout(0) {
              requestSearchReload(search)
            }
          }
        }
      })
      addDisposable(remoteRecords.errorProperty.observe(_ => refreshTelemetry()))
      addDisposable(remoteRecords.totalCountProperty.observe(_ => refreshTelemetry()))
      addDisposable(remoteRecords.sortingProperty.observe(_ => refreshTelemetry()))
    }

  private def buildTable(remoteRecords: ListProperty[InsightRecord]): TableView[InsightRecord] =
    TableView.tableView[InsightRecord] {
      val table = summon[TableView[InsightRecord]]
      table.items = remoteRecords
      table.setFixedCellSize(44)
      table.setRowFactory(_ => new InsightRow())
      table.setPlaceholder(
        div {
          classes = "clarity-empty-state"

          div {
            classes = "clarity-empty-state__title"
            text = "No records match the current lens"
          }

          div {
            classes = "clarity-empty-state__copy"
            text = "Try another search term or move back to all topics. The queue keeps conflict visible, so empty results are treated as information."
          }
        }
      )

      classes = "table-page__table"

      style {
        height = "560px"
      }

      TableColumn.column[InsightRecord, String]("Topic") {
        val currentColumn = summon[TableColumn[InsightRecord, String]]
        currentColumn.setCellValueFactory(new PropertyValueFactory[InsightRecord, String]("state"))
        currentColumn.prefWidth = 160
        currentColumn.setSortable(true)
        currentColumn.setSortKey("state")
        currentColumn.setResizable(false)
        currentColumn.setCellFactory(_ => new InsightStateCell())
      }

      TableColumn.column[InsightRecord, String]("Thought Field") {
        val currentColumn = summon[TableColumn[InsightRecord, String]]
        currentColumn.setCellValueFactory(new PropertyValueFactory[InsightRecord, String]("title"))
        currentColumn.prefWidth = 360
        currentColumn.setSortable(true)
        currentColumn.setSortKey("title")
      }

      TableColumn.column[InsightRecord, String]("Steward") {
        val currentColumn = summon[TableColumn[InsightRecord, String]]
        currentColumn.setCellValueFactory(new PropertyValueFactory[InsightRecord, String]("steward"))
        currentColumn.prefWidth = 190
        currentColumn.setSortable(true)
        currentColumn.setSortKey("steward")
      }

      TableColumn.column[InsightRecord, Int]("Tension") {
        val currentColumn = summon[TableColumn[InsightRecord, Int]]
        currentColumn.setCellValueFactory(new PropertyValueFactory[InsightRecord, Int]("tension"))
        currentColumn.prefWidth = 120
        currentColumn.setSortable(true)
        currentColumn.setSortKey("tension")
        currentColumn.setResizable(false)
        currentColumn.setCellFactory(_ => new InsightTensionCell())
      }

      TableColumn.column[InsightRecord, Int]("Revisions") {
        val currentColumn = summon[TableColumn[InsightRecord, Int]]
        currentColumn.setCellValueFactory(new PropertyValueFactory[InsightRecord, Int]("revisions"))
        currentColumn.prefWidth = 120
        currentColumn.setSortable(true)
        currentColumn.setSortKey("revisions")
        currentColumn.setResizable(false)
      }
    }

  private def computeTelemetry(query: InsightQuery, remoteRecords: ListProperty[InsightRecord]): QueueTelemetry = {
    val filtered = filteredData(query)
    val remote = remoteRecords.remotePropertyOrNull
    val activeSort =
      query.sort.headOption
        .flatMap(parseSort)
        .map { case (field, ascending) =>
          val label =
            field match
              case "state" => "topic"
              case "title" => "title"
              case "steward" => "steward"
              case "tension" => "tension"
              case "revisions" => "revisions"
              case other => other
          s"$label ${if (ascending) "ascending" else "descending"}"
        }
        .getOrElse("manual")

    QueueTelemetry(
      filteredCount = filtered.length,
      totalCount = demoData.length,
      loadedCount = remoteRecords.length,
      loading = remote.loadingProperty.get,
      error = remote.errorProperty.get.flatMap(error => Option(error.getMessage)).filter(_.nonEmpty),
      activeSort = activeSort,
      query = query,
      topicCounts = topics.iterator.map(topic => topic -> filtered.count(_.state.get == topic)).toMap
    )
  }

  private def queueStatusCopy(current: QueueTelemetry): String = {
    val stageLabel = current.query.stage.getOrElse("all topics")
    val loadingCopy = if (current.loading) " Refresh in progress." else ""
    val errorCopy = current.error.map(message => s" Issue: $message").getOrElse("")
    s"Viewing $stageLabel with ${current.filteredCount} relevant records. Loaded ${current.loadedCount}. Sorted by ${current.activeSort}.$loadingCopy$errorCopy"
  }

  private def stageFilterButton(
    label: String,
    stage: Option[String],
    activeStage: Option[String]
  )(onPick: Option[String] => Unit): Unit =
    button(label) {
      buttonType = "button"
      classes = Vector("table-page__filter-chip") ++ Option.when(stage == activeStage)("is-active")

      onClick { _ =>
        onPick(stage)
      }
    }

  private def detailRow(label: String, value: String, stateValue: String = ""): Unit =
    div {
      classes = "table-page__detail-row"

      div {
        classes = "table-page__detail-label"
        text = label
      }

      div {
        classes =
          Vector("table-page__detail-value") ++ Option.when(stateValue.nonEmpty)(s"is-${stateCss(stateValue)}")
        text = value
      }
    }

  private def delayedRemotePage(query: InsightQuery): js.Promise[ListProperty.RemotePage[InsightRecord, InsightQuery]] = {
    val promise = Promise[ListProperty.RemotePage[InsightRecord, InsightQuery]]()

    setTimeout(320) {
      try {
        promise.success(loadRemotePage(query))
      } catch {
        case NonFatal(error) =>
          promise.failure(error)
      }
    }

    promise.future.toJSPromise
  }

  private def loadRemotePage(query: InsightQuery): ListProperty.RemotePage[InsightRecord, InsightQuery] = {
    if (query.search.equalsIgnoreCase("stalled")) {
      throw RuntimeException("The queue could not refresh. Remove the 'stalled' filter and try again.")
    }

    val normalizedOffset = math.max(0, query.offset)
    val normalizedSize = math.max(1, query.size)
    val filtered = filteredData(query)
    val sorted = sortRecords(filtered, query.sort)
    val untilIndex = math.min(sorted.length, normalizedOffset + normalizedSize)
    val pageItems =
      if (normalizedOffset >= sorted.length) Vector.empty
      else sorted.slice(normalizedOffset, untilIndex)
    val hasMore = untilIndex < sorted.length

    ListProperty.RemotePage(
      items = pageItems,
      offset = Some(normalizedOffset),
      nextQuery = Option.when(hasMore)(query.copy(offset = untilIndex)),
      totalCount = Some(sorted.length),
      hasMore = Some(hasMore)
    )
  }

  private def filteredData(query: InsightQuery): Vector[InsightRecord] =
    demoData.filter { record =>
      val stageMatches = query.stage.forall(_ == record.state.get)
      val search = normalize(query.search)
      val searchMatches =
        if (search.isEmpty) true
        else {
          Seq(record.title.get, record.steward.get, record.summary.get)
            .exists(value => normalize(value).contains(search))
        }

      stageMatches && searchMatches
    }

  private def sortRecords(records: Vector[InsightRecord], sort: Seq[String]): Vector[InsightRecord] =
    sort.headOption.flatMap(parseSort) match
      case Some(("state", ascending)) =>
        sortBy(records, record => topicRank(record.state.get), ascending)
      case Some(("title", ascending)) =>
        sortBy(records, record => normalize(record.title.get), ascending)
      case Some(("steward", ascending)) =>
        sortBy(records, record => normalize(record.steward.get), ascending)
      case Some(("tension", ascending)) =>
        sortBy(records, _.tension.get, ascending)
      case Some(("revisions", ascending)) =>
        sortBy(records, _.revisions.get, ascending)
      case _ =>
        records

  private def sortBy[A: Ordering](records: Vector[InsightRecord], valueFor: InsightRecord => A, ascending: Boolean): Vector[InsightRecord] = {
    val ordering = Ordering.by[InsightRecord, A](valueFor)
    records.sorted(using if (ascending) ordering else ordering.reverse)
  }

  private def parseSort(sortValue: String): Option[(String, Boolean)] = {
    val parts = sortValue.split(",", 2).map(_.trim)
    if (parts.isEmpty || parts(0).isEmpty) None
    else Some(parts(0) -> (parts.length < 2 || !parts(1).equalsIgnoreCase("desc")))
  }

  private def buildDemoData(total: Int): Vector[InsightRecord] =
    Vector.tabulate(total) { index =>
      val topic =
        index % 4 match
          case 0 => topics(0)
          case 1 => topics(1)
          case 2 => topics(2)
          case _ => topics(3)
      val theme = themes(index % themes.length)
      val steward = stewards((index / 2) % stewards.length)
      val summary = summaries((index / 3) % summaries.length)
      val tension =
        topic match
          case "Forms" => 5 - (index % 2)
          case "Tables" => 3 + (index % 3)
          case "Windows" => 2 + (index % 2)
          case "Docs" => 1 + (index % 2)
      val revisions =
        topic match
          case "Forms" => 1 + (index % 2)
          case "Tables" => 2 + (index % 4)
          case "Windows" => 4 + (index % 4)
          case "Docs" => 6 + (index % 3)

      new InsightRecord(
        title = Property(s"$theme ${index + 1}"),
        state = Property(topic),
        steward = Property(steward),
        tension = Property(tension),
        revisions = Property(revisions),
        summary = Property(summary),
        nextStep = Property(nextSteps(topic)),
        updatedAt = Property(f"2026-04-${(index % 9) + 1}%02d")
      )
    }

  private def normalize(value: String | Null): String =
    Option(value).map(_.trim.toLowerCase).getOrElse("")

  private def topicRank(label: String): Int =
    topics.indexWhere(_ == label) match
      case -1 => topics.length
      case rank => rank

  private def stateCss(label: String): String =
    label.trim.toLowerCase

  private def tensionLabel(value: Int): String =
    value match
      case 1 => "Quiet"
      case 2 => "Held"
      case 3 => "Active"
      case 4 => "Sharp"
      case _ => "Critical"

  private def discard(promise: js.Promise[?]): Unit = {
    promise.toFuture.recover { case NonFatal(error) => dom.console.error(error.getMessage) }
    ()
  }

  private final class InsightRow extends TableRow[InsightRecord] {
    this.element.classList.add("clarity-table__row")

    override protected def updateItem(item: InsightRecord | Null, empty: Boolean): Unit = {
      topics.foreach(topic => this.element.classList.remove(s"is-${stateCss(topic)}"))
      if (!empty && item != null) {
        this.element.classList.add(s"is-${stateCss(item.state.get)}")
        this.element.setAttribute("data-tension", item.tension.get.toString)
      } else {
        this.element.removeAttribute("data-tension")
      }
    }
  }

  private final class InsightStateCell extends TableCell[InsightRecord, String] {
    this.element.classList.add("clarity-table__state-cell")

    override protected def updateItem(item: String | Null, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      topics.foreach(topic => this.element.classList.remove(s"is-${stateCss(topic)}"))
      if (!empty && item != null) {
        this.element.classList.add(s"is-${stateCss(item)}")
      }
    }
  }

  private final class InsightTensionCell extends TableCell[InsightRecord, Int] {
    this.element.classList.add("clarity-table__tension-cell")

    override protected def updateItem(item: Int | Null, empty: Boolean): Unit = {
      this.element.classList.remove("is-sharp")
      this.element.classList.remove("is-critical")
      if (empty || item == null) {
        super.updateItem(item, empty)
      } else {
        val value = item.asInstanceOf[Int]
        textContent = tensionLabel(value)
        if (value >= 4) this.element.classList.add("is-sharp")
        if (value >= 5) this.element.classList.add("is-critical")
      }
    }
  }
}

object TablePage {
  def tablePage(init: TablePage ?=> Unit = {}): TablePage =
    composite(new TablePage())
}
