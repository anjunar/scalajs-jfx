import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
for (const file of ["index.js", "dist/fullopt/main.js", "types/index.d.ts", "README.md"]) {
  await access(resolve(packageRoot, file));
}

const { parseInstant, parseLocalDate, parseLocalDateTime } = await import(pathToFileURL(resolve(packageRoot, "index.js")));
if (parseLocalDate("2026-09-07").format("dd.MM.yyyy", "de-DE") !== "07.09.2026") {
  throw new Error("scala-java-time LocalDate export is not usable");
}
if (parseLocalDateTime("2026-09-07T20:15:00").format("dd.MM.yyyy HH:mm", "de-DE") !== "07.09.2026 20:15") {
  throw new Error("scala-java-time LocalDateTime export is not usable");
}
if (parseInstant("2026-09-07T18:15:00Z").epochMilli !== 1788804900000) {
  throw new Error("scala-java-time Instant export is not usable");
}

console.log("scalajs-ui-bridge linked artifact and types verified");
