import { spawn } from "node:child_process";

const steps = ["build:client", "build:server", "prerender"];
const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";

for (const step of steps) {
  await run(npmCommand, ["run", step]);
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: process.env,
      stdio: "inherit",
      shell: process.platform === "win32",
    });

    child.once("error", reject);
    child.once("exit", (code, signal) => {
      if (code === 0) resolve();
      else reject(new Error(`${command} ${args.join(" ")} failed${signal ? ` (${signal})` : ""}`));
    });
  });
}
