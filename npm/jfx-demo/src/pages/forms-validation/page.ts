import { classes, div, property } from "@anjunar/jfx-core";
import type { Property } from "@anjunar/jfx-core";
import {
  assertFalse,
  assertTrue,
  decimalMax,
  decimalMin,
  digits,
  email,
  form,
  future,
  futureOrPresent,
  input,
  inputContainer,
  isNull,
  max,
  min,
  negative,
  negativeOrZero,
  notBlank,
  notEmpty,
  notNull,
  past,
  pastOrPresent,
  pattern,
  positive,
  positiveOrZero,
  size,
} from "@anjunar/jfx-forms";

function stringField(): Property<string> {
  return property("");
}

/** One field per validator -- all 22, per CLAUDE_DEMO_PLAN.md §5. */
export function formsValidationPage(): void {
  const model = {
    notBlank: stringField(),
    notEmpty: stringField(),
    notNull: stringField(),
    isNull: stringField(),
    size: stringField(),
    min: stringField(),
    max: stringField(),
    decimalMin: stringField(),
    decimalMax: stringField(),
    digits: stringField(),
    positive: stringField(),
    positiveOrZero: stringField(),
    negative: stringField(),
    negativeOrZero: stringField(),
    email: stringField(),
    pattern: stringField(),
    past: stringField(),
    pastOrPresent: stringField(),
    future: stringField(),
    futureOrPresent: stringField(),
    assertTrue: stringField(),
    assertFalse: stringField(),
  };

  const presence = ["notBlank", "notEmpty", "notNull", "isNull", "size"] as const;
  const numeric = ["min", "max", "decimalMin", "decimalMax", "digits", "positive", "positiveOrZero", "negative", "negativeOrZero"] as const;
  const format = ["email", "pattern"] as const;
  const temporal = ["past", "pastOrPresent", "future", "futureOrPresent"] as const;
  const boolean_ = ["assertTrue", "assertFalse"] as const;

  // A plain div, not fieldSet(): fieldSet registers itself as a control of
  // its own enclosing form (see /forms/composition), which logs a
  // binding-failure message unless its name also happens to be a model
  // field. Grouping fields visually here doesn't need that.
  function group(fields: readonly string[]): void {
    div(() => {
      classes("flex", "flex-col", "gap-2");
      for (const field of fields) {
        inputContainer({ label: field }, () => {
          input(field);
        });
      }
    });
  }

  form(
    model,
    {
      schema: {
        notBlank: [notBlank()],
        notEmpty: [notEmpty()],
        notNull: [notNull()],
        isNull: [isNull()],
        size: [size(2, 5)],
        min: [min(10)],
        max: [max(10)],
        decimalMin: [decimalMin("1.5")],
        decimalMax: [decimalMax("1.5")],
        digits: [digits(3, 2)],
        positive: [positive()],
        positiveOrZero: [positiveOrZero()],
        negative: [negative()],
        negativeOrZero: [negativeOrZero()],
        email: [email()],
        pattern: [pattern(/^[a-z]+$/)],
        past: [past()],
        pastOrPresent: [pastOrPresent()],
        future: [future()],
        futureOrPresent: [futureOrPresent()],
        assertTrue: [assertTrue()],
        assertFalse: [assertFalse()],
      },
    },
    () => {
      div(() => {
        classes("flex", "flex-col", "gap-4");
        group(presence);
        group(numeric);
        group(format);
        group(temporal);
        group(boolean_);
      });
    }
  );
}
