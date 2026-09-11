import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { $getRoot, PASTE_COMMAND, UNDO_COMMAND, REDO_COMMAND, type LexicalEditor } from "lexical";
import { installRuntime, mount, property, renderToString, resetRuntime, hydrate } from "@anjunar/scalajs-ui-core";
import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";
import { form } from "@anjunar/scalajs-ui-forms";
import { viewport } from "@anjunar/scalajs-ui-viewport";
import { editor, type EditorOptions, type UploadedMediaReference } from "../src/index.js";

const disposers: (() => void)[] = [];
beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
});
afterEach(() => {
  disposers.splice(0).reverse().forEach(dispose => dispose());
  document.body.replaceChildren();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function setup(markdown = "Before", options: EditorOptions = {}) {
  const root = document.createElement("div");
  document.body.append(root);
  const model = { body: property(markdown) };
  const app = mount(root, () => viewport(() => form(model, {}, () =>
    editor("body", { plugins: ["image"], ...options }))));
  disposers.push(() => app.dispose());
  const surface = root.querySelector(".scalajs-ui-editor__surface") as HTMLElement & { __lexicalEditor: LexicalEditor };
  const lexical = surface.__lexicalEditor;
  lexical.update(() => $getRoot().selectEnd(), { discrete: true });
  return { root, surface, lexical, model, app };
}

function transfer(files: File[] = [], html = "", text = "") {
  return {
    files: { length: files.length, item: (index: number) => files[index] ?? null },
    items: files.map(file => ({ kind: "file", type: file.type, getAsFile: () => file })),
    types: files.length ? ["Files", "text/html", "text/plain"] : ["text/html", "text/plain"],
    getData: (type: string) => type === "text/html" ? html : type === "text/plain" ? text : "",
    setData: vi.fn(),
  };
}

function paste(lexical: LexicalEditor, data: ReturnType<typeof transfer>) {
  const event = new Event("paste", { bubbles: true, cancelable: true });
  Object.defineProperty(event, "clipboardData", { value: data });
  lexical.dispatchCommand(PASTE_COMMAND, event as ClipboardEvent);
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("media references", () => {
  it("exports images after a real edit, including title and explicit 680px", async () => {
    const { lexical, model, root } = setup('Before\n\n![Katze](/media/cat.webp "Garten"){width=680}');
    lexical.update(() => $getRoot().getAllTextNodes()[0]!.setTextContent("After"), { discrete: true });
    await vi.waitFor(() => expect(model.body.get).toContain('![Katze](/media/cat.webp "Garten"){width=680}'));
    expect(model.body.get).toContain("After");
    expect(model.body.get).not.toContain("Some(");
    expect(root.querySelector("img")?.getAttribute("width")).toBe("680");
    expect(root.querySelector("img")?.getAttribute("title")).toBe("Garten");
    const json = lexical.getEditorState().toJSON();
    const clone = lexical.parseEditorState(JSON.stringify(json));
    expect(clone.toJSON()).toEqual(json);
  });

  it("resolves stable IDs without adding proprietary ID markup", () => {
    const { lexical } = setup("![Cat](/media/4711)", {
      mediaUrlPolicy: { resolve: src => src.startsWith("/media/") ? { src, mediaId: src.slice(7) } : null },
    });
    expect(JSON.stringify(lexical.getEditorState().toJSON())).toContain('"mediaId":"4711"');
  });

  it("discards existing Base64 from the bound value and keeps other content", () => {
    const { model, root } = setup("Before ![old](data:image/png;base64,YQ==) after");
    expect(model.body.get).toBe("Before  after");
    expect(root.querySelector("img")).toBeNull();
  });

  it("rejects external and blob images in JSON import", () => {
    const { lexical } = setup("![Cat](/media/cat.webp)");
    for (const src of ["https://host/cat.png", "//host/cat.png", "blob:abc", "data:image/png;base64,YQ=="]) {
      const json = JSON.stringify(lexical.getEditorState().toJSON()).replace("/media/cat.webp", src);
      // Lexical reports import errors through its onError callback; no invalid image is retained.
      const error = vi.spyOn(console, "error").mockImplementation(() => {});
      const state = lexical.parseEditorState(json);
      expect(JSON.stringify(state.toJSON())).not.toContain(src);
      expect(error).toHaveBeenCalled();
      error.mockRestore();
    }
  });

  it("commits resize to Markdown and supports undo/redo", async () => {
    const { root, lexical, model } = setup("![Cat](/media/cat.webp){width=320}");
    const handle = root.querySelector(".image-resizer")!;
    handle.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, clientX: 0 }));
    window.dispatchEvent(new MouseEvent("mousemove", { clientX: 100 }));
    window.dispatchEvent(new MouseEvent("mouseup", { clientX: 100 }));
    await vi.waitFor(() => expect(model.body.get).toContain("{width=420}"));
    lexical.dispatchCommand(UNDO_COMMAND, undefined);
    await vi.waitFor(() => expect(model.body.get).toContain("{width=320}"));
    lexical.dispatchCommand(REDO_COMMAND, undefined);
    await vi.waitFor(() => expect(model.body.get).toContain("{width=420}"));
  });

  it("does not resize a readonly image", () => {
    const { root, model } = setup("![Cat](/media/cat.webp){width=320}", { editable: false });
    root.querySelector(".image-resizer")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, clientX: 0 }));
    window.dispatchEvent(new MouseEvent("mousemove", { clientX: 100 }));
    window.dispatchEvent(new MouseEvent("mouseup", { clientX: 100 }));
    expect(model.body.get).toContain("{width=320}");
  });
});

