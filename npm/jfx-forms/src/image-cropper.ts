/**
 * An image field with an in-browser crop dialog (canvas drag, live preview),
 * bound by name like `input`. Mirrors `jfx.forms.ImageCropper`. The value is
 * a {@link MediaValue}, translated at the boundary from `jfx.forms.Media` --
 * see `MediaCodec` in `jfx-bridge`'s `FormFactories.scala`.
 *
 * Not projected: `previewMaxWidth`/`previewMaxHeight`, `outputMaxWidth`/
 * `outputMaxHeight`, `thumbnailMaxWidth`/`thumbnailMaxHeight`, custom
 * `ImageValidator`s -- each has an obvious trigger to add later.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export interface ImageCropperOptions {
  readonly placeholder?: string;
  /** Locks the crop rectangle to `width / height`. Unset: free-form cropping. */
  readonly aspectRatio?: number;
  /** An output MIME type such as `"image/png"` or `"image/jpeg"`. Defaults to `"image/png"`. */
  readonly outputType?: string;
  /** `0..1`. Only meaningful for a lossy `outputType`. Defaults to `0.92`. */
  readonly outputQuality?: number;
  readonly windowTitle?: string;
  /** Skips registration with the enclosing form context -- an image field with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts an image cropper field named `name`. */
export function imageCropper(name: string, options: ImageCropperOptions = {}): void {
  component("image-cropper", defined({ name, ...options }), () => {});
}
