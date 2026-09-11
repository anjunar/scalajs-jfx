import "@anjunar/scalajs-ui-bridge";
import { hydrate } from "@anjunar/scalajs-ui-core";
import { counter } from "./counter.mjs";
import { projectTable } from "./table-preview.mjs";

export async function activateCounter() {
  await hydrate(document.querySelector("#counter-root"), counter);
}

export async function activateProjectTable() {
  await hydrate(document.querySelector("#table-root"), projectTable);
}