describe("uploads", () => {
  it("imports internal HTML images with title and width, and rejects external images", async () => {
    const { lexical, model, root } = setup();
    paste(lexical, transfer([], '<p>Text <img src="/media/cat.png" alt="Cat" title="Garden" width="320"><img src="https://host/foreign.png"></p>'));
    await vi.waitFor(() => expect(root.querySelectorAll("img")).toHaveLength(1));
    expect(model.body.get).toContain('![Cat](/media/cat.png "Garden"){width=320}');
    expect(model.body.get).not.toContain("https://host");
    expect(model.body.get).toContain("Text");
  });

  it("sanitizes JSON clipboard image references without losing surrounding text", async () => {
    const { lexical, model } = setup();
    const data = transfer();
    data.getData = (mime: string) => mime === "application/x-lexical-editor" ? JSON.stringify({
      namespace: "body", nodes: [
        { type: "text", version: 1, text: "Copied ", format: 0, mode: "normal", style: "", detail: 0 },
        { type: "image", version: 2, src: "/media/cat.png", mediaId: "cat", altText: "Cat", widthPx: 320 },
        { type: "image", version: 2, src: "https://host/foreign.png", altText: "Foreign" },
      ],
    }) : "";
    paste(lexical, data);
    await vi.waitFor(() => expect(model.body.get).toContain("![Cat](/media/cat.png){width=320}"));
    expect(model.body.get).toContain("Copied");
    expect(model.body.get).not.toContain("https://host");
  });

  it("uploads an embedded HTML image once and keeps pasted text", async () => {
    const upload = vi.fn().mockResolvedValue({ src: "/media/cat.png", mediaId: "cat" });
    const fetchImage = vi.fn().mockResolvedValue({ blob: () => Promise.resolve(new Blob(["image"], { type: "image/png" })) });
    vi.stubGlobal("fetch", fetchImage);
    const { lexical, model, root } = setup("Before", { mediaUploader: { upload } });
    paste(lexical, transfer([], '<p>Caption<img src="data:image/png;base64,YQ=="></p>'));
    await vi.waitFor(() => expect(root.querySelectorAll("img")).toHaveLength(1));
    expect(upload).toHaveBeenCalledOnce();
    expect(model.body.get).toContain("Caption");
    expect(model.body.get).not.toMatch(/data:|blob:/);
  });

  it("keeps replacement text when a pasted image finishes uploading", async () => {
    const upload = vi.fn().mockResolvedValue({ src: "/media/cat.png", mediaId: "cat" });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ blob: () => Promise.resolve(new Blob(["image"], { type: "image/png" })) }));
    const { lexical, model, root } = setup("Replace", { mediaUploader: { upload } });
    lexical.update(() => $getRoot().getAllTextNodes()[0]!.select(0, 7), { discrete: true });
    paste(lexical, transfer([], '<p>Caption<img src="data:image/png;base64,YQ==" alt="Cat" width="320"></p>'));
    await vi.waitFor(() => expect(root.querySelectorAll("img")).toHaveLength(1));
    expect(model.body.get).toContain("Caption");
    expect(model.body.get).toContain("![Cat](/media/cat.png){width=320}");
    expect(model.body.get).not.toContain("Replace");
  });

  it("moves an internal image without uploading or duplicating it", async () => {
    vi.stubGlobal("DragEvent", Event);
    const upload = vi.fn();
    const { root, model } = setup("![Cat](/media/cat.png)\n\nLast", { mediaUploader: { upload } });
    const data = transfer();
    const start = new Event("dragstart", { bubbles: true, cancelable: true });
    Object.defineProperty(start, "dataTransfer", { value: data });
    root.querySelector("img")!.dispatchEvent(start);
    const drop = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperties(drop, { dataTransfer: { value: data }, clientX: { value: 0 }, clientY: { value: 0 } });
    root.querySelector(".scalajs-ui-editor__surface p:last-child")!.dispatchEvent(drop);
    await vi.waitFor(() => expect(model.body.get.indexOf("Last")).toBeLessThan(model.body.get.indexOf("/media/cat.png")));
    expect(root.querySelectorAll("img")).toHaveLength(1);
    expect(upload).not.toHaveBeenCalled();
  });

  it("deduplicates file and HTML representations from the same clipboard", async () => {
    const upload = vi.fn().mockResolvedValue({ src: "/media/cat.png", mediaId: "cat" });
    const fetchImage = vi.fn();
    vi.stubGlobal("fetch", fetchImage);
    const { lexical, root } = setup("Before", { mediaUploader: { upload } });
    paste(lexical, transfer([new File(["x"], "cat.png", { type: "image/png" })], '<img src="data:image/png;base64,YQ==">'));
    await vi.waitFor(() => expect(root.querySelectorAll("img")).toHaveLength(1));
    expect(upload).toHaveBeenCalledOnce();
    expect(fetchImage).not.toHaveBeenCalled();
  });

  it("rejects an uploader result with an external URL", async () => {
    const onMediaStatus = vi.fn();
    const { lexical, root } = setup("Before", {
      mediaUploader: { upload: vi.fn().mockResolvedValue({ src: "https://storage/cat.png", mediaId: "cat" }) }, onMediaStatus,
    });
    paste(lexical, transfer([new File(["x"], "cat.png", { type: "image/png" })]));
    await vi.waitFor(() => expect(onMediaStatus.mock.lastCall?.[0].error).toContain("internal media URL"));
    expect(root.querySelector("img")).toBeNull();
  });

  it("aborts a pending upload on unmount even when the uploader never settles", async () => {
    const upload = vi.fn().mockReturnValue(new Promise(() => {}));
    const onMediaStatus = vi.fn();
    const { lexical, app } = setup("Before", { mediaUploader: { upload }, onMediaStatus });
    paste(lexical, transfer([new File(["x"], "cat.png", { type: "image/png" })]));
    app.dispose();
    expect((upload.mock.calls[0]![1] as AbortSignal).aborted).toBe(true);
    expect(onMediaStatus).toHaveBeenLastCalledWith({ pending: 0, error: null });
  });
  it("uploads pasted files before creating nodes and preserves file order", async () => {
    const first = deferred<UploadedMediaReference>();
    const second = deferred<UploadedMediaReference>();
    const upload = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const onMediaStatus = vi.fn();
    const { lexical, model, root } = setup("Before", { mediaUploader: { upload }, onMediaStatus });
    paste(lexical, transfer([new File(["a"], "a.png", { type: "image/png" }), new File(["b"], "b.png", { type: "image/png" })]));
    expect(upload).toHaveBeenCalledTimes(2);
    expect(root.querySelector("img")).toBeNull();
    second.resolve({ mediaId: "b", src: "/media/b.png" });
    await Promise.resolve();
    expect(root.querySelector("img")).toBeNull();
    first.resolve({ mediaId: "a", src: "/media/a.png" });
    await vi.waitFor(() => expect(root.querySelectorAll("img")).toHaveLength(2));
    expect(model.body.get.indexOf("/media/a.png")).toBeLessThan(model.body.get.indexOf("/media/b.png"));
    expect(model.body.get).not.toMatch(/data:|blob:/);
    await vi.waitFor(() => expect(onMediaStatus).toHaveBeenLastCalledWith({ pending: 0, error: null }));
  });

  it("reports a failed upload and leaves the document unchanged", async () => {
    const onMediaStatus = vi.fn();
    const { lexical, root, model } = setup("Before", {
      mediaUploader: { upload: vi.fn().mockRejectedValue(new Error("Storage unavailable")) }, onMediaStatus,
    });
    paste(lexical, transfer([new File(["x"], "cat.png", { type: "image/png" })]));
    await vi.waitFor(() => expect(onMediaStatus).toHaveBeenLastCalledWith({ pending: 0, error: "Storage unavailable" }));
    expect(model.body.get).toBe("Before");
    expect(root.querySelector("img")).toBeNull();
  });

  it("does not insert a late upload into a replacement document", async () => {
    const request = deferred<UploadedMediaReference>();
    const upload = vi.fn().mockReturnValue(request.promise);
    const { lexical, root, model } = setup("Before", { mediaUploader: { upload } });
    paste(lexical, transfer([new File(["x"], "cat.png", { type: "image/png" })]));
    const signal = upload.mock.calls[0]![1] as AbortSignal;
    model.body.set("Replacement");
    expect(signal.aborted).toBe(true);
    request.resolve({ mediaId: "cat", src: "/media/cat.png" });
    await new Promise(resolve => setTimeout(resolve, 30));
    expect(model.body.get).toBe("Replacement");
    expect(root.querySelector("img")).toBeNull();
  });

  it("uses the same uploader for dropped files", async () => {
    const upload = vi.fn().mockResolvedValue({ mediaId: "cat", src: "/media/cat.png" });
    const { surface, root } = setup("Before", { mediaUploader: { upload } });
    const event = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperties(event, { dataTransfer: { value: transfer([new File(["x"], "cat.png", { type: "image/png" })]) }, clientX: { value: 0 }, clientY: { value: 0 } });
    surface.dispatchEvent(event);
    await vi.waitFor(() => expect(root.querySelector("img")?.getAttribute("src")).toBe("/media/cat.png"));
    expect(upload).toHaveBeenCalledOnce();
  });

  it("uploads through the picker and retries in the same dialog after failure", async () => {
    const upload = vi.fn().mockRejectedValueOnce(new Error("Try again"))
      .mockResolvedValueOnce({ mediaId: "cat", src: "/media/cat.png" });
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: vi.fn(() => "blob:preview"), revokeObjectURL: vi.fn() }));
    const { root, model } = setup("Before", { mediaUploader: { upload } });
    (root.querySelector('button[title="Image"]') as HTMLButtonElement).click();
    const dialog = document.querySelector(".image-plugin-dialog")!;
    const input = dialog.querySelector('input[type="file"]')!;
    Object.defineProperty(input, "files", { value: { item: () => new File(["x"], "cat.png", { type: "image/png" }) } });
    input.dispatchEvent(new Event("change", { bubbles: true }));
    (dialog.querySelector("#image-alt-input") as HTMLInputElement).value = "Katze";
    const confirm = document.querySelector(".ui-dialog__button--primary") as HTMLButtonElement;
    confirm.click();
    await vi.waitFor(() => expect(document.querySelector('[role="alert"]')?.textContent).toContain("Try again"));
    expect(model.body.get).toBe("Before");
    confirm.click();
    await vi.waitFor(() => expect(model.body.get).toContain("![Katze](/media/cat.png){width=680}"));
    await vi.waitFor(() => expect(document.querySelector(".image-plugin-dialog")).toBeNull());
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:preview");
    vi.unstubAllGlobals();
  });
});

