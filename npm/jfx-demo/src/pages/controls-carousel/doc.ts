/** Keep title/summary in sync with the "/controls/carousel" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { controlsCarouselPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function controlsCarouselDoc(): void {
  docPage(
    {
      title: "Carousel",
      summary: "carousel(): a looping slide show with reactive activeIndex and autoAdvanceMs properties; ssrShowAllStates brings every slide into the initial HTML.",
    },
    () => {
      example({ code: snippet }, () => {
        controlsCarouselPage();
      });
    }
  );
}
