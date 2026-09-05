# scalajs-jfx-forms

Model-bound forms and controls for JFX 3, including inputs, validation, field groups, nested and repeating forms, combo boxes, and image cropping.

## Overview

`jfx-forms` connects named controls to reflected Scala model properties. `Form` and `SubForm` use the model's `ClassDescriptor` and annotations to bind values and create validators. `ArrayForm` repeats a form body over a `ListProperty`. The controls use core properties for bidirectional updates and core lifecycle ownership for subscriptions.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-forms" % "3.0.0"
```

## Quick start

```scala
import jfx.forms.Form.form
import jfx.forms.Input.input
import jfx.forms.InputContainer.inputContainer
import jfx.core.state.Property
import jfx.forms.validators.{EmailConstraint, NotBlank}

import scala.annotation.meta.field

final class Profile(
    @(NotBlank @field)("Name is required")
    var name: Property[String] = Property(""),
    @(EmailConstraint @field)()
    var email: Property[String] = Property("")
)

val profile = new Profile()
form(profile) {
  inputContainer("Name") { input("name") {} }
  inputContainer("Email") { input("email") {} }
}
```

## Core concepts

Controls bind by name to a `Property` on the form model. `Input` handles text-like values, `InputContainer` supplies a label and presentation shell, and `FieldSet` groups controls without creating an additional model binding. `Form.validate()` evaluates registered controls and returns validation errors; server errors can be applied through the form controller.

`SubForm` binds a nested model as a child control. `ArrayForm` renders one item body per list entry. `ComboBox` uses the viewport overlay for its dropdown. `ImageCropper` maps a `Media` value and opens its crop dialog in the viewport layer.

## Usage

```scala
import jfx.forms.ArrayForm.arrayForm
import jfx.forms.SubForm.subForm

form(profile) {
  subForm[Address]("address") {
    input("street") {}
  }
  arrayForm("aliases") { index =>
    input(s"aliases-$index") {}
  }
}
```

Validators are supplied by `jfx.forms.validators`, including nullability, emptiness, size, numeric bounds, decimal bounds, digits, patterns, email, and date constraints. They use the same annotation metadata for model-backed validation.

## SSR and non-JavaScript behavior

SSR renders the form fields and their current values. Native form controls and links remain readable without JavaScript. Hydration adds bidirectional model updates, client-side validation, dropdown behavior, crop interaction, and server-error presentation. Server-side validation errors can be mapped back to controls by field path.

## API overview

- `Form.form`, `SubForm.subForm`, `ArrayForm.arrayForm`
- `Input.input`, `InputContainer.inputContainer`, `FieldSet.fieldSet`
- `ComboBox.comboBox`, `ImageCropper.imageCropper`
- `FormController.validate`, `validateBindings`, and error response handling
- `jfx.forms.validators` and validator annotations

## Related modules

- [`jfx-core`](../jfx-core/README.md) provides properties and lifecycle.
- [`jfx-controls`](../jfx-controls/README.md) provides collection controls.
- [`jfx-viewport`](../jfx-viewport/README.md) hosts combo-box and cropper overlays.
- [`jfx-editor`](../jfx-editor/README.md) is a form-compatible rich-text control.
