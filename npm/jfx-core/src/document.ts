import type { HeadEntry } from "./contract.js";

/**
 * Factories for {@link HeadEntry}. Pure data, no runtime crossing -- mirrors
 * `jfx.core.document.HeadEntry`'s companion object one-for-one so a page that
 * knows the Scala DSL already knows this one.
 *
 * `key` decides identity, not position: pushing the same key again replaces
 * the entry (see `DocumentHead.push` on the Scala side). The factories below
 * derive the key from whatever makes the tag unique -- the `name` of a meta,
 * the `rel` of a link -- exactly like their Scala counterparts.
 */

export function title(value: string): HeadEntry {
  return { key: "title", tagName: "title", text: value };
}

export function charset(value: string = "UTF-8"): HeadEntry {
  return { key: "meta:charset", tagName: "meta", attributes: [["charset", value]] };
}

export function base(href: string): HeadEntry {
  return { key: "base", tagName: "base", attributes: [["href", href]] };
}

export function meta(name: string, content: string): HeadEntry {
  return {
    key: `meta:name=${name}`,
    tagName: "meta",
    attributes: [
      ["name", name],
      ["content", content],
    ],
  };
}

/** `<meta property="…">` -- the Open Graph form, which uses `property` rather than `name`. */
export function metaProperty(property: string, content: string): HeadEntry {
  return {
    key: `meta:property=${property}`,
    tagName: "meta",
    attributes: [
      ["property", property],
      ["content", content],
    ],
  };
}

export function link(
  rel: string,
  href: string,
  ...attributes: readonly (readonly [string, string])[]
): HeadEntry {
  return {
    key: `link:${rel}`,
    tagName: "link",
    attributes: [["rel", rel], ["href", href], ...attributes],
  };
}

/** `<link rel="alternate" hreflang="…">` -- keyed by language, because a document has one per
 * translation and they must not replace each other. */
export function alternate(hreflang: string, href: string): HeadEntry {
  return {
    key: `link:alternate:${hreflang}`,
    tagName: "link",
    attributes: [
      ["rel", "alternate"],
      ["hreflang", hreflang],
      ["href", href],
    ],
  };
}

export function script(
  key: string,
  src: string,
  ...attributes: readonly (readonly [string, string])[]
): HeadEntry {
  return { key, tagName: "script", attributes: [["src", src], ...attributes] };
}

export function inlineScript(key: string, source: string): HeadEntry {
  return { key, tagName: "script", text: source, rawText: true };
}

export function jsonLd(key: string, json: string): HeadEntry {
  return {
    key,
    tagName: "script",
    attributes: [["type", "application/ld+json"]],
    text: escapeClosingTag(json),
    rawText: true,
  };
}

/** Raw text ends at the first `</script`, wherever it appears -- inside a JSON string as well.
 * `<\/` is the escape JSON itself allows, so the payload stays valid and the element stays
 * closed where it should be. */
function escapeClosingTag(value: string): string {
  return value.replace(/<\//g, "<\\/");
}
