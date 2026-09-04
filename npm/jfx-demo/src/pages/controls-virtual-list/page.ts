import { classes, div, listProperty, text } from "@anjunar/jfx-core";
import { virtualList } from "@anjunar/jfx-controls";

const rows: readonly string[] = Array.from({ length: 200 }, (_, index) => `Row ${index + 1}`);

export function controlsVirtualListPage(): void {
  const items = listProperty<string>([...rows]);

  div(() => {
    classes("h-80");
    virtualList(
      items,
      (item) => {
        div(() => {
          classes("px-3", "py-2", "border-b", "border-line");
          text(item ?? "");
        });
      },
      {
        estimateHeightPx: 36,
        overscanPx: 200,
        header: () => {
          div(() => {
            classes("px-3", "py-2", "font-semibold");
            text(`${rows.length} rows`);
          });
        },
      }
    );
  });
}
