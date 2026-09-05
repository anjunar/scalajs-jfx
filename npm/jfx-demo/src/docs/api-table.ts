/** A signature/options table -- name, type, description. See CLAUDE_DEMO_PLAN.md §4. */
import { classes, element, text } from "@anjunar/jfx-core";
import { translated } from "../app/i18n.js";

const table = element("table");
const thead = element("thead");
const tbody = element("tbody");
const tr = element("tr");
const th = element("th");
const td = element("td");

export interface ApiRow {
  readonly name: string;
  readonly type: string;
  readonly description: string;
}

export function apiTable(rows: readonly ApiRow[]): void {
  table(() => {
    classes("docs-api-table");
    thead(() => {
      tr(() => {
        th(() => text(translated("Name")));
        th(() => text(translated("Type")));
        th(() => text(translated("Description")));
      });
    });
    tbody(() => {
      for (const row of rows) {
        tr(() => {
          td(() => {
            classes("docs-api-table__name");
            text(row.name);
          });
          td(() => {
            classes("docs-api-table__type");
            text(row.type);
          });
          td(() => text(row.description));
        });
      }
    });
  });
}