describe("server and form contract", () => {
  it("preserves textarea input entered before hydration", async () => {
    const model = { body: property("Server") };
    const build = () => viewport(() => form(model, {}, () => editor("body")));
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.append(root);
    (root.querySelector("textarea") as HTMLTextAreaElement).value = "Typed before hydration";
    const app = await hydrate(root, build);
    disposers.push(() => app.dispose());
    expect(model.body.get).toBe("Typed before hydration");
    expect(root.querySelector(".scalajs-ui-editor__surface")?.textContent).toBe("Typed before hydration");
  });
  it("renders width/title without uploading and hydrates the same reference", async () => {
    const upload = vi.fn();
    const build = () => viewport(() => editor("body", { standalone: true, editable: false,
      value: '![Cat](/media/cat.png "Garden"){width=320}', mediaUploader: { upload } }));
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.append(root);
    expect(root.querySelector("img")?.getAttribute("width")).toBe("320");
    const app = await hydrate(root, build);
    disposers.push(() => app.dispose());
    expect(root.querySelector(".scalajs-ui-editor__surface img")?.getAttribute("title")).toBe("Garden");
    expect(root.querySelector(".scalajs-ui-editor__surface img")?.getAttribute("width")).toBe("320");
    expect(upload).not.toHaveBeenCalled();
  });

  it("submits current Markdown through the hidden textarea after RichText edits", async () => {
    const { lexical, root, model } = setup("Before\n\n![Cat](/media/cat.png){width=320}");
    lexical.update(() => $getRoot().getAllTextNodes()[0]!.setTextContent("After"), { discrete: true });
    await vi.waitFor(() => expect(model.body.get).toContain("After"));
    expect(new FormData(root.querySelector("form")!).get("body")).toBe(model.body.get);
  });
});
