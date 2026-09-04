// The ambient shape of `@anjunar/scalajs-jfx-bridge` lives in `@anjunar/jfx`'s
// own `src/bridge.d.ts` -- that package owns the contract, so it owns the
// declaration of its runtime peer (JAVASCRIPT_API.md §4).
//
// It is reached by relative path for the same reason `entry-client.ts` reaches
// the library itself by relative path: this app is not yet a real consumer of
// the published packages. Two things have to change together before both lines
// can go, and both belong to the npm modularisation:
//
//  1. `@anjunar/jfx` does not currently *ship* this declaration -- `files` lists
//     `dist` only, and `tsc` does not copy an input `.d.ts` into `outDir`. A
//     consumer that installs both packages therefore cannot typecheck
//     `import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge"` under
//     `strict`; it gets TS7016.
//  2. The relative import in `entry-client.ts` / `entry-server.ts` exists
//     because Vite's SSR runner instantiates the package twice when it is
//     reached through both a `file:` symlink and a real path.
/// <reference path="../../jfx/src/bridge.d.ts" />
