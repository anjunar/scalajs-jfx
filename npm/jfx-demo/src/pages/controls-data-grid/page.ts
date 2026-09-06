import { attr, classIf, classes, div, listProperty, onClick, property, style, text } from "@anjunar/jfx-core";
import { dataGrid } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

interface Tile {
  readonly title: string;
  readonly category: string;
  readonly summary: string;
  readonly accent: string;
}

const catalog: readonly Tile[] = [
  { title: "Atlas Memo", category: "Research", summary: "Dense notes, references, and open questions in one card.", accent: "#2563eb" },
  { title: "Northwind", category: "Commerce", summary: "A product teaser with enough structure for catalogue browsing.", accent: "#0f766e" },
  { title: "Signal Room", category: "Operations", summary: "Metrics and ownership should still feel quiet at scale.", accent: "#9333ea" },
  { title: "Amber Draft", category: "Editorial", summary: "A story card with room for title, deck, and routing metadata.", accent: "#ea580c" },
  { title: "Mint Ledger", category: "Finance", summary: "Stable dimensions make grids predictable for dashboards too.", accent: "#059669" },
  { title: "Violet Tape", category: "Archive", summary: "Long collections stay light when only the viewport is rendered.", accent: "#7c3aed" },
];

const tiles: readonly Tile[] = Array.from({ length: 180 }, (_, index) => ({
  ...catalog[index % catalog.length],
  title: `${catalog[index % catalog.length].title} ${index + 1}`,
}));

export function controlsDataGridPage(): void {
  const items = listProperty<Tile>([...tiles]);
  const selectedIndex = property(-1);

  div(() => {
    classes("flex", "flex-col", "gap-4");
    div(() => {
      style("height", "520px");
      dataGrid(
        items,
        (item, index) => {
          div(() => {
            classes("data-grid-showcase-card", "flex", "flex-col", "gap-3");
            classIf("data-grid-showcase-card--selected", selectedIndex.map((selected) => selected === index));
            style("height", "100%");
            style("padding", "16px");
            style("border-radius", "8px");
            style("cursor", item === null ? "default" : "pointer");
            attr("role", "button");
            attr("tabindex", "0");
            if (item !== null) onClick(() => selectedIndex.set(index));

            div(() => {
              style("height", "4px");
              style("border-radius", "999px");
              style("background", item?.accent ?? "var(--aj-line)");
            });
            div(() => {
              classes("text-xs", "font-bold", "text-ink-muted");
              text(item?.category ?? translated("Loading...").get);
            });
            div(() => {
              classes("text-lg", "font-bold");
              text(item?.title ?? `${translated("Loading tile").get} ${index + 1}`);
            });
            div(() => {
              classes("text-ink-soft");
              text(item?.summary ?? translated("Loading nearby range...").get);
            });
          });
        },
        {
          itemWidthPx: 240,
          itemHeightPx: 196,
          gapPx: 16,
          overscanRows: 1,
          prefetchItems: 24,
          crawlable: true,
          crawlId: "showcase-tiles",
          header: () => text(translated("180 cards · only the visible rows are mounted")),
          emptyPlaceholder: () => text(translated("No tiles.")),
        }
      );
    });
    div(() => {
      classes("showcase-result");
      text(selectedIndex.map((index) => index < 0
        ? translated("Select a card to inspect its reactive state.").get
        : `${translated("Selected card").get} ${index + 1} / ${items.size}`));
    });
  });
}
