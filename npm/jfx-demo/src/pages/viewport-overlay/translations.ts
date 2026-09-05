import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Overlay`, { de: "Overlay" }),
  catalogEntry(i18n`A positioned floating layer -- the primitive a combo box's dropdown is built from.`, { de: "Eine positionierte schwebende Ebene – das Grundelement für das ComboBox-Dropdown." }),
  catalogEntry(i18n`overlay(): a positioned floating layer -- the same primitive a combo box's dropdown is built from.`, { de: "overlay(): eine positionierte schwebende Ebene – dasselbe Grundelement wie beim ComboBox-Dropdown." }),
  catalogEntry(i18n`Menu`, { de: "Menü" }),
  catalogEntry(i18n`Choose me`, { de: "Auswählen" }),
  catalogEntry(i18n`Menu item chosen.`, { de: "Menüeintrag ausgewählt." }),
];
