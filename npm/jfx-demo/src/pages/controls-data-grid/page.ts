import { classes, div, listProperty, text } from "@anjunar/jfx-core";
import { dataGrid } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

interface Tile {
  readonly label: string;
}

const tiles: readonly Tile[] = Array.from({ length: 24 }, (_, index) => ({ label: `Tile ${index + 1}` }));

export function controlsDataGridPage(): void {
  const items = listProperty<Tile>([...tiles]);

  div(() => {
    classes("h-80");
    dataGrid(
      items,
      (item) => {
        div(() => {
          classes("flex", "items-center", "justify-center", "h-full", "border", "border-line", "rounded-control");
          text(item ? item.label : "");
        });
      },
      {
        itemWidthPx: 120,
        itemHeightPx: 90,
        gapPx: 8,
        emptyPlaceholder: () => {
          text(translated("No tiles."));
        },
      }
    );
  });
}
