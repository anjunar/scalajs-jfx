import "./style.css";

const root = document.documentElement;
const themeButton = document.querySelector("#theme-toggle");
const preference = matchMedia("(prefers-color-scheme: dark)");
let chosenTheme;
try { chosenTheme = localStorage.getItem("scalajs-jfx.theme"); } catch { /* Storage is optional. */ }
const isTheme = value => value === "dark" || value === "light";

function applyTheme(mode) {
  root.dataset.theme = mode;
  themeButton.setAttribute("aria-pressed", String(mode === "dark"));
  themeButton.setAttribute("aria-label", mode === "dark" ? "Switch to light theme" : "Switch to dark theme");
}
applyTheme(isTheme(chosenTheme) ? chosenTheme : preference.matches ? "dark" : "light");
themeButton.hidden = false;
themeButton.addEventListener("click", () => {
  chosenTheme = root.dataset.theme === "dark" ? "light" : "dark";
  applyTheme(chosenTheme);
  try { localStorage.setItem("scalajs-jfx.theme", chosenTheme); } catch { /* Keep the in-memory choice. */ }
});
preference.addEventListener("change", event => {
  if (!isTheme(chosenTheme)) applyTheme(event.matches ? "dark" : "light");
});

for (const button of document.querySelectorAll("[data-copy]")) {
  button.hidden = false;
  button.addEventListener("click", async () => {
    const code = document.getElementById(button.dataset.copy);
    try {
      await navigator.clipboard.writeText(code.textContent);
      document.querySelector("#copy-status").textContent = `${button.dataset.label} copied.`;
      button.textContent = "Copied";
      setTimeout(() => { button.textContent = "Copy"; }, 1800);
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(code);
      selection.removeAllRanges();
      selection.addRange(range);
      document.querySelector("#copy-status").textContent = "Clipboard unavailable. Code selected; use your browser’s Copy command.";
    }
  });
}

const activate = document.querySelector("#activate-counter");
activate.hidden = false;
activate.addEventListener("click", async () => {
  activate.disabled = true;
  activate.textContent = "Loading runtime…";
  try {
    const { activateCounter } = await import("./hydrate.mjs");
    await activateCounter();
    document.querySelector("#counter-fieldset").disabled = false;
    activate.textContent = "Hydrated · try Increment";
    document.querySelector("#runtime-status").textContent = "The existing server HTML is now interactive.";
  } catch (error) {
    activate.disabled = false;
    activate.textContent = "Retry live example";
    document.querySelector("#runtime-status").textContent = "The runtime could not load. You can still read the code and open either full demo.";
    console.error("Landing counter hydration failed", error);
  }
});
