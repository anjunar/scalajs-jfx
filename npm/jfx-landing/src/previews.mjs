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
    { name: "Customer portal", owner: "Mira", language: "TypeScript", status: "Shipping", progress: "92%" },
    { name: "Operations desk", owner: "Noah", language: "Scala", status: "Healthy", progress: "84%" },
    { name: "Identity service", owner: "Ivy", language: "Scala", status: "Review", progress: "71%" },
    { name: "Analytics studio", owner: "Leo", language: "TypeScript", status: "Building", progress: "63%" },
    { name: "Inventory sync", owner: "Aya", language: "Scala", status: "At risk", progress: "48%" },
    { name: "Support console", owner: "Sam", language: "TypeScript", status: "Planned", progress: "24%" },
  ]);
  tableView(rows, [
    column("Project", row => text(row.name), { prefWidth: 210 }),
    column("Owner", row => text(row.owner), { prefWidth: 110 }),
    column("API", row => text(row.language), { prefWidth: 150 }),
    column("Status", row => text(row.status), { prefWidth: 140 }),
    column("Progress", row => text(row.progress), { prefWidth: 120 }),
  ], { rowHeight: 42, showFooter: false });
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
