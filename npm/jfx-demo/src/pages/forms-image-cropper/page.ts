import { property } from "@anjunar/jfx-core";
import { form, imageCropper } from "@anjunar/jfx-forms";
import type { MediaValue } from "@anjunar/jfx-forms";

export function formsImageCropperPage(): void {
  const model = { avatar: property<MediaValue | null>(null) };

  form(model, {}, () => {
    imageCropper("avatar", { aspectRatio: 1, windowTitle: "Crop avatar" });
  });
}
