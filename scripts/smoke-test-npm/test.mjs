import { Succeeded, Restricted, CodesToHttp, Groups } from "@kiitdev/codes";

function check(condition, label) {
  if (!condition) throw new Error(`FAILED: ${label}`);
  console.log(`ok: ${label}`);
}

const status = Succeeded.SUCCESS;
check(status.name === "SUCCESS", "Succeeded.SUCCESS.name");

const http = CodesToHttp();
check(http.toCode(status) === 200, "CodesToHttp().toCode(SUCCESS) === 200");
check(http.toCode(Restricted.DENIED) === 401, "CodesToHttp().toCode(DENIED) === 401");

function describe(s) {
  switch (s.group) {
    case Groups.SUCCEEDED: return "ok";
    case Groups.RESTRICTED: return "restricted";
    default: return "other";
  }
}
check(describe(status) === "ok", "exhaustive switch narrows on Groups");

// JSON round-trip: the core reason statuses are plain objects, not classes.
const revived = JSON.parse(JSON.stringify(status));
check(revived.name === status.name && revived.group === status.group, "JSON round-trip preserves shape");
