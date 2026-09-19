import { describe, expect, it } from "vitest";
import {
  Groups,
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
import { groupDescription, statusPath, statusCode } from "../src/status.js";

// Ported from StatusTest.kt. Two Kotlin test groups are intentionally not ported:
// - statusKey checks: StatusKey is `internal` in Kotlin (module-private, inaccessible to real
//   external consumers too) and has no exported TS equivalent; covered indirectly via codes.test.ts's
//   CodesToHttp scope/override behavior instead.
// - typealias transparency checks (Restricted === Failed.Restricted, same instance): TS has no
//   typealias-vs-fully-qualified duality, groups.ts's Restricted is the only representation.

describe("Status: success flag", () => {
  it("is true for every Passed group", () => {
    expect(Succeeded("S", "S").success).toBe(true);
    expect(Pending("P", "P").success).toBe(true);
    expect(Excluded("F", "F").success).toBe(true);
    expect(Information("I", "I").success).toBe(true);
  });

  it("is false for every Failed group", () => {
    expect(Restricted("R", "R").success).toBe(false);
    expect(Invalid("I", "I").success).toBe(false);
    expect(Rejected("E", "E").success).toBe(false);
    expect(Unserved("U", "U").success).toBe(false);
  });
});

describe("Status: origin", () => {
  it("defaults to custom for direct construction", () => {
    expect(Succeeded("S", "S").origin).toBe(StatusConstants.CUSTOM);
    expect(Restricted("R", "R").origin).toBe(StatusConstants.CUSTOM);
  });

  it("is overridable at the call site", () => {
    expect(Succeeded("S", "S", StatusConstants.KIIT).origin).toBe(StatusConstants.KIIT);
  });
});

describe("Status: group", () => {
  it("returns the correct string for all eight groups", () => {
    expect(Succeeded("S", "S").group).toBe(Groups.SUCCEEDED);
    expect(Pending("P", "P").group).toBe(Groups.PENDING);
    expect(Excluded("F", "F").group).toBe(Groups.EXCLUDED);
    expect(Information("N", "N").group).toBe(Groups.INFORMATION);
    expect(Restricted("R", "R").group).toBe(Groups.RESTRICTED);
    expect(Invalid("I", "I").group).toBe(Groups.INVALID);
    expect(Rejected("E", "E").group).toBe(Groups.REJECTED);
    expect(Unserved("U", "U").group).toBe(Groups.UNSERVED);
  });
});

describe("groupDescription", () => {
  it("is non-blank and distinct for all eight groups", () => {
    const descriptions = [
      groupDescription(Succeeded("S", "S")),
      groupDescription(Pending("P", "P")),
      groupDescription(Excluded("F", "F")),
      groupDescription(Information("N", "N")),
      groupDescription(Restricted("R", "R")),
      groupDescription(Invalid("I", "I")),
      groupDescription(Rejected("E", "E")),
      groupDescription(Unserved("U", "U")),
    ];
    expect(descriptions.every((d) => d.trim().length > 0)).toBe(true);
    expect(new Set(descriptions).size).toBe(descriptions.length);
  });

  it("is consistent across instances of the same group", () => {
    expect(groupDescription(Restricted("A", "A"))).toBe(groupDescription(Restricted("B", "B")));
  });
});

describe("Status: scope", () => {
  it("defaults to an empty string", () => {
    expect(Restricted("RESTRICTED", "Restricted").scope).toBe("");
    expect(Succeeded.SUCCESS.scope).toBe("");
  });

  it("is settable via the constructor", () => {
    const s = Restricted("RESTRICTED", "Restricted", StatusConstants.KIIT, "payments.cards");
    expect(s.scope).toBe("payments.cards");
  });

  it("is settable via spread on an existing instance, including built-ins", () => {
    const scoped = { ...Succeeded.CREATED, scope: "payments.cards" };
    expect(scoped.scope).toBe("payments.cards");
    expect(scoped.name).toBe(Succeeded.CREATED.name);
    expect(scoped.origin).toBe(Succeeded.CREATED.origin);
  });
});

describe("statusPath", () => {
  it("is just origin when scope is unset", () => {
    const s = Restricted("RESTRICTED", "Restricted", StatusConstants.KIIT);
    expect(statusPath(s)).toBe(StatusConstants.KIIT);
  });

  it("includes scope when set", () => {
    const s = Restricted("RESTRICTED", "Restricted", StatusConstants.KIIT, "payments.cards");
    expect(statusPath(s)).toBe(`${StatusConstants.KIIT}:payments.cards`);
  });
});

describe("statusCode", () => {
  it("is Passed:group:name for a passed status", () => {
    expect(statusCode(Succeeded.SUCCESS)).toBe("Passed:Succeeded:SUCCESS");
  });

  it("is Failed:group:name for a failed status", () => {
    const s = Restricted("RESTRICTED", "Restricted", StatusConstants.KIIT);
    expect(statusCode(s)).toBe("Failed:Restricted:RESTRICTED");
  });

  it("is unaffected by scope", () => {
    const s = { ...Succeeded.SUCCESS, scope: "payments.cards" };
    expect(statusCode(s)).toBe("Passed:Succeeded:SUCCESS");
  });

  it("is not unique across different origins", () => {
    const kiitDenied = Restricted("DENIED", "Denied", StatusConstants.KIIT);
    const customDenied = Restricted("DENIED", "Custom denied", "com.acme");
    expect(statusCode(kiitDenied)).toBe(statusCode(customDenied));
  });
});
