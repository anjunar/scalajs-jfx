import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`ImageCropper`, { de: "ImageCropper" }),
  catalogEntry(i18n`Crops an uploaded image to a fixed ratio before it reaches the model.`, { de: "Schneidet ein hochgeladenes Bild auf ein festes Seitenverhältnis zu, bevor es das Modell erreicht." }),
  catalogEntry(i18n`imageCropper(), aspectRatio, windowTitle: crops an uploaded image to a fixed ratio before it reaches the model.`, { de: "imageCropper(), aspectRatio, windowTitle: schneidet ein hochgeladenes Bild auf ein festes Verhältnis zu." }),
];
