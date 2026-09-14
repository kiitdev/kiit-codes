import type { Status, Passed, Failed } from "@kiitdev/codes";
import { Succeeded } from "@kiitdev/codes";

function describe(status: Status): string {
  if (status.success) {
    const p: Passed = status;
    return p.name;
  }
  const f: Failed = status;
  return f.name;
}

console.log(describe(Succeeded.SUCCESS));
