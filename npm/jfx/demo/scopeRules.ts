/**
 * Why an ambient scope is safe here, demonstrated rather than asserted.
 *
 * The stack is only ever non-empty while synchronous code runs, and JavaScript
 * never interleaves synchronous code. So a second SSR request cannot observe the
 * first one's scope: by the time its turn starts, the stack is empty again. That
 * is what keeps this out of the failure mode ARCHITECTURE.md §5 describes.
 *
 * What remains is programmer error, and it fails loudly (§7) in both shapes:
 *
 *   1. A body that returns a promise is refused where it is written.
 *   2. A body that fires an unawaited async function is refused when the escaped
 *      continuation tries to compose -- with a message naming the cause, never by
 *      silently composing somewhere else.
 *
 * Deferred work re-enters through capture(). The DSL's own asynchronous helpers
 * already do it; hand-rolled deferrals must.
 */
import {
  capture,
  classes,
  div,
  installRuntime,
  renderToString,
  text,
} from "../src/index.js";
import { stubRuntime } from "../src/stub/index.js";

async function main(): Promise<void> {
  installRuntime(stubRuntime);

  console.log("--- 1. a body that returns a promise ---------------------------");
  console.log(
    await refusal(() =>
      renderToString(() => {
        div(() => Promise.resolve());
      })
    )
  );

  console.log("\n--- 2. a body that lets an async function escape ---------------");
  console.log(
    await refusal(async () => {
      const escaped = new Promise<void>((_resolve, reject) => {
        void renderToString(() => {
          div(() => {
            void (async () => {
              await Promise.resolve();
              try {
                text("would compose into whatever scope is current later");
                reject(new Error("NOT REACHED"));
              } catch (error) {
                reject(error as Error);
              }
            })();
          });
        });
      });
      await escaped;
    })
  );

  console.log("\n--- 3. capture() restores the position -------------------------");
  const result = await renderToString(() => {
    div(() => {
      classes("host");
      const restore = capture();
      queueMicrotask(() => restore(() => text("composed from a later turn")));
    });
  });
  console.log(result.html);
}

async function refusal(work: () => Promise<unknown>): Promise<string> {
  try {
    await work();
    return "NOT REACHED -- this should have been refused";
  } catch (error) {
    return `refused: ${firstSentence((error as Error).message)}`;
  }
}

function firstSentence(message: string): string {
  const stop = message.indexOf(". ");
  return stop < 0 ? message : `${message.slice(0, stop)}.`;
}

main().catch((error: unknown) => {
  console.error(error);
  throw error;
});
