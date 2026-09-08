import { parse, serialize } from "parse5";
import messages from "./messages.de.json" with { type: "json" };
export function translate(source) {
  if (Object.hasOwn(messages, source)) return messages[source];
  if (source.startsWith("Copy ")) return `${translate(source.slice(5))} kopieren`;
  return source.replace(" · choose your API", " · wähle deine API").replace(" · package ↗", " · Paket ↗");
}
export function localizePage(html, locale) {
  if (locale !== "de") return html;
  const document = parse(html);
  function visit(node, skip = false) {
    const attrs = Object.fromEntries((node.attrs ?? []).map(a => [a.name, a.value]));
    // Keep executable/displayed code and live SSR examples identical to their sources.
    const preserve = skip || ["script", "style", "pre", "code"].includes(node.tagName) || attrs.id === "counter-root" || ["jfx-form", "jfx-table-view", "jfx-editor"].some(c => attrs.class?.split(" ").includes(c));
    if (node.nodeName === "#text" && !preserve) {
      const source = node.value.trim();
      node.value = node.value.replace(source, translate(source));
    }
    for (const attr of node.attrs ?? []) {
      if (!preserve && ["aria-label", "title", "alt", "data-label", "content"].includes(attr.name)) attr.value = translate(attr.value);
      if (attr.name === "href" && /^\.\/(scala|typescript)\//.test(attr.value)) attr.value = attr.value.replace(/^(\.\/(?:scala|typescript)\/)/, "$1de/");
    }
    for (const child of node.childNodes ?? []) visit(child, preserve);
  }
  visit(document);
  return serialize(document);
}
