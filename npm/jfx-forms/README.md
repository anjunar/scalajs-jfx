# @anjunar/jfx-forms

Typed JFX3 forms for model-bound inputs, validators, field groups, nested and repeating forms, combo boxes, and image cropper fields.

## Overview

This package projects `jfx.forms` into TypeScript. It does not port validation or DOM behavior: the linked Scala.js runtime performs model binding, validation, input handling, overlays, and crop interaction.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/jfx-forms @anjunar/jfx-controls @anjunar/jfx-viewport @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Quick start

```ts
import { property } from "@anjunar/jfx-core";
import { email, form, input, inputContainer, notBlank } from "@anjunar/jfx-forms";

const model = { name: property(""), email: property("") };

const mounted = form(model, {
  schema: { name: [notBlank()], email: [email()] },
}, () => {
  inputContainer({ label: "Name" }, () => input("name"));
  inputContainer({ label: "Email" }, () => input("email", { type: "email" }));
});

mounted.validate();
```

Models are records or class instances containing one `Property` or `ListProperty` per bindable field. Decorated TypeScript classes can carry validator metadata; explicit schemas are useful for records or dynamic validator lists.

## Usage

- `fieldSet` groups controls and propagates disabled/error state; it does not create a model binding.
- `arrayForm` repeats a body over a `ListProperty`.
- `subForm` binds a nested model through a parent `Property`.
- `comboBox` supports local arrays or list properties and uses a viewport overlay.
- `imageCropper` maps a `MediaValue` and uses the viewport for its dialog.

The validators mirror the Scala annotation set: `notNull`, `notBlank`, `notEmpty`, `size`, numeric bounds, decimal bounds, `digits`, `pattern`, `email`, and past/future constraints. Decorator counterparts such as `@NotBlank()` and `@Email()` are exported as well.

## SSR and non-JavaScript behavior

SSR renders native form fields and their values. Hydration adds bidirectional updates, client validation, dropdown behavior, crop interaction, and server-error mapping. Keep the server form usable as HTML when JavaScript is disabled.

## API overview

- `form`, `subForm`, `arrayForm`
- `input`, `inputContainer`, `fieldSet`
- `comboBox`, `imageCropper`, `MediaValue`
- `FormHandle`, `FormOptions`, `FormSchema`, `ValidatorSpec`
- Validator functions and decorator classes exported from `validators`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides `Property` and lifecycle.
- [`@anjunar/jfx-controls`](../jfx-controls/README.md) provides collection controls.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) hosts dropdowns and dialogs.
- [`@anjunar/jfx-editor`](../jfx-editor/README.md) provides a rich-text form field.
