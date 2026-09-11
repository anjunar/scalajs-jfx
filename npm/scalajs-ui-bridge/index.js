import { installRuntime } from "@anjunar/scalajs-ui-core";
import {
  bridgeRuntime,
  parseInstant,
  parseLocalDate,
  parseLocalDateTime,
} from "./dist/fullopt/main.js";

// Register through core's shared slot, including its duplicate-runtime guard.
installRuntime(bridgeRuntime);

export { bridgeRuntime, parseInstant, parseLocalDate, parseLocalDateTime };
