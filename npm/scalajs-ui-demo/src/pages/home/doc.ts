/** Keep title/summary in sync with the "/" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { homePage } from "./page.js";

export function homeDoc(): void {
  docPage(
    {
      title: "Build with TypeScript. Run on Scala JS UI.",
      summary: "The TypeScript API is a language-level entrance to the same reactive, SSR-capable runtime as the Scala.js DSL.",
      eyebrow: "Scala JS UI 1.0 · TypeScript",
      scalaPath: "/",
    },
    () => {
      homePage();
    }
  );
}
