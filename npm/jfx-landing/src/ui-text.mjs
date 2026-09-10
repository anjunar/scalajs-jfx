const de = {
  "Copy": "Kopieren", "Copied": "Kopiert",
  "Clipboard unavailable. Code selected; use your browser’s Copy command.": "Zwischenablage nicht verfügbar. Der Code ist markiert; nutze die Kopierfunktion deines Browsers.",
  "Loading runtime…": "Runtime wird geladen…", "Loading runtime": "Runtime wird geladen",
  "Hydrated · try Increment": "Hydriert · probiere Increment", "Hydrated": "Hydriert",
  "The existing server HTML is now interactive.": "Das vorhandene Server-HTML ist jetzt interaktiv.",
  "Retry live example": "Live-Beispiel erneut laden", "Runtime unavailable": "Runtime nicht verfügbar",
  "The runtime could not load. You can still read the code and open either full demo.": "Die Runtime konnte nicht geladen werden. Du kannst den Code weiterhin lesen und beide Demos öffnen.",
  "Selection applies to this page only: browser storage is unavailable.": "Auswahl gilt nur auf dieser Seite: Browserspeicher nicht verfügbar.",
  "Loading interactive TableView…": "Interaktive TableView wird geladen…",
  "Interactive: sort a header, select rows or resize a column.": "Interaktiv: Sortiere eine Spalte, wähle Zeilen aus oder ändere eine Spaltenbreite.",
  "The interactive TableView could not load; the server-rendered rows remain available.": "Die interaktive TableView konnte nicht geladen werden; die servergerenderten Zeilen bleiben verfügbar."
};
export function uiText(source, locale) { return locale === "de" ? de[source] ?? source : source; }
