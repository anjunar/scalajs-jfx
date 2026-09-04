/**
 * The plain, JSON-shaped value an `imageCropper` field holds.
 *
 * `jfx.forms.Media`/`Thumbnail` are Scala classes of `Property`-wrapped
 * fields, built for the Scala.js-only cropping UI (canvas drag, live preview).
 * There is no TypeScript equivalent to construct or read directly. `MediaCodec`
 * (in `jfx-bridge`'s `FormFactories.scala`) converts at the boundary instead --
 * this is the value on this side of that conversion, and the only one an
 * `imageCropper` model field ever needs to hold.
 */
export interface MediaValue {
  readonly id: string;
  readonly name: string;
  readonly contentType: string;
  readonly data: string;
  readonly thumbnail?: MediaValue;
}
