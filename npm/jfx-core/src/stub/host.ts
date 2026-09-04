/**
 * A minimal host abstraction for the stub runtime.
 *
 * It mirrors `jfx.core.render.HostElement` / `HostNode` closely enough that the
 * declarative layer above cannot tell the difference, and shallowly enough that
 * it stays a test double. The production runtime is the Scala.js bundle.
 */

export interface HostNode {
  readonly kind: "element" | "text" | "comment";
}

export interface HostElement extends HostNode {
  readonly kind: "element";
  readonly tagName: string;
  setAttribute(name: string, value: string): void;
  removeAttribute(name: string): void;
  getAttribute(name: string): string | null;
  setStyle(name: string, value: string): void;
  removeStyle(name: string): void;
  setClassNames(names: readonly string[]): void;
  setDomProperty(name: string, value: unknown): void;
  insertBefore(child: HostNode, reference: HostNode | null): void;
  removeChild(child: HostNode): void;
  addEventListener(name: string, handler: (event: Event) => void): () => void;
}

export interface HostText extends HostNode {
  readonly kind: "text";
  setText(value: string): void;
}

export interface HostComment extends HostNode {
  readonly kind: "comment";
}

export interface HostDocument {
  readonly isBrowser: boolean;
  createElement(tagName: string): HostElement;
  createText(value: string): HostText;
  createComment(value: string): HostComment;
}

/* ----------------------------------------------------------------------- SSR */

const VOID_ELEMENTS = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input",
  "link", "meta", "source", "track", "wbr",
]);

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export class SsrElement implements HostElement {
  readonly kind = "element" as const;
  private readonly attributes = new Map<string, string>();
  private readonly styles = new Map<string, string>();
  private classNames: readonly string[] = [];
  readonly children: HostNode[] = [];

  constructor(readonly tagName: string) {}

  setAttribute(name: string, value: string): void {
    this.attributes.set(name, value);
  }
  removeAttribute(name: string): void {
    this.attributes.delete(name);
  }
  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null;
  }
  setStyle(name: string, value: string): void {
    this.styles.set(name, value);
  }
  removeStyle(name: string): void {
    this.styles.delete(name);
  }
  setClassNames(names: readonly string[]): void {
    this.classNames = names;
  }
  setDomProperty(): void {
    // A DOM property has no server-side representation. The real SSR host
    // ignores it too; hydration installs it.
  }
  insertBefore(child: HostNode, reference: HostNode | null): void {
    const index = reference === null ? this.children.length : this.children.indexOf(reference);
    this.children.splice(index < 0 ? this.children.length : index, 0, child);
  }
  removeChild(child: HostNode): void {
    const index = this.children.indexOf(child);
    if (index >= 0) this.children.splice(index, 1);
  }
  addEventListener(): () => void {
    return () => {};
  }

  renderHtml(): string {
    const parts: string[] = [];
    if (this.classNames.length > 0) {
      parts.push(` class="${escapeHtml(this.classNames.join(" "))}"`);
    }
    for (const [name, value] of this.attributes) {
      parts.push(value === "" ? ` ${name}` : ` ${name}="${escapeHtml(value)}"`);
    }
    if (this.styles.size > 0) {
      const declarations = [...this.styles]
        .map(([name, value]) => `${name}: ${value}`)
        .join("; ");
      parts.push(` style="${escapeHtml(declarations)}"`);
    }

    const open = `<${this.tagName}${parts.join("")}>`;
    if (VOID_ELEMENTS.has(this.tagName)) return open;
    return `${open}${renderChildren(this.children)}</${this.tagName}>`;
  }
}

export class SsrText implements HostText {
  readonly kind = "text" as const;
  constructor(private value: string) {}
  setText(value: string): void {
    this.value = value;
  }
  renderHtml(): string {
    return escapeHtml(this.value);
  }
}

export class SsrComment implements HostComment {
  readonly kind = "comment" as const;
  constructor(private readonly value: string) {}
  renderHtml(): string {
    return `<!--${this.value}-->`;
  }
}

export function renderChildren(children: readonly HostNode[]): string {
  return children
    .map((child) => {
      if (child instanceof SsrElement) return child.renderHtml();
      if (child instanceof SsrText) return child.renderHtml();
      if (child instanceof SsrComment) return child.renderHtml();
      return "";
    })
    .join("");
}

export class SsrDocument implements HostDocument {
  readonly isBrowser = false;
  createElement(tagName: string): HostElement {
    return new SsrElement(tagName);
  }
  createText(value: string): HostText {
    return new SsrText(value);
  }
  createComment(value: string): HostComment {
    return new SsrComment(value);
  }
}

/* ----------------------------------------------------------------------- DOM */

interface Wrapped {
  readonly node: Node;
}

function unwrap(node: HostNode | null): Node | null {
  return node === null ? null : (node as unknown as Wrapped).node;
}

export class DomElement implements HostElement, Wrapped {
  readonly kind = "element" as const;
  constructor(readonly node: Element) {}
  get tagName(): string {
    return this.node.tagName.toLowerCase();
  }
  setAttribute(name: string, value: string): void {
    this.node.setAttribute(name, value);
  }
  removeAttribute(name: string): void {
    this.node.removeAttribute(name);
  }
  getAttribute(name: string): string | null {
    return this.node.getAttribute(name);
  }
  setStyle(name: string, value: string): void {
    (this.node as HTMLElement).style.setProperty(name, value);
  }
  removeStyle(name: string): void {
    (this.node as HTMLElement).style.removeProperty(name);
  }
  setClassNames(names: readonly string[]): void {
    this.node.setAttribute("class", names.join(" "));
  }
  setDomProperty(name: string, value: unknown): void {
    (this.node as unknown as Record<string, unknown>)[name] = value;
  }
  insertBefore(child: HostNode, reference: HostNode | null): void {
    this.node.insertBefore(unwrap(child)!, unwrap(reference));
  }
  removeChild(child: HostNode): void {
    const node = unwrap(child);
    if (node !== null && node.parentNode === this.node) this.node.removeChild(node);
  }
  addEventListener(name: string, handler: (event: Event) => void): () => void {
    this.node.addEventListener(name, handler);
    return () => this.node.removeEventListener(name, handler);
  }
}

export class DomText implements HostText, Wrapped {
  readonly kind = "text" as const;
  constructor(readonly node: Text) {}
  setText(value: string): void {
    this.node.data = value;
  }
}

export class DomComment implements HostComment, Wrapped {
  readonly kind = "comment" as const;
  constructor(readonly node: Comment) {}
}

export class DomDocument implements HostDocument {
  readonly isBrowser = true;
  constructor(private readonly document: Document) {}
  createElement(tagName: string): HostElement {
    return new DomElement(this.document.createElement(tagName));
  }
  createText(value: string): HostText {
    return new DomText(this.document.createTextNode(value));
  }
  createComment(value: string): HostComment {
    return new DomComment(this.document.createComment(value));
  }
}
