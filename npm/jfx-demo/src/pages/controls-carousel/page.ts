import { button, classes, div, listProperty, onClick, property, style, text } from "@anjunar/jfx-core";
import { carousel } from "@anjunar/jfx-controls";
import { translated } from "../../app/i18n.js";

interface Slide {
  readonly kicker: string;
  readonly title: string;
  readonly copy: string;
  readonly accent: string;
}

const slideCatalog: readonly Slide[] = [
  { kicker: "Atlas", title: "Architecture that keeps moving", copy: "The carousel owns the visible sequence while the slide renderer stays declarative.", accent: "#2563eb" },
  { kicker: "Signal", title: "Auto-advance without hidden magic", copy: "A lifecycle-bound timer rotates through the same explicit slide collection.", accent: "#0f766e" },
  { kicker: "Northwind", title: "SSR can surface every state", copy: "Stable dynamic ranges keep the server and hydration structure aligned.", accent: "#ea580c" },
  { kicker: "Harbor", title: "Wrap-around is part of the contract", copy: "The step after the right edge returns to the beginning.", accent: "#7c3aed" },
];

export function controlsCarouselPage(): void {
  const slides = listProperty<Slide>([...slideCatalog]);
  const activeIndex = property(0);
  const autoAdvanceMs = property(2600);
  const move = (delta: number): void => {
    activeIndex.set((activeIndex.get + delta + slides.size) % slides.size);
  };

  div(() => {
    classes("flex", "flex-col", "gap-4");

    div(() => {
      classes("carousel-showcase-frame");
      carousel(
        slides,
        (slide, index) => {
          div(() => {
            classes("carousel-demo-slide");
            style("min-height", "320px");
            style("padding", "28px");
            div(() => {
              classes("carousel-demo-slide__kicker");
              text(slide.kicker);
            });
            div(() => {
              classes("carousel-demo-slide__title");
              text(`${index + 1}. ${slide.title}`);
            });
            div(() => {
              classes("carousel-demo-slide__copy");
              text(slide.copy);
            });
            div(() => {
              classes("carousel-demo-slide__footer");
              div(() => {
                classes("carousel-demo-slide__pill");
                style("background", slide.accent);
                text(translated("State"));
              });
              div(() => {
                classes("carousel-demo-slide__accent");
                style("color", slide.accent);
                text(translated("Looping sequence"));
              });
            });
          });
        },
        { activeIndex, autoAdvanceMs, ssrShowAllStates: true }
      );
    });

    div(() => {
      classes("showcase-action-row");
      button(translated("Previous"), {}, () => onClick(() => move(-1)));
      button(translated("Next"), {}, () => onClick(() => move(1)));
      button(translated("Fast autoplay"), {}, () => onClick(() => autoAdvanceMs.set(1400)));
      button(translated("Slow autoplay"), {}, () => onClick(() => autoAdvanceMs.set(3400)));
      button(translated("Stop timer"), {}, () => onClick(() => autoAdvanceMs.set(0)));
    });

    div(() => {
      classes("showcase-result");
      div(() => text(activeIndex.map((index) => `${translated("Selected slide").get}: ${index + 1} / ${slides.size}`)));
      div(() => text(autoAdvanceMs.map((milliseconds) => `autoAdvanceMs = ${milliseconds}`)));
    });
  });
}
