import type {
  Disposable,
  ListProperty,
  Property,
  ReadOnlyProperty,
} from "../contract.js";

/** Straight port of `jfx.core.state.Property`, minus the cycle detector. */
export class StubProperty<T> implements Property<T> {
  private listeners: Array<(value: T) => void> = [];
  private defaultValue: T;

  constructor(private value: T) {
    this.defaultValue = value;
  }

  get get(): T {
    return this.value;
  }

  get isDirty(): boolean {
    return this.value !== this.defaultValue;
  }

  set(value: T): void {
    if (value !== this.value) this.setAlways(value);
  }

  setAlways(value: T): void {
    this.value = value;
    for (const listener of [...this.listeners]) listener(value);
  }

  reset(): void {
    this.set(this.defaultValue);
  }

  observe(observer: (value: T) => void): Disposable {
    this.listeners.push(observer);
    observer(this.value);
    return this.remover(observer);
  }

  observeWithoutInitial(observer: (value: T) => void): Disposable {
    this.listeners.push(observer);
    return this.remover(observer);
  }

  map<R>(transform: (value: T) => R): ReadOnlyProperty<R> {
    return new MappedProperty(this, transform);
  }

  private remover(observer: (value: T) => void): Disposable {
    return {
      dispose: () => {
        this.listeners = this.listeners.filter((entry) => entry !== observer);
      },
    };
  }
}

class MappedProperty<S, T> implements ReadOnlyProperty<T> {
  constructor(
    private readonly source: ReadOnlyProperty<S>,
    private readonly transform: (value: S) => T
  ) {}

  get get(): T {
    return this.transform(this.source.get);
  }

  observe(observer: (value: T) => void): Disposable {
    return this.source.observe((value) => observer(this.transform(value)));
  }

  observeWithoutInitial(observer: (value: T) => void): Disposable {
    return this.source.observeWithoutInitial((value) =>
      observer(this.transform(value))
    );
  }

  map<R>(transform: (value: T) => R): ReadOnlyProperty<R> {
    return new MappedProperty(this, transform);
  }
}

export class StubListProperty<T> implements ListProperty<T> {
  private readonly backing: StubProperty<readonly T[]>;

  constructor(initial: readonly T[]) {
    this.backing = new StubProperty<readonly T[]>([...initial]);
  }

  get get(): readonly T[] {
    return this.backing.get;
  }
  get size(): number {
    return this.backing.get.length;
  }
  setAll(values: readonly T[]): void {
    this.backing.setAlways([...values]);
  }
  add(value: T): void {
    this.setAll([...this.get, value]);
  }
  insert(index: number, value: T): void {
    const next = [...this.get];
    next.splice(index, 0, value);
    this.setAll(next);
  }
  removeAt(index: number): void {
    const next = [...this.get];
    next.splice(index, 1);
    this.setAll(next);
  }
  clear(): void {
    this.setAll([]);
  }
  observe(observer: (value: readonly T[]) => void): Disposable {
    return this.backing.observe(observer);
  }
  observeWithoutInitial(observer: (value: readonly T[]) => void): Disposable {
    return this.backing.observeWithoutInitial(observer);
  }
  map<R>(transform: (value: readonly T[]) => R): ReadOnlyProperty<R> {
    return this.backing.map(transform);
  }
}
