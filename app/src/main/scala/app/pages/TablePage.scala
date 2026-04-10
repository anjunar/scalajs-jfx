package app.pages

import app.component.AddressForm
import app.domain.{Address, Email, Person}
import jfx.action.Button.*
import jfx.control.{TableColumn, TableView}
import jfx.control.TableColumn.*
import jfx.control.TableView.*
import jfx.control.cell.PropertyValueFactory
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.core.state.{ListProperty, Property}
import jfx.dsl.*
import jfx.form.Input
import jfx.form.Input.{input, placeholder, placeholder_=}
import jfx.layout.Div
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
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

  final case class PersonQuery(
    filter: String = "",
    sort: Seq[String] = Seq.empty,
    offset: Int = 0,
    size: Int = 20
  )

  private val firstNames =
    Vector("Alice", "Bob", "Carla", "David", "Elena", "Frank", "Grace", "Henry", "Isla", "Jonas")
  private val lastNames =
    Vector("Anderson", "Bauer", "Clark", "Diaz", "Evans", "Fischer", "Garcia", "Hughes", "Ivanov", "Johnson")
  private val cities =
    Vector("Berlin", "Hamburg", "Munich", "Cologne", "Leipzig", "Dresden", "Bremen", "Stuttgart")
  private val streetNames =
    Vector("Oak Street", "River Road", "Hill Avenue", "Sunset Lane", "Market Street", "Park Row")

  private val demoData: Vector[Person] = buildDemoData(total = 240)

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given TablePage = this

      style {
        maxWidth = "980px"
        margin = "24px auto"
        padding = "24px"
        display = "flex"
        flexDirection = "column"
        gap = "14px"
        fontFamily = "inherit"
        color = "var(--color-text)"
      }

      val remotePersons = ListProperty.remote[Person, PersonQuery](
        loader = ListProperty.RemoteLoader(query => delayedRemotePage(query)),
        initialQuery = PersonQuery(),
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

      val filterDebounceMs = 280
      var pendingFilterHandle: Option[SetTimeoutHandle] = None
      var queuedFilterReload: Option[String] = None
      var filterInput: Input | Null = null
      var status: Div | Null = null

      def currentFilterValue: String =
        Option(filterInput).map(_.element.value.trim).getOrElse("")

      def cancelPendingFilterReload(): Unit = {
        pendingFilterHandle.foreach(clearTimeout)
        pendingFilterHandle = None
      }

      def shouldReloadFilter(filter: String): Boolean = {
        val currentQuery = remotePersons.query
        currentQuery.filter != filter ||
        currentQuery.offset != 0 ||
        remotePersons.errorProperty.get.nonEmpty
      }

      def updateStatus(): Unit = {
        val total = remotePersons.totalCountProperty.get.map(_.toString).getOrElse("?")
        val query = remotePersons.query
        val sortText =
          if (remotePersons.getSorting.isEmpty) "none"
          else remotePersons.getSorting.map(_.asQueryValue).mkString(", ")
        val loadingText = if (remotePersons.loadingProperty.get) " | loading..." else ""
        val errorText = remotePersons.errorProperty.get
          .flatMap(error => Option(error.getMessage))
          .filter(_.nonEmpty)
          .map(message => s" | error: $message")
          .getOrElse("")

        Option(status).foreach { currentStatus =>
          currentStatus.textContent =
            s"Loaded ${remotePersons.length} of $total rows | filter='${query.filter}' | sort=$sortText | offset=${query.offset} | pageSize=${query.size}$loadingText$errorText"
        }
      }

      def runFilterReload(filter: String): Unit = {
        queuedFilterReload = None
        if (shouldReloadFilter(filter)) {
          discard(remotePersons.reload(current => current.copy(filter = filter, offset = 0)))
        } else {
          updateStatus()
        }
      }

      def requestFilterReload(filter: String): Unit =
        if (remotePersons.loadingProperty.get) {
          queuedFilterReload = Some(filter)
        } else {
          runFilterReload(filter)
        }

      def scheduleFilterReload(): Unit = {
        val filter = currentFilterValue
        cancelPendingFilterReload()
        pendingFilterHandle = Some(setTimeout(filterDebounceMs) {
          pendingFilterHandle = None
          requestFilterReload(filter)
        })
      }

      def applyFilter(): Unit = {
        cancelPendingFilterReload()
        requestFilterReload(currentFilterValue)
      }

      div {
        classes = "showcase-page-hero"

        div {
          classes = "showcase-page-hero__eyebrow"
          text = "Remote Data Showcase"
        }

        div {
          classes = "showcase-page-hero__title"
          text = "A table demo that feels like a product feature, not a toy example."
        }

        div {
          classes = "showcase-page-hero__copy"
          text =
            "This page shows the kind of workflow people expect from real apps: debounced filtering, lazy loading, remote sorting and long-list rendering."
        }

        hbox {
          classes = "showcase-page-hero__badges"

          div {
            classes = "showcase-page-hero__badge"
            text = "RemoteListProperty"
          }

          div {
            classes = "showcase-page-hero__badge"
            text = "Virtualized Rows"
          }

          div {
            classes = "showcase-page-hero__badge"
            text = "Server-Like Sorting"
          }
        }
      }

      div {
        text =
          "Type into the filter for debounced reloads, click the headers for sort state, and scroll into unloaded areas to fetch rows on demand."

        style {
          color = "var(--color-neutral-500)"
          lineHeight = "1.5"
        }
      }

      hbox {
        style {
          alignItems = "center"
          flexWrap = "wrap"
          gap = "10px"
        }

        filterInput = input("person-filter") {
          placeholder = "Filter by first name, last name or city"

          style {
            minWidth = "320px"
            padding = "9px 12px"
            color = "var(--color-text)"
            backgroundColor = "var(--color-background-secondary)"
            border = "1px solid var(--jfx-table-border)"
            borderRadius = "10px"
            fontSize = "14px"
          }

          element.oninput = _ => scheduleFilterReload()
          element.onkeydown = (event: KeyboardEvent) =>
            if (event.key == "Enter") {
              event.preventDefault()
              applyFilter()
            }
        }

        button("Apply Filter") {
          buttonType = "button"

          style {
            padding = "9px 12px"
            color = "var(--color-text)"
            border = "1px solid var(--jfx-table-border)"
            borderRadius = "10px"
            backgroundColor = "var(--color-background-secondary)"
            cursor = "pointer"
          }

          onClick { _ =>
            applyFilter()
          }
        }

        button("Clear Filter") {
          buttonType = "button"

          style {
            padding = "9px 12px"
            color = "var(--color-text)"
            border = "1px solid var(--jfx-table-border)"
            borderRadius = "10px"
            backgroundColor = "var(--color-background-secondary)"
            cursor = "pointer"
          }

          onClick { _ =>
            cancelPendingFilterReload()
            queuedFilterReload = None
            Option(filterInput).foreach(_.element.value = "")
            requestFilterReload("")
          }
        }

        button("Reload") {
          buttonType = "button"

          style {
            padding = "9px 12px"
            color = "var(--color-text)"
            border = "1px solid var(--jfx-table-border)"
            borderRadius = "10px"
            backgroundColor = "var(--color-background-secondary)"
            cursor = "pointer"
          }

          onClick { _ =>
            cancelPendingFilterReload()
            queuedFilterReload = None
            discard(remotePersons.reload())
          }
        }
      }

      status = div {
        style {
          padding = "10px 12px"
          color = "var(--color-text)"
          backgroundColor = "var(--color-background-primary)"
          border = "1px solid var(--jfx-table-border)"
          borderRadius = "10px"
          fontFamily = "Consolas, monospace"
          fontSize = "13px"
        }
      }

      buildTable(remotePersons)

      addDisposable(remotePersons.observe(_ => updateStatus()))
      addDisposable(remotePersons.loadingProperty.observe { loading =>
        updateStatus()
        if (!loading) {
          queuedFilterReload.foreach { filter =>
            queuedFilterReload = None
            setTimeout(0) {
              requestFilterReload(filter)
            }
          }
        }
      })
      addDisposable(remotePersons.errorProperty.observe(_ => updateStatus()))
      addDisposable(remotePersons.totalCountProperty.observe(_ => updateStatus()))
      addDisposable(remotePersons.sortingProperty.observe(_ => updateStatus()))
    }


  private def buildTable(remotePersons: ListProperty[Person]): TableView[Person] =
    tableView[Person] {
      items = remotePersons
      fixedCellSize = 34

      style {
        height = "360px"
      }

      column[Person, String]("First Name") {
        cellValueFactory = new PropertyValueFactory[Person, String]("firstName")
        prefWidth = 180
        sortable = true
        sortKey = "firstName"
      }

      column[Person, String]("Last Name") {
        cellValueFactory = new PropertyValueFactory[Person, String]("lastName")
        prefWidth = 180
        sortable = true
        sortKey = "lastName"
      }

      column[Person, String]("City") {
        cellValueFactory = (features: TableColumn.CellDataFeatures[Person, String]) => {
          val address = features.getValue.address.get
          if (address == null) Property("")
          else address.city
        }
        prefWidth = 180
        sortable = true
        sortKey = "city"
      }

      column[Person, Int]("Emails") {
        cellValueFactory = (features: TableColumn.CellDataFeatures[Person, Int]) =>
          Property(features.getValue.emails.length)
        prefWidth = 90
        sortable = true
        sortKey = "emails"
      }
    }

  private def delayedRemotePage(query: PersonQuery): js.Promise[ListProperty.RemotePage[Person, PersonQuery]] = {
    val promise = Promise[ListProperty.RemotePage[Person, PersonQuery]]()

    setTimeout(350) {
      try {
        promise.success(loadRemotePage(query))
      } catch {
        case NonFatal(error) =>
          promise.failure(error)
      }
    }

    promise.future.toJSPromise
  }

  private def loadRemotePage(query: PersonQuery): ListProperty.RemotePage[Person, PersonQuery] = {
    if (query.filter.equalsIgnoreCase("error")) {
      throw RuntimeException("Simulated backend error. Use another filter value.")
    }

    val normalizedOffset = math.max(0, query.offset)
    val normalizedSize = math.max(1, query.size)
    val filtered = demoData.filter(matchesFilter(_, query.filter))
    val sorted = sortPersons(filtered, query.sort)
    val fromIndex = normalizedOffset
    val untilIndex = math.min(sorted.length, fromIndex + normalizedSize)
    val pageItems =
      if (fromIndex >= sorted.length) Vector.empty
      else sorted.slice(fromIndex, untilIndex)
    val hasMore = untilIndex < sorted.length

    ListProperty.RemotePage(
      items = pageItems,
      offset = Some(fromIndex),
      nextQuery = Option.when(hasMore)(query.copy(offset = untilIndex)),
      totalCount = Some(sorted.length),
      hasMore = Some(hasMore)
    )
  }

  private def matchesFilter(person: Person, filter: String): Boolean = {
    val normalizedFilter = filter.trim.toLowerCase
    if (normalizedFilter.isEmpty) {
      true
    } else {
      Seq(
        person.firstName.get,
        person.lastName.get,
        cityOf(person)
      ).exists(value => normalize(value).contains(normalizedFilter))
    }
  }

  private def sortPersons(persons: Vector[Person], sort: Seq[String]): Vector[Person] =
    sort.headOption.flatMap(parseSort) match {
      case Some(("firstName", ascending)) =>
        sortByString(persons, _.firstName.get, ascending)
      case Some(("lastName", ascending)) =>
        sortByString(persons, _.lastName.get, ascending)
      case Some(("city", ascending)) =>
        sortByString(persons, cityOf, ascending)
      case Some(("emails", ascending)) =>
        val ordering = Ordering.by[Person, Int](_.emails.length)
        persons.sorted(using if (ascending) ordering else ordering.reverse)
      case _ =>
        persons
    }

  private def sortByString(persons: Vector[Person], valueFor: Person => String, ascending: Boolean): Vector[Person] = {
    val ordering = Ordering.by[Person, String](person => normalize(valueFor(person)))
    persons.sorted(using if (ascending) ordering else ordering.reverse)
  }

  private def parseSort(sortValue: String): Option[(String, Boolean)] = {
    val parts = sortValue.split(",", 2).map(_.trim)
    if (parts.isEmpty || parts(0).isEmpty) {
      None
    } else {
      val ascending = parts.length < 2 || !parts(1).equalsIgnoreCase("desc")
      Some(parts(0) -> ascending)
    }
  }

  private def cityOf(person: Person): String = {
    val address = person.address.get
    if (address == null) "" else address.city.get
  }

  private def normalize(value: String | Null): String =
    Option(value).map(_.toLowerCase).getOrElse("")

  private def buildDemoData(total: Int): Vector[Person] =
    Vector.tabulate(total) { index =>
      val firstName = firstNames(index % firstNames.length)
      val lastName = lastNames((index / 2) % lastNames.length)
      val city = cities((index / 3) % cities.length)
      val street = s"${streetNames(index % streetNames.length)} ${10 + (index % 80)}"
      val emailCount = 1 + (index % 3)
      val emails = js.Array(
        (0 until emailCount).map { emailIndex =>
          new Email(Property(s"${normalize(firstName)}.${normalize(lastName)}.${index + 1}.${emailIndex + 1}@demo.dev"))
        } *
      )

      new Person(
        firstName = Property(firstName),
        lastName = Property(lastName),
        address = Property(new Address(Property(street), Property(city))),
        emails = ListProperty(emails)
      )
    }

  private def discard(promise: js.Promise[?]): Unit = {
    promise.toFuture.recover { case NonFatal(error) => dom.console.error(error.getMessage) }
    ()
  }
}

object TablePage {
  def tablePage(init: TablePage ?=> Unit = {}): TablePage =
    composite(new TablePage())
}
