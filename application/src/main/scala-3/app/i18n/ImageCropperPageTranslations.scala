package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object ImageCropperPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Image cropper", "Bildzuschnitt"),
    de(
      i18n"Upload, crop and bind images through the regular forms contract.",
      "Bilder über den regulären Formularvertrag hochladen, zuschneiden und binden."
    ),
    de(i18n"Upload and crop", "Hochladen und zuschneiden"),
    de(i18n"One control, two image sizes", "Ein Control, zwei Bildgrößen"),
    de(
      i18n"The cropper stores a bounded main image and generates a separate thumbnail while keeping file readers, canvas interaction and its viewport window lifecycle-bound.",
      "Der Cropper speichert ein begrenztes Hauptbild und erzeugt ein separates Vorschaubild; FileReader, Canvas-Interaktion und Viewport-Fenster bleiben an seinen Lebenszyklus gebunden."
    ),
    de(i18n"Crop a profile image", "Profilbild zuschneiden"),
    de(
      i18n"Choose an image, adjust the square selection and apply it in the viewport window.",
      "Wähle ein Bild, passe den quadratischen Ausschnitt an und übernimm ihn im Viewport-Fenster."
    ),
    de(i18n"Choose a profile image", "Profilbild auswählen"),
    de(i18n"Crop profile image", "Profilbild zuschneiden"),
    de(i18n"Generated thumbnail", "Erzeugtes Vorschaubild"),
    de(i18n"Cropped profile image", "Zugeschnittenes Profilbild"),
    de(i18n"Readonly state", "Schreibgeschützter Zustand"),
    de(
      i18n"Editability comes from the same control contract used by Input and ComboBox.",
      "Die Editierbarkeit stammt aus demselben Control-Vertrag wie bei Input und ComboBox."
    ),
    de(i18n"Image selection is disabled", "Die Bildauswahl ist deaktiviert"),
    de(i18n"Contextual DSL", "Kontextbezogene DSL"),
    de(
      i18n"ImageCropper remains a normal typed control and can participate in model binding.",
      "ImageCropper bleibt ein normales typisiertes Control und kann an die Modellbindung teilnehmen."
    )
  )
}
