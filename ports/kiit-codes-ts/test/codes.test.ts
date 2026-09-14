import { describe, expect, it } from "vitest";
import { Codes, CodesToHttp } from "../src/codes.js";
import {
  StatusConstants,
  Succeeded,
  Pending,
  Excluded,
  Information,
  Restricted,
  Invalid,
  Rejected,
  Unserved,
} from "../src/groups.js";

// Ported from CodesTest.kt and CodesToHttpTest.kt. CodesToGrpcTest/CompositeLookupTest are not
// ported: CodesToGrpc/CompositeLookup are deferred (see the plan doc's Phase 3 scope).

describe("Codes.all: data", () => {
  it("SUCCESS has the correct values", () => {
    expect(Succeeded.SUCCESS.name).toBe("SUCCESS");
    expect(Succeeded.SUCCESS.origin).toBe(StatusConstants.KIIT);
    expect(Succeeded.SUCCESS.message).toBe("The operation completed successfully.");
    expect(Succeeded.SUCCESS.success).toBe(true);
  });

  it("DENIED has the correct values", () => {
    expect(Restricted.DENIED.name).toBe("DENIED");
    expect(Restricted.DENIED.origin).toBe(StatusConstants.KIIT);
    expect(Restricted.DENIED.success).toBe(false);
  });

  it("every built-in code has the KIIT origin", () => {
    expect(Codes.all.every((s) => s.origin === StatusConstants.KIIT)).toBe(true);
  });

  it("every built-in code is unique by origin+group+name", () => {
    const keys = Codes.all.map((s) => `${s.origin}:${s.group}:${s.name}`);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("skipped and discarded are both Excluded with distinct names", () => {
    expect(Excluded.SKIPPED.success).toBe(true);
    expect(Excluded.DISCARDED.success).toBe(true);
    expect(Excluded.SKIPPED.name).not.toBe(Excluded.DISCARDED.name);
  });

  it("information codes all have success true", () => {
    expect(Information.METADATA.success).toBe(true);
    expect(Information.HEALTH.success).toBe(true);
    expect(Information.DIAGNOSTICS.success).toBe(true);
  });

  it("EXITED is Succeeded, not Information", () => {
    expect(Succeeded.EXITED.success).toBe(true);
    expect(Succeeded.EXITED.group).toBe(Succeeded.SUCCESS.group);
  });

  it("SCHEDULED is Pending", () => {
    expect(Pending.SCHEDULED.success).toBe(true);
    expect(Pending.SCHEDULED.group).toBe(Pending.ACCEPTED.group);
  });

  it("DEGRADED, LEGAL_BLOCK, and ABORTED are all Unserved", () => {
    expect(Unserved.DEGRADED.success).toBe(false);
    expect(Unserved.LEGAL_BLOCK.success).toBe(false);
    expect(Unserved.ABORTED.success).toBe(false);
  });
});

describe("Codes.statusFor", () => {
  it("finds a built-in status by origin/group/name", () => {
    const found = Codes.statusFor(StatusConstants.KIIT, "Restricted", "DENIED");
    expect(found).toBe(Restricted.DENIED);
  });

  it("returns undefined for an unregistered combination", () => {
    expect(Codes.statusFor(StatusConstants.KIIT, "Restricted", "NOT_A_REAL_CODE")).toBeUndefined();
  });
});

describe("CodesToHttp: group defaults", () => {
  const http = CodesToHttp();

  it("Succeeded/Excluded/Information default to 200, Pending to 202", () => {
    expect(http.toCode(Succeeded.UPDATED)).toBe(200);
    expect(http.toCode(Excluded.OMITTED)).toBe(200);
    expect(http.toCode(Information.METADATA)).toBe(200);
    expect(http.toCode(Pending.PROCESSING)).toBe(202);
  });

  it("Restricted defaults to 401, Invalid to 400, Rejected to 409, Unserved to 503", () => {
    expect(http.toCode(Restricted.UNAUTHENTICATED)).toBe(401);
    expect(http.toCode(Invalid.OUT_OF_RANGE)).toBe(400);
    expect(http.toCode(Rejected.RULE_VIOLATION)).toBe(409);
    // CONFLICT needs no override; 409 is already Rejected's own group default.
    expect(http.toCode(Rejected.CONFLICT)).toBe(409);
    expect(http.toCode(Unserved.UNREACHABLE)).toBe(503);
  });

  it("DEGRADED and ABORTED fall through to Unserved's default (no closer HTTP equivalent)", () => {
    expect(http.toCode(Unserved.DEGRADED)).toBe(503);
    expect(http.toCode(Unserved.ABORTED)).toBe(503);
  });

  it("a custom, unregistered status still resolves via its group's default", () => {
    const custom = Rejected("CUSTOM", "Custom error");
    expect(http.toCode(custom)).toBe(409);
  });
});

describe("CodesToHttp: per-code overrides", () => {
  const http = CodesToHttp();

  it("matches the full DEFAULT_OVERRIDES table", () => {
    expect(http.toCode(Succeeded.CREATED)).toBe(201);
    expect(http.toCode(Succeeded.HANDLED)).toBe(204);
    expect(http.toCode(Pending.CONFIRM)).toBe(200);
    expect(http.toCode(Excluded.CANCELLED)).toBe(499);
    expect(http.toCode(Pending.REDIRECTED)).toBe(307);
    expect(http.toCode(Invalid.NOT_FOUND)).toBe(404);
    expect(http.toCode(Rejected.NOT_EXISTS)).toBe(404);
    expect(http.toCode(Restricted.FORBIDDEN)).toBe(403);
    expect(http.toCode(Restricted.SUSPENDED)).toBe(403);
    expect(http.toCode(Restricted.LOCKED)).toBe(423);
    expect(http.toCode(Rejected.EXPIRED)).toBe(410);
    expect(http.toCode(Rejected.GONE)).toBe(410);
    expect(http.toCode(Invalid.PAYLOAD_TOO_LARGE)).toBe(413);
    expect(http.toCode(Unserved.UNSUPPORTED)).toBe(501);
    expect(http.toCode(Unserved.TIMEOUT)).toBe(504);
    expect(http.toCode(Unserved.RATE_LIMITED)).toBe(429);
    expect(http.toCode(Unserved.RESOURCE_LIMITED)).toBe(429);
    expect(http.toCode(Unserved.UNEXPECTED)).toBe(500);
    expect(http.toCode(Unserved.LEGAL_BLOCK)).toBe(451);
  });

  it("overrides are keyed by origin+scope+group+name, not full structural equality", () => {
    // Same identity as NOT_FOUND, different message - still resolves to its override.
    const differentMessage = Invalid("NOT_FOUND", "A completely different message.", StatusConstants.KIIT);
    expect(http.toCode(differentMessage)).toBe(404);
  });

  it("an override does not apply across a different group sharing origin+name", () => {
    // Shares CREATED's origin+name but is Invalid, not Succeeded - must not get CREATED's 201.
    const collidesWithCreated = Invalid("CREATED", "failure", StatusConstants.KIIT);
    expect(http.toCode(collidesWithCreated)).toBe(400);

    // Same collision in the other direction, against NOT_FOUND's override.
    const collidesWithNotFound = Succeeded("NOT_FOUND", "ok", StatusConstants.KIIT);
    expect(http.toCode(collidesWithNotFound)).toBe(200);
  });
});

describe("CodesToHttp.toStatus: reverse lookup", () => {
  const http = CodesToHttp();

  it("finds the registered status for a unique code", () => {
    expect(http.toStatus(201)?.name).toBe(Succeeded.CREATED.name);
  });

  it("returns undefined for an unrecognized code, no guessed fallback", () => {
    expect(http.toStatus(999)).toBeUndefined();
  });

  it("round-trips for an overridden code", () => {
    expect(http.toStatus(404)?.name).toBe(Invalid.NOT_FOUND.name);
  });

  it("a forward+back round trip does not generally preserve the original status", () => {
    const original = Succeeded.UPDATED;
    const code = http.toCode(original);
    const restored = http.toStatus(code);
    expect(code).toBe(200);
    expect(restored).toBe(Succeeded.SUCCESS);
    expect(restored).not.toBe(original);
  });

  it("resolves deterministically via CANONICAL_PREFERENCE when multiple statuses share a code", () => {
    expect(http.toStatus(200)).toBe(Succeeded.SUCCESS);
    expect(http.toStatus(404)).toBe(Invalid.NOT_FOUND);
    expect(http.toStatus(410)).toBe(Rejected.GONE);
    expect(http.toStatus(500)).toBe(Unserved.UNEXPECTED);
    expect(http.toStatus(409)).toBe(Rejected.CONFLICT);
    expect(http.toStatus(501)).toBe(Unserved.UNSUPPORTED);
    expect(http.toStatus(401)).toBe(Restricted.UNAUTHENTICATED);
    expect(http.toStatus(403)).toBe(Restricted.FORBIDDEN);
  });

  it("422 has no dedicated mapping since INVALID_ENTITY was removed from Codes", () => {
    expect(http.toStatus(422)).toBeUndefined();
  });

  it("stays in sync with a custom overrides map, not just the defaults", () => {
    const key = `${Unserved.TIMEOUT.origin}:${Unserved.TIMEOUT.scope}:${Unserved.TIMEOUT.group}:${Unserved.TIMEOUT.name}`;
    const custom = CodesToHttp({ [key]: 599 });
    expect(custom.toStatus(599)).toBe(Unserved.TIMEOUT);
    // TIMEOUT no longer resolves to 504 for this instance.
    expect(custom.toStatus(504)).toBeUndefined();
  });
});

describe("CodesToHttp: scope and overrides", () => {
  const http = CodesToHttp();

  it("scope does not affect toCode when the group default already applies", () => {
    // RULE_VIOLATION has no override to begin with, nothing for scope to disturb.
    const scoped = { ...Rejected.RULE_VIOLATION, scope: "payments.cards" };
    expect(http.toCode(scoped)).toBe(409);
  });

  it("a scoped copy of an overridden status falls back to the group default", () => {
    // CREATED's 201 override is registered for the bare status only; scoping it is a distinct
    // key, so it falls through to Succeeded's plain group default.
    const scoped = { ...Succeeded.CREATED, scope: "payments.cards" };
    expect(http.toCode(scoped)).toBe(200);
  });
});
