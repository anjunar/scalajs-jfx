import { attr, classes, div, property } from "@anjunar/jfx-core";
import type { Property } from "@anjunar/jfx-core";
import {
  AssertFalse,
  AssertTrue,
  comboBox,
  DecimalMax,
  DecimalMin,
  Digits,
  Email,
  form,
  Future,
  FutureOrPresent,
  input,
  inputContainer,
  Max,
  Min,
  Negative,
  NegativeOrZero,
  NotBlank,
  NotEmpty,
  NotNull,
  Null,
  Past,
  PastOrPresent,
  Pattern,
  Positive,
  PositiveOrZero,
  Size,
} from "@anjunar/jfx-forms";

function stringField(): Property<string> {
  return property("");
}

class ValidationModel {
  @NotBlank() readonly notBlank = stringField();
  @NotEmpty() readonly notEmpty = stringField();
  @NotNull() readonly notNull = stringField();
  @Null() readonly isNull = stringField();
  @Size(2, 5) readonly size = stringField();
  @Min(10) readonly min = stringField();
  @Max(10) readonly max = stringField();
  @DecimalMin("1.5") readonly decimalMin = stringField();
  @DecimalMax("1.5") readonly decimalMax = stringField();
  @Digits(3, 2) readonly digits = stringField();
  @Positive() readonly positive = stringField();
  @PositiveOrZero() readonly positiveOrZero = stringField();
  @Negative() readonly negative = stringField();
  @NegativeOrZero() readonly negativeOrZero = stringField();
  @Email() readonly email = stringField();
  @Pattern(/^[a-z]+$/) readonly pattern = stringField();
  @Past() readonly past = stringField();
  @PastOrPresent() readonly pastOrPresent = stringField();
  @Future() readonly future = stringField();
  @FutureOrPresent() readonly futureOrPresent = stringField();
  @AssertTrue() readonly assertTrue = property(false);
  @AssertFalse() readonly assertFalse = property(true);
}

/** One field per validator -- all 22, per CLAUDE_DEMO_PLAN.md §5. */
export function formsValidationPage(): void {
  const model = new ValidationModel();

  const presence = ["notBlank", "notEmpty", "notNull", "isNull", "size"] as const;
  const numeric = ["min", "max", "decimalMin", "decimalMax", "digits", "positive", "positiveOrZero", "negative", "negativeOrZero"] as const;
  const format = ["email", "pattern"] as const;
  const temporal = ["past", "pastOrPresent", "future", "futureOrPresent"] as const;
  const boolean_ = ["assertTrue", "assertFalse"] as const;

  // A plain div, not fieldSet(): fieldSet registers itself as a control of
  // its own enclosing form (see /forms/composition), which logs a
  // binding-failure message unless its name also happens to be a model
  // field. Grouping fields visually here doesn't need that.
  function group(fields: readonly (keyof ValidationModel)[], render: (field: keyof ValidationModel) => void): void {
    div(() => {
      classes("flex", "flex-col", "gap-2");
      for (const field of fields) {
        inputContainer({ label: field }, () => {
          render(field);
        });
      }
    });
  }

  form(model, () => {
    div(() => {
      classes("flex", "flex-col", "gap-4");
      group(presence, field => input(field));
      group(numeric, field => input(field, { type: "number" }, () => {
        attr("step", field === "min" || field === "max" ? "1" : "any");
      }));
      group(format, field => input(field, { type: field === "email" ? "email" : "text" }));
      group(temporal, field => input(field, { type: "date" }));
      group(boolean_, field => comboBox(field, { items: [true, false], converter: String }));
    });
  });
}
