import {
  addClass,
  anchor,
  article,
  attr,
  button,
  classes,
  domProperty,
  element,
  hbox,
  li,
  on,
  onClick,
  onDoubleClick,
  paragraph,
  property,
  section,
  self,
  span,
  style,
  text,
  ul,
} from "@anjunar/jfx-core";

const mark = element("mark");

export function coreElementsPage(): void {
  const highlighted = property(false);
  const doubleClicks = property(0);

  article(() => {
    classes("flex", "flex-col", "gap-3");

    section(() => {
      paragraph(() => {
        text("A ");
        span(() => {
          style("font-weight", "600");
          text("span");
        });
        text(" and a ");
        mark(() => {
          classes("px-1", "rounded-control", "cursor-pointer");
          addClass("core-elements-mark");
          domProperty("title", "Click to toggle");
          style("background-color", highlighted.map((value) => (value ? "var(--aj-accent-muted)" : "transparent")));
          on("click", () => highlighted.set(!highlighted.get));
          text("mark");
        });
        text(" -- element() builds both from the one-line pattern div/span/anchor are themselves built from.");
      });
    });

    hbox(() => {
      classes("gap-3", "items-center");
      anchor(() => {
        attr("href", "https://developer.mozilla.org/docs/Web/HTML/Element/a");
        text("anchor()");
      });
      button("Disable me", {}, () => {
        classes("px-3", "py-1.5");
        // Captured during render, then referenced (not re-called) inside the
        // handler -- self() itself only resolves while a scope is active,
        // which a later click is not. See core-lifecycle for the general form.
        const handle = self();
        onClick(() => {
          handle.setDomProperty("disabled", true);
        });
      });
      button(doubleClicks.map((count) => `Double-click me (${count})`), {}, () => {
        classes("px-3", "py-1.5");
        onDoubleClick(() => doubleClicks.set(doubleClicks.get + 1));
      });
    });

    ul(() => {
      classes("list-disc", "pl-5");
      li(() => text("attr() sets a plain HTML attribute"));
      li(() => text("style() sets a plain CSS property, constant or reactive"));
      li(() => text("domProperty() sets a DOM property directly, once at composition time"));
      li(() => text("on() is the generic event entry point onClick/onInput/onDoubleClick are themselves built from"));
      li(() => text("addClass() adds one class without touching whatever classes() already set"));
    });
  });
}
