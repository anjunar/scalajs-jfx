package app

import jfx.action.Button
import jfx.control.cell.PropertyValueFactory
import jfx.control.{TableColumn, TableView}
import jfx.core.state.{ListProperty, Property}
import jfx.layout.Div
import org.scalajs.dom
import org.scalajs.dom.HTMLInputElement

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.timers.setTimeout
import scala.util.control.NonFatal

object Main {

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

  def main(args: Array[String]): Unit = {
    val root = new Div()
    styleRoot(root)

    val title = new Div()
    title.textContent = "RemoteListProperty Demo"
    styleTitle(title)

    val description = new Div()
    description.textContent =
      "Use the filter, click the column headers for server sorting, and scroll near the bottom to lazy load more rows."
    styleDescription(description)

    val toolbar = new Div()
    styleToolbar(toolbar)

    val filterInput = dom.document.createElement("input").asInstanceOf[HTMLInputElement]
    styleFilterInput(filterInput)
    filterInput.placeholder = "Filter by first name, last name or city"

    val applyFilterButton = new Button()
    applyFilterButton.textContent = "Apply Filter"
    applyFilterButton.buttonType = "button"
    styleButton(applyFilterButton)

    val clearFilterButton = new Button()
    clearFilterButton.textContent = "Clear Filter"
    clearFilterButton.buttonType = "button"
    styleButton(clearFilterButton)

    val reloadButton = new Button()
    reloadButton.textContent = "Reload"
    reloadButton.buttonType = "button"
    styleButton(reloadButton)

    toolbar.element.appendChild(filterInput)
    toolbar.addChild(applyFilterButton)
    toolbar.addChild(clearFilterButton)
    toolbar.addChild(reloadButton)

    val status = new Div()
    styleStatus(status)

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

    val table = buildTable()
    table.setItems(remotePersons)
    table.setFixedCellSize(34)
    table.element.style.height = "360px"

    def applyFilter(): Unit = {
      val filter = filterInput.value.trim
      discard(remotePersons.reload(current => current.copy(filter = filter, offset = 0)))
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

      status.textContent =
        s"Loaded ${remotePersons.length} of $total rows | filter='${query.filter}' | sort=$sortText | offset=${query.offset} | pageSize=${query.size}$loadingText$errorText"
    }

    applyFilterButton.addClick(_ => applyFilter())
    clearFilterButton.addClick(_ => {
      filterInput.value = ""
      discard(remotePersons.reload(current => current.copy(filter = "", offset = 0)))
    })
    reloadButton.addClick(_ => discard(remotePersons.reload()))

    filterInput.onkeydown = event =>
      if (event.key == "Enter") {
        applyFilter()
      }

    remotePersons.observe(_ => updateStatus())
    remotePersons.loadingProperty.observe(_ => updateStatus())
    remotePersons.errorProperty.observe(_ => updateStatus())
    remotePersons.totalCountProperty.observe(_ => updateStatus())
    remotePersons.sortingProperty.observe(_ => updateStatus())

    root.addChild(title)
    root.addChild(description)
    root.addChild(toolbar)
    root.addChild(status)
    root.addChild(table)

    dom.document.body.innerHTML = ""
    dom.document.body.appendChild(root.element)
  }

  private def buildTable(): TableView[Person] = {
    val table = new TableView[Person]()

    val firstName = new TableColumn[Person, String]("First Name")
    firstName.setCellValueFactory(new PropertyValueFactory[Person, String]("firstName"))
    firstName.setPrefWidth(180)
    firstName.setSortable(true)
    firstName.setSortKey("firstName")

    val lastName = new TableColumn[Person, String]("Last Name")
    lastName.setCellValueFactory(new PropertyValueFactory[Person, String]("lastName"))
    lastName.setPrefWidth(180)
    lastName.setSortable(true)
    lastName.setSortKey("lastName")

    val city = new TableColumn[Person, String]("City")
    city.setCellValueFactory(features => {
      val address = features.getValue.address.get
      if (address == null) Property("")
      else address.city
    })
    city.setPrefWidth(180)
    city.setSortable(true)
    city.setSortKey("city")

    val emailCount = new TableColumn[Person, Int]("Emails")
    emailCount.setCellValueFactory(features => Property(features.getValue.emails.length))
    emailCount.setPrefWidth(90)
    emailCount.setSortable(true)
    emailCount.setSortKey("emails")

    table.getColumns.addOne(firstName)
    table.getColumns.addOne(lastName)
    table.getColumns.addOne(city)
    table.getColumns.addOne(emailCount)
    table
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
        }*
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

  private def styleRoot(root: Div): Unit = {
    root.element.style.maxWidth = "980px"
    root.element.style.margin = "24px auto"
    root.element.style.padding = "24px"
    root.element.style.display = "flex"
    root.element.style.setProperty("flex-direction", "column")
    root.element.style.setProperty("gap", "14px")
    root.element.style.fontFamily = "Segoe UI, sans-serif"
    root.element.style.color = "#0f172a"
  }

  private def styleTitle(title: Div): Unit = {
    title.element.style.fontSize = "28px"
    title.element.style.fontWeight = "700"
  }

  private def styleDescription(description: Div): Unit = {
    description.element.style.color = "#475569"
    description.element.style.lineHeight = "1.5"
  }

  private def styleToolbar(toolbar: Div): Unit = {
    toolbar.element.style.display = "flex"
    toolbar.element.style.setProperty("align-items", "center")
    toolbar.element.style.setProperty("flex-wrap", "wrap")
    toolbar.element.style.setProperty("gap", "10px")
  }

  private def styleFilterInput(input: HTMLInputElement): Unit = {
    input.style.minWidth = "320px"
    input.style.padding = "9px 12px"
    input.style.border = "1px solid #cbd5e1"
    input.style.borderRadius = "6px"
    input.style.fontSize = "14px"
  }

  private def styleButton(button: Button): Unit = {
    button.element.style.padding = "9px 12px"
    button.element.style.border = "1px solid #cbd5e1"
    button.element.style.borderRadius = "6px"
    button.element.style.backgroundColor = "#f8fafc"
    button.element.style.cursor = "pointer"
  }

  private def styleStatus(status: Div): Unit = {
    status.element.style.padding = "10px 12px"
    status.element.style.backgroundColor = "#f8fafc"
    status.element.style.border = "1px solid #e2e8f0"
    status.element.style.borderRadius = "6px"
    status.element.style.fontFamily = "Consolas, monospace"
    status.element.style.fontSize = "13px"
  }
}
