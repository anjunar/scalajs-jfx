package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.Image.*
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.i18n.i18n

object ImagePage {
  def render()(using AbstractComponent, Cursor): Unit = {
    showcasePage(i18n"Images & graphics", i18n"A picture says more than a thousand lines of code.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(i18n"Visual presence", i18n"Images give your application depth and identity.", i18n"The Image component binds native <img> elements reactively into the DSL. It supports static sources as well as reactive properties for dynamic galleries or profile pictures.")

        componentShowcase(i18n"Static image", i18n"Simple inclusion of an image with source and alt text.") {
          hbox {
            style { gap = "20px"; alignItems = "center" }
            image {
              src = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?ixlib=rb-1.2.1&auto=format&fit=crop&w=300&q=80"
              alt = "A cute cat"
              style { borderRadius = "8px"; width = "200px"; height = "auto"; boxShadow = "0 4px 12px rgba(0,0,0,0.1)" }
            }
            div { style { flex = "1" }; text(i18n"This image is loaded statically. The Image component ensures that all attributes are set correctly.") {} }
          }
        }

        componentShowcase(i18n"Dynamic image source", i18n"The source can be bound to a property to swap images at runtime.") {
          val currentSrc = Property("https://images.unsplash.com/photo-1543852786-1cf6624b9987?ixlib=rb-1.2.1&auto=format&fit=crop&w=300&q=80")

          vbox {
            style { gap = "15px"; alignItems = "center" }
            image {
              src = currentSrc
              alt = "Dynamic image"
              style { borderRadius = "50%"; width = "150px"; height = "150px"; objectFit = "cover"; border = "3px solid var(--aj-accent)" }
            }
            hbox {
              style { gap = "10px" }
              button(i18n"Cat 1") { onClick { _ => currentSrc.set("https://images.unsplash.com/photo-1543852786-1cf6624b9987?ixlib=rb-1.2.1&auto=format&fit=crop&w=300&q=80") } }
              button(i18n"Cat 2") { onClick { _ => currentSrc.set("https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=300&q=80") } }
            }
          }
        }

        apiSection(i18n"DSL syntax", i18n"Attribute assignments happen intuitively inside the block.") {
          codeBlock("scala", """image {
  src = "path/to/image.jpg"
  alt = "Description"
  style { width = "100%" }
}

// Oder reaktiv:
val myProperty = Property("...")
image {
  src = myProperty
}""")
        }
      }
    }
  }
}
