package app

import jfx.dsl.*
import jfx.router.Route

import scala.scalajs.js

object Routes {

  val routes = js.Array[Route](

    Route(
      path = "/",
      factory = _ =>
        js.Promise.resolve(
          div {
            text = "Hallo Welt!"
          }
        ),
      children = js.Array(
        Route(
          path = "/person",
          factory = context =>
            js.Promise.resolve(
              div {
                text = s"Person ${context.queryParams.get("id").getOrElse("")}".trim
              }
            )
        )
      )
    )

  )

}
