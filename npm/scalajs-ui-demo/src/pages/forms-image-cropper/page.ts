import { property } from "@anjunar/scalajs-ui-core";
import { form, imageCropper } from "@anjunar/scalajs-ui-forms";
import type { MediaValue } from "@anjunar/scalajs-ui-forms";
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
