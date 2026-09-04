import { classes, div, property } from "@anjunar/jfx-core";
import { form, inputContainer, input } from "@anjunar/jfx-forms";
import { editor } from "@anjunar/jfx-editor";

export function editorBasicsPage(): void {
  const model = {
    title: property("Getting started"),
    body: property<unknown>(null),
  };

  form(model, {}, () => {
    div(() => {
      classes("flex", "flex-col", "gap-3");

      inputContainer({ label: "Title" }, () => {
        input("title");
      });

      editor("body", {
        placeholder: "Write the article...",
        plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
      });
    });
  });
}
