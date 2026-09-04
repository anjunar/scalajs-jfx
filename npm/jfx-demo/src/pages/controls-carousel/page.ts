import { classes, div, listProperty, text } from "@anjunar/jfx-core";
import { carousel } from "@anjunar/jfx-controls";

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

export function controlsCarouselPage(): void {
  const slides = listProperty<Album>([...albums]);
  div(() => {
    classes("h-56");
    carousel(
      slides,
      (album, index) => {
        div(() => {
          classes("p-8");
          div(() => {
            classes("text-lg", "font-semibold");
            text(`${index + 1}. ${album.title}`);
          });
          div(() => {
            classes("text-ink-soft");
            text(`${album.artist} — ${album.year}`);
          });
        });
      },
      { autoAdvanceMs: 3200, ssrShowAllStates: true }
    );
  });
}
