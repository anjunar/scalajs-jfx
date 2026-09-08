import { attr, classes, div, disposeWith, domProperty, element, isBrowser, on, property, self, text, type ReadOnlyProperty } from "@anjunar/jfx-core";
import { createPreferences, designs, serverPreferences } from "@anjunar/scalajs-jfx/preferences";
import { translated } from "./i18n.js";
const label = element("label"), select = element("select"), option = element("option"), status = element("span");

/** Each shell owns its preferences and subscriptions, including during SSR. */
export function preferenceControls(url: string): void {
  const preferences = createPreferences("jfx-demo.theme", url);
  const initial = serverPreferences(url);
  div(() => {
    classes("preferences");
    choice("design-choice", "Design", "design", designs.map(d => [d.id, d.name]), preferences.setDesign);
    choice("scheme-choice", "Appearance", "colorScheme", [["light", translated("Light")], ["dark", translated("Dark")]], value => preferences.setColorScheme(value === "dark" ? "dark" : "light"));
    status(() => {
      classes("preferences__status");
      attr("role", "status");
      const message = translated("Selection applies to this page only: browser storage is unavailable.");
      const notice = property("");
      let available = true;
      disposeWith({ dispose: preferences.subscribe(state => { available = state.storageAvailable; notice.set(available ? "" : message.get); }) });
      disposeWith(message.observeWithoutInitial(value => notice.set(available ? "" : value)));
      text(notice);
    });
  });
  function choice(id: string, title: string, axis: "design" | "colorScheme", options: readonly (readonly [string, string | ReadOnlyProperty<string>])[], change: (value: string) => void): void {
    label(() => {
      attr("for", id);
      status(() => { classes("preferences__label"); text(translated(title)); });
      select(() => {
        attr("id", id);
        attr("disabled", "");
        for (const [value, title] of options) option(() => {
          attr("value", value);
          if (value === initial[axis]) attr("selected", "");
          text(title);
        });
        on("change", event => change((event.target as HTMLSelectElement).value));
        if (isBrowser()) {
          domProperty("disabled", false);
          const control = self();
          disposeWith({ dispose: preferences.subscribe(state => control.setDomProperty("value", state[axis])) });
        }
      });
    });
  }
}
