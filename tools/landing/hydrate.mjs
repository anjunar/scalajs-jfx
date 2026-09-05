import "@anjunar/scalajs-jfx-bridge";
import { hydrate } from "@anjunar/jfx-core";
import { counter } from "./counter.mjs";

export async function activateCounter() {
  await hydrate(document.querySelector("#counter-root"), counter);
}
