# @anjunar/jfx-forms

The forms API of JFX3 in TypeScript: model-bound inputs, validators, field
groups, repeating and nested sub-forms, a combo box, and an image cropper.

Like every package in the family, this is **types and ergonomics, not a
framework**. Rendering, DOM binding, keyboard handling and the crop dialog's
canvas dragging all live in the `jfx.forms` Scala.js components -- the same
classes the Scala demo mounts -- published as part of the linked runtime
`@anjunar/scalajs-jfx-bridge`. Adding this package does not add a second
implementation; `jfx-bridge` grew a `dependsOn(jfxForms)` edge and eight
registry entries (`form`, `sub-form`, `input`, `input-container`, `field-set`,
`array-form`, `combo-box`, `image-cropper`).

```bash
npm install @anjunar/jfx-core @anjunar/jfx-controls @anjunar/jfx-viewport @anjunar/jfx-forms @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## The one real design problem, and how this package answers it

`jfx.forms.Form`/`SubForm` bind a control to a model property by asking a
macro-built `reflect.ClassDescriptor` for the field named after the control --
built by a Scala-3 macro over an actual case class
(`ReflectMacros.reflectWithAccessors[M]`). There is no such macro in
TypeScript, and no case class behind a plain TS object to read annotations
from.

So a form model here is **not** a plain value object -- it is a class or
record containing one bridge `Property<T>`/`ListProperty<T>` handle per
bindable field, from `runtime.property(...)`/`runtime.listProperty(...)`. A
control's model property is found by *name* directly in that object. Decorated
class models carry their validator metadata with the fields, so `form` can
infer the schema without a separate `schema` object:

```ts
import { property } from "@anjunar/jfx-core";
import { form, input, notNull, size, email } from "@anjunar/jfx-forms";

const model = {
  name: property(""),
  email: property(""),
};

form(
  model,
  { schema: { name: [notNull(), size(1, 100)], email: [notNull(), email()] } },
  () => {
    input("name");
    input("email", { type: "email" });
  }
);
```

The annotation-style API uses real TypeScript classes and returns the same
model instance to the form:

```ts
import { property } from "@anjunar/jfx-core";
import { Email, form, NotBlank, input } from "@anjunar/jfx-forms";

class AccountModel {
  @NotBlank()
  readonly name = property("");

  @Email()
  readonly email = property("");
}

const model = new AccountModel();
form(model, () => {
  input("name");
  input("email", { type: "email" });
});
```

Enable TypeScript's `experimentalDecorators` option for this syntax. The
explicit `{ schema }` form remains available for plain records and for cases
where the validator list is assembled dynamically.

`form(...)` returns a `FormHandle`. Keep it when a submit action needs to call
`validate()`, inspect `validateBindings()`, apply `setErrorResponses(...)`, or
clear errors with `clearErrors()`.

`notNull()`/`size(...)`/`email()` and their decorator counterparts (`@NotNull()`/
`@Size(...)`/`@Email()`) build the exact `{ name, parameters }` shape
`reflect.Annotation` already has -- `FormFactories.schemaFrom` (in
`jfx-bridge`) turns them into real `Annotation`s that the same, unmodified
`ValidatorFactory`/`BuiltinValidators` consume. **No validator logic is
ported to TypeScript**; every validator listed below runs the identical Scala
code a `jfx.forms.Form` on the Scala side would run.

| Function | Scala annotation |
| --- | --- |
| `notNull`, `isNull` | `@NotNull`, `@Null` |
| `assertTrue`, `assertFalse` | `@AssertTrue`, `@AssertFalse` |
| `notEmpty`, `notBlank` | `@NotEmpty`, `@NotBlank` |
| `size(min?, max?)` | `@Size` |
| `min(value)`, `max(value)` | `@Min`, `@Max` |
| `decimalMin(value, inclusive?)`, `decimalMax(...)` | `@DecimalMin`, `@DecimalMax` |
| `positive`, `positiveOrZero`, `negative`, `negativeOrZero` | `@Positive`, ... |
| `digits(integer, fraction)` | `@Digits` |
| `pattern(regexOrString)` | `@Pattern` |
| `email()` | `@EmailConstraint` |
| `past`, `pastOrPresent`, `future`, `futureOrPresent` | `@Past`, ... |

## Controls

- **`input(name, options?, content?)`** -- a text input. `options.type`
  defaults to `"text"`.
- **`inputContainer({ label }, content)`** -- a floating-label wrapper; put
  exactly one control inside.
- **`fieldSet({ name }, content)`** -- groups controls for error propagation
  and disabled cascading. Like its Scala counterpart, it does **not** bind
  the controls inside it to any model property; only `form`/`subForm` bind.
- **`arrayForm(name, itemRenderer, options?)`** -- a repeating group over a
  `ListProperty` field, one call to `itemRenderer(index)` per item.
- **`subForm(name, model, options, content)`** -- a nested form, itself bound
  as a control of its parent (needs a `Property` field on the parent model
  holding the whole nested model record).
- **`comboBox(name, options)`** -- single/multi-select over `items`
  (a `ListProperty` or a plain array), with an optional `converter`/
  `itemRenderer`. Its dropdown is a `@anjunar/jfx-viewport` overlay, so it
  needs a `viewport(...)` ancestor.
- **`imageCropper(name, options?)`** -- an image field with an in-browser
  crop dialog. Its value is a {@link MediaValue}
  (`{ id, name, contentType, data, thumbnail? }`), translated at the boundary
  from `jfx.forms.Media` -- there is no TypeScript equivalent to construct
  that class directly.

## A worked example: repeating and nested

```ts
import { listProperty, property } from "@anjunar/jfx-core";
import { arrayForm, form, input, notBlank, subForm } from "@anjunar/jfx-forms";

const owner = { name: property(""), email: property("") };
const model = {
  title: property("Grocery list"),
  items: listProperty<string>(["Milk", "Eggs"]),
  // The parent field a subForm binds to: a Property wrapping the nested model.
  owner: property(owner),
};

form(model, { schema: { title: [notBlank()] } }, () => {
  input("title");

  arrayForm("items", (index) => {
    input(`items-${index}`);
  });

  // subForm's own model is the *unwrapped* dictionary -- the parent's `owner`
  // field (above) is what it binds to, by name, the same as an `input` does.
  subForm("owner", owner, { schema: { name: [notBlank()] } }, () => {
    input("name");
    input("email", { type: "email" });
  });
});
```

### Not in this release

`SubForm.newInstance()`/`clearForm()`/`factory` (an imperative
reinstantiation hook -- mount a fresh `subForm` under `when()` instead),
`ComboBox`'s `valueRenderer`/`footerRenderer`/`identityBy`/`selectionText`/
sizing options, and `ImageCropper`'s size-limit/custom-validator options are
not projected -- each has an obvious trigger to add later. `arrayForm` also
carries forward a real limitation of `jfx.forms.ArrayForm` itself: an edit
inside an item control is not written back into the array (only structural
list changes re-render items from the model) -- this facade does not paper
over it.

## Tests

```bash
npm run verify   # typecheck + the bridge smoke test + the consumer test
```

Like the controls and viewport facades, this suite runs only against the
really linked bridge -- model binding needs the real `Property`/`ListProperty`
handles, which the stub runtime does not build. Link it first:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```
