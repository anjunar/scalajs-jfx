import { property } from "@anjunar/jfx-core";
import { form, imageCropper } from "@anjunar/jfx-forms";
import type { MediaValue } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

class AvatarModel {
  readonly avatar = property<MediaValue | null>(null);
}

export function formsImageCropperPage(): void {
  const model = new AvatarModel();

  form(model, () => {
    imageCropper("avatar", { aspectRatio: 1, windowTitle: translated("Crop avatar").get });
  });
}
