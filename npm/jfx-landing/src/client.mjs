import "./style.css";
import { mountPresentation } from "./presentation.mjs";

import { createPreferences } from "@anjunar/scalajs-jfx/preferences";
import { uiText } from "./ui-text.mjs";
const t = source => uiText(source, document.documentElement.lang);
mountPresentation(document, () => {
  document.querySelector('#view-status').textContent = t('Selection applies to this page only: browser storage is unavailable.');
});
const header = document.querySelector('.site-header');
const menu = document.querySelector('.header-menu');
const measureHeader = () => {
  document.documentElement.style.setProperty('--landing-header-height', `${header.getBoundingClientRect().height}px`);
};
measureHeader();
const headerObserver = new ResizeObserver(measureHeader);
headerObserver.observe(header);
window.addEventListener('pagehide', event => { if (!event.persisted) headerObserver.disconnect(); });
menu.addEventListener('keydown', event => {
  if (event.key === 'Escape' && menu.open) {
    menu.open = false;
    menu.querySelector('summary').focus();
  }
});
document.addEventListener('click', event => {
  if (!menu.contains(event.target)) menu.open = false;
});
menu.addEventListener('focusout', event => {
  if (event.relatedTarget && !menu.contains(event.relatedTarget)) menu.open = false;
});
for (const anchor of menu.querySelectorAll('.section-links a')) anchor.addEventListener('click', event => {
  if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  menu.open = false;
  const target = document.getElementById(anchor.dataset.pageAnchor);
  target.setAttribute('tabindex', '-1');
  target.focus({ preventScroll: true });
});
const preferences = createPreferences("scalajs-jfx.theme", location.href);
const design = document.querySelector("#design-choice");
const scheme = document.querySelector("#scheme-choice");
const language = document.querySelector("#language-choice");
const demoLinks = [...document.querySelectorAll('a[href]')]
  .filter(anchor => /^\.\/(scala|typescript)\//.test(anchor.getAttribute('href')));
for (const control of [design, scheme, language]) control.disabled = false;
const unsubscribe = preferences.subscribe(state => {
  design.value = state.design;
  scheme.value = state.colorScheme;
  for (const anchor of demoLinks) {
    const url = new URL(anchor.href);
    url.searchParams.set("design", state.design);
    url.searchParams.set("colorScheme", state.colorScheme);
    anchor.href = url.href;
  }
  for (const anchor of document.querySelectorAll("[data-page-anchor]")) {
    const url = new URL(location.href);
    url.hash = anchor.dataset.pageAnchor;
    anchor.href = url.href;
  }
  document.querySelector("#preference-status").textContent = state.storageAvailable ? "" : t("Selection applies to this page only: browser storage is unavailable.");
});
window.addEventListener("pagehide", event => { if (!event.persisted) unsubscribe(); });
design.addEventListener("change", () => preferences.setDesign(design.value));
scheme.addEventListener("change", () => preferences.setColorScheme(scheme.value));
language.addEventListener("change", () => {
  const url = new URL(`./${language.value === "de" ? "de" : "en"}/`, document.baseURI);
  url.search = location.search;
  url.hash = location.hash;
  // Explicit URL state also preserves appearance when browser storage is unavailable.
  const state = preferences.getState();
  url.searchParams.set("design", state.design);
  url.searchParams.set("colorScheme", state.colorScheme);
  location.assign(url.href);
});

for (const button of document.querySelectorAll("[data-copy]")) {
  button.hidden = false;
  button.addEventListener("click", async () => {
    const code = document.getElementById(button.dataset.copy);
    try {
      await navigator.clipboard.writeText(code.textContent);
      document.querySelector("#copy-status").textContent = `${button.dataset.label}: ${t("Copied")}`;
      button.textContent = t("Copied");
      button.dataset.state = "copied";
      setTimeout(() => { button.textContent = t("Copy"); delete button.dataset.state; }, 1800);
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(code);
      selection.removeAllRanges();
      selection.addRange(range);
      document.querySelector("#copy-status").textContent = t("Clipboard unavailable. Code selected; use your browser’s Copy command.");
    }
  });
}

const activate = document.querySelector("#activate-counter");
const liveProof = document.querySelector("#live-proof");
const proofStatus = document.querySelector("#proof-status");
activate.hidden = false;
activate.addEventListener("click", async () => {
  activate.disabled = true;
  document.querySelector("#counter-fieldset").setAttribute("aria-busy", "true");
  activate.textContent = t("Loading runtime…");
  liveProof.dataset.state = "loading";
  proofStatus.textContent = t("Loading runtime");
  try {
    const { activateCounter } = await import("./hydrate.mjs");
    await activateCounter();
    document.querySelector("#counter-fieldset").disabled = false;
    activate.textContent = t("Hydrated · try Increment");
    liveProof.dataset.state = "hydrated";
    proofStatus.textContent = t("Hydrated");
    document.querySelector("#runtime-status").textContent = t("The existing server HTML is now interactive.");
  } catch (error) {
    activate.disabled = false;
    activate.textContent = t("Retry live example");
    liveProof.dataset.state = "error";
    proofStatus.textContent = t("Runtime unavailable");
    document.querySelector("#runtime-status").textContent = t("The runtime could not load. You can still read the code and open either full demo.");
    console.error("Landing counter hydration failed", error);
  } finally {
    document.querySelector("#counter-fieldset").setAttribute("aria-busy", "false");
  }
});
