import { classes, div, listProperty, text } from "@anjunar/jfx-core";
import { column, tableView } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

interface Album {
  readonly title: string;
  readonly artist: string;
  readonly year: number;
}

const albums: readonly Album[] = [
  { title: "Kind of Blue", artist: "Miles Davis", year: 1959 },
  { title: "Blue Train", artist: "John Coltrane", year: 1957 },
  { title: "The Köln Concert", artist: "Keith Jarrett", year: 1975 },
  { title: "Head Hunters", artist: "Herbie Hancock", year: 1973 },
  { title: "Speak No Evil", artist: "Wayne Shorter", year: 1966 },
];

export function controlsTablePage(): void {
  const rows = listProperty<Album>([...albums]);
  div(() => {
    classes("h-80");
    tableView(
      rows,
      [
        column(translated("Title").get, (album) => text(album.title), { prefWidth: 260, sortable: true, sortKey: "title" }),
        column(translated("Artist").get, (album) => text(album.artist), { prefWidth: 220 }),
        column(translated("Year").get, (album) => text(String(album.year)), { prefWidth: 90 }),
      ],
      { rowHeight: 40, crawlable: true, crawlId: "albums" }
    );
  });
}
