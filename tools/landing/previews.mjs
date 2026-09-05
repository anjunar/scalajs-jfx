// Real library SSR, generated during the Pages build. These are readonly
// previews; the linked demo routes expose the complete interactive controls.
import "@anjunar/scalajs-jfx-bridge";
import { attr, listProperty, property, renderToString, text } from "@anjunar/jfx-core";
import { form, input, inputContainer } from "@anjunar/jfx-forms";
import { column, tableView } from "@anjunar/jfx-controls";
import { editor } from "@anjunar/jfx-editor";
import { counter } from "./counter.mjs";

export function accountForm() {
  const model = { name: property("Mira"), email: property("mira@example.com") };
  form(model, {}, () => {
    inputContainer({ label: "Name" }, () => input("name", {}, () => attr("aria-label", "Name")));
    inputContainer({ label: "Email" }, () => input("email", { type: "email" }, () => attr("aria-label", "Email")));
  });
}

export function projectTable() {
  const rows = listProperty([
    { name: "Website", language: "TypeScript" },
    { name: "Workspace", language: "Scala" },
    { name: "Admin console", language: "TypeScript" },
  ]);
  tableView(rows, [
    column("Project", row => text(row.name), { prefWidth: 150 }),
    column("API", row => text(row.language), { prefWidth: 650 }),
  ], { rowHeight: 40, showFooter: false });
}

export function articleEditor() {
  editor("body", {
    standalone: true,
    value: "## A place for your ideas\n\nWrite **rich content**. Keep a Markdown value.\n\n- Headings and lists\n- Links, tables and code",
    editable: false,
    editUrl: "./typescript/editor/basics",
    editLabel: "Open live editor ↗",
  });
}

export async function renderPreviews() {
  const result = {};
  for (const [name, body] of Object.entries({ counter, accountForm, projectTable, articleEditor })) {
    result[name] = (await renderToString(body)).html;
  }
  return result;
}
