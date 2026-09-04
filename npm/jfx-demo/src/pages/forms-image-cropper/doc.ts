/** Keep title/summary in sync with the "/forms/image-cropper" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { formsImageCropperPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function formsImageCropperDoc(): void {
  docPage(
    { title: "ImageCropper", summary: "imageCropper(), aspectRatio, windowTitle: crops an uploaded image to a fixed ratio before it reaches the model." },
    () => {
      example({ code: snippet }, () => {
        formsImageCropperPage();
      });
    }
  );
}
