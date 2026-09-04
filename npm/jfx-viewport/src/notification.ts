/**
 * Short feedback that dismisses itself. Mirrors `Viewport.notify`.
 *
 * A registry entry (`"notification"`), the same as `floatingWindow` -- but
 * unlike a window, its lifetime is independent of whatever mounted it: it
 * fades out and removes itself on its own timer regardless, and dismisses
 * early if whatever placed it in the tree unmounts first. Call it directly
 * from an event handler (`onClick(() => notify("Saved"))`), the same way the
 * Scala demo calls `Viewport.notify` from `onClick`.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export type NotificationKind = "info" | "success" | "warning" | "error";

export interface NotificationOptions {
  readonly kind?: NotificationKind;
  /** Milliseconds before the notification fades out. Defaults to 3000. */
  readonly durationMs?: number;
}

/** Fires one notification. */
export function notify(message: string, options: NotificationOptions = {}): void {
  component("notification", defined({ message, ...options }));
}
