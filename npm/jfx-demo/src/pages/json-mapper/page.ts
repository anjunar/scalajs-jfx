import { button, classes, div, element, heading, hbox, onClick, property, listProperty, text } from "@anjunar/jfx-core";
import { JsonId, JsonMapper, JsonProperty } from "@anjunar/jfx-json";
import type { ListProperty, Property } from "@anjunar/jfx-core";
import { translated } from "../../app/i18n.js";

class Profile {
  @JsonId readonly id: Property<string> = property("");
  @JsonProperty("displayName") readonly name: Property<string> = property("");
  @JsonProperty("roles") readonly roles: ListProperty<string> = listProperty<string>([]);
}

export function jsonMapperPage(): void {
  const profile = new Profile();
  profile.id.set("user-42");
  profile.name.set("Ada Lovelace");
  profile.roles.add("admin");

  const output = property("");
  const status = property("");
  const refresh = (message = ""): void => {
    output.set(JSON.stringify(JsonMapper.serialize(profile), null, 2));
    status.set(message);
  };
  const loadSample = (): void => {
    const restored = JsonMapper.deserialize(
      { id: "user-7", displayName: "Grace Hopper", roles: ["author", "reviewer"] },
      Profile,
    );
    if (!(restored instanceof Profile)) {
      throw new Error("JsonMapper returned the wrong model class");
    }
    profile.id.set(restored.id.get);
    profile.name.set(restored.name.get);
    profile.roles.setAll(restored.roles.get);
    refresh("Sample deserialized into Profile");
  };
  refresh();

  div(() => {
    classes("flex", "flex-col", "gap-3");
    heading(2, () => text(translated("Schema-driven JSON mapping")));
    text(translated("Decorators name displayName, keep id as an identifier and map a ListProperty. The mapper infers the class schema at runtime."));
    hbox(() => {
      classes("gap-2");
      button(translated("Serialize current model"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(() => refresh("Current Profile serialized"));
      });
      button(translated("Deserialize sample"), {}, () => {
        classes("px-3", "py-1.5");
        onClick(loadSample);
      });
    });
    text(status);
    element("pre")(() => {
      classes("rounded-control", "p-3", "overflow-auto", "bg-surface-muted");
      text(output);
    });
  });
}
