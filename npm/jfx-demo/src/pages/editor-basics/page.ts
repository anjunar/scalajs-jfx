import { classes, div, property } from "@anjunar/jfx-core";
import { form, inputContainer, input } from "@anjunar/jfx-forms";
import { editor } from "@anjunar/jfx-editor";
import { translated } from "../../app/i18n.js";

export function editorBasicsPage(): void {
  const model = {
    title: property(translated("Getting started").get),
    body: property(
      "## Markdown editor\n\nThe public value stays **Markdown**.\n\n" +
        "| Feature | Representation |\n| --- | --- |\n| Tables | GFM pipe table |\n| Code | Fenced block |\n\n" +
        "```scala\nval publicValue = \"Markdown\"\n```\n\n---"
    ),
  };

  form(model, {}, () => {
    div(() => {
      classes("flex", "flex-col", "gap-3");

      inputContainer({ label: translated("Title").get }, () => {
        input("title");
      });

      editor("body", {
        placeholder: translated("Write the article...").get,
        plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
      });
    });
  });
}
