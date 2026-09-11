/**
 * `property`, `ReadOnlyProperty.map` and `ListProperty` -- the state half of
 * `contract.ts`, exercised through the package entry point.
 */
import { beforeEach, describe, expect, it } from "vitest";
import { listProperty, property } from "../src/index.js";
import { useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

describe("property", () => {
  it("reports its initial value and is not dirty", () => {
    const count = property(0);
    expect(count.get).toBe(0);
    expect(count.isDirty).toBe(false);
  });

  it("notifies observers with the current value on subscribe", () => {
    const seen: number[] = [];
    property(7).observe((value) => seen.push(value));
    expect(seen).toEqual([7]);
  });

  it("skips the initial value for observeWithoutInitial", () => {
    const seen: number[] = [];
    const count = property(7);
    count.observeWithoutInitial((value) => seen.push(value));
    count.set(8);
    expect(seen).toEqual([8]);
  });

  it("does not notify when set to an equal value, but setAlways does", () => {
    const seen: number[] = [];
    const count = property(1);
    count.observeWithoutInitial((value) => seen.push(value));
    count.set(1);
    expect(seen).toEqual([]);
    count.setAlways(1);
    expect(seen).toEqual([1]);
  });

  it("tracks dirtiness against the initial value and resets to it", () => {
    const name = property("anna");
    name.set("bea");
    expect(name.isDirty).toBe(true);
    name.reset();
    expect(name.get).toBe("anna");
    expect(name.isDirty).toBe(false);
  });

  it("stops notifying after the subscription is disposed", () => {
    const seen: number[] = [];
    const count = property(0);
    const subscription = count.observeWithoutInitial((value) => seen.push(value));
    count.set(1);
    subscription.dispose();
    count.set(2);
    expect(seen).toEqual([1]);
  });
});

describe("ReadOnlyProperty.map", () => {
  it("derives a value and follows the source", () => {
    const count = property(2);
    const label = count.map((value) => `n=${value}`);
    expect(label.get).toBe("n=2");

    const seen: string[] = [];
    label.observe((value) => seen.push(value));
    count.set(3);
    expect(seen).toEqual(["n=2", "n=3"]);
  });

  it("chains", () => {
    const count = property(2);
    const doubledLabel = count.map((value) => value * 2).map((value) => `${value}`);
    expect(doubledLabel.get).toBe("4");
    count.set(5);
    expect(doubledLabel.get).toBe("10");
  });

  it("disposes the underlying subscription", () => {
    const count = property(0);
    const seen: number[] = [];
    const subscription = count.map((value) => value + 1).observeWithoutInitial((value) => seen.push(value));
    count.set(1);
    subscription.dispose();
    count.set(2);
    expect(seen).toEqual([2]);
  });
});

describe("listProperty", () => {
  it("starts from a copy of the initial values", () => {
    const source = ["a", "b"];
    const items = listProperty(source);
    source.push("c");
    expect(items.get).toEqual(["a", "b"]);
    expect(items.size).toBe(2);
  });

  it("defaults to empty", () => {
    expect(listProperty<string>().get).toEqual([]);
  });

  it("notifies on add, insert, removeAt, clear and setAll", () => {
    const items = listProperty<string>(["a"]);
    const log: string[][] = [];
    items.observeWithoutInitial((value) => log.push([...value]));

    items.add("b");
    items.insert(0, "z");
    items.removeAt(1);
    items.setAll(["x"]);
    items.clear();

    expect(log).toEqual([
      ["a", "b"],
      ["z", "a", "b"],
      ["z", "b"],
      ["x"],
      [],
    ]);
  });

  it("maps like any other read-only property", () => {
    const items = listProperty<number>([1, 2, 3]);
    const total = items.map((values) => values.reduce((sum, value) => sum + value, 0));
    expect(total.get).toBe(6);
    items.add(4);
    expect(total.get).toBe(10);
  });
});
