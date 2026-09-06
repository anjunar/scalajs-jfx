/** Keep title/summary in sync with the "/controls/data-grid" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { controlsDataGridPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function controlsDataGridDoc(): void {
  docPage(
    {
      title: "DataGrid",
      summary: "dataGrid(): 180 rich cards in responsive columns, with viewport virtualization and reactive selection.",
    },
    () => {
      example({ code: snippet, note: "The renderer's item is null for a position that exists but has not loaded yet -- meaningful for a remote source, always non-null for a local one." }, () => {
        controlsDataGridPage();
      });
    }
  );
}
