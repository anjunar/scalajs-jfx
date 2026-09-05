import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Extending the DSL`, { de: "DSL erweitern" }),
  catalogEntry(i18n`element() builds a tag wrapper in one line; attr/style/domProperty/on/onDoubleClick/addClass/self are what every wrapper is made from.`, { de: "element() baut einen Tag-Wrapper in einer Zeile; attr, style, domProperty, on, onDoubleClick, addClass und self bilden jeden Wrapper." }),
  catalogEntry(i18n`element() builds a tag wrapper in one line; attr/style/domProperty/on/addClass/self are the settings every wrapper is made from.`, { de: "element() baut einen Tag-Wrapper in einer Zeile; attr, style, domProperty, on, addClass und self bilden jeden Wrapper." }),
  catalogEntry(i18n`A `, { de: "Ein " }),
  catalogEntry(i18n`span`, { de: "span" }),
  catalogEntry(i18n` and a `, { de: " und ein " }),
  catalogEntry(i18n`Click to toggle`, { de: "Zum Umschalten klicken" }),
  catalogEntry(i18n`mark`, { de: "mark" }),
  catalogEntry(i18n` -- element() builds both from the one-line pattern div/span/anchor are themselves built from.`, { de: " – element() erstellt beide nach demselben Muster; div/span/anchor basieren ebenfalls darauf." }),
  catalogEntry(i18n`anchor()`, { de: "anchor()" }),
  catalogEntry(i18n`Disable me`, { de: "Deaktivieren" }),
  catalogEntry(i18n`attr() sets a plain HTML attribute`, { de: "attr() setzt ein einfaches HTML-Attribut" }),
  catalogEntry(i18n`style() sets a plain CSS property, constant or reactive`, { de: "style() setzt eine einfache CSS-Eigenschaft, konstant oder reaktiv" }),
  catalogEntry(i18n`domProperty() sets a DOM property directly, once at composition time`, { de: "domProperty() setzt direkt beim Komponieren eine DOM-Eigenschaft" }),
  catalogEntry(i18n`on() is the generic event entry point onClick/onInput/onDoubleClick are themselves built from`, { de: "on() ist der allgemeine Ereigniseinstieg; onClick/onInput/onDoubleClick basieren darauf" }),
  catalogEntry(i18n`addClass() adds one class without touching whatever classes() already set`, { de: "addClass() ergänzt eine Klasse, ohne bestehende classes() zu verändern" }),
];
