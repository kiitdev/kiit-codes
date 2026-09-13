import { describe, expect, it } from "vitest";
import { Checked, collect } from "../src/checked.js";
import { Err } from "../src/err.js";
import type { HasErrors, HasStatus } from "../src/err.js";
import { Succeeded, Invalid } from "../src/groups.js";
import type { Status } from "../src/status.js";

// Ported from CheckedTest.kt.

function validatePhone(phone: string): Checked {
  return phone.length >= 10
    ? Checked.success()
    : Checked.failure(Invalid.INVALID_VALUE, [Err.on("phone", phone, "Too short")]);
}

describe("validatePhone (realistic Checked-returning validator)", () => {
  it("succeeds for a long enough number", () => {
    const checked = validatePhone("5551234567");
    expect(checked.isValid).toBe(true);
    expect(checked.status).toBe(Succeeded.SUCCESS);
  });

  it("fails for a too-short number", () => {
    const checked = validatePhone("12345");
    expect(checked.isValid).toBe(false);
    expect(checked.status).toBe(Invalid.INVALID_VALUE);
    expect(checked.errors).toHaveLength(1);
    expect(checked.errors[0]?.message).toBe("Too short");
  });
});

describe("Checked.success / Checked.failure", () => {
  it("success defaults to Succeeded.SUCCESS with no errors", () => {
    const checked = Checked.success();
    expect(checked.status).toBe(Succeeded.SUCCESS);
    expect(checked.errors).toHaveLength(0);
  });

  it("success accepts a custom Passed status", () => {
    const checked = Checked.success(Succeeded.CREATED);
    expect(checked.status).toBe(Succeeded.CREATED);
    expect(checked.errors).toHaveLength(0);
  });

  it("failure carries the given status and errors", () => {
    const errors = [Err.of("bad field")];
    const checked = Checked.failure(Invalid.BAD_REQUEST, errors);
    expect(checked.status).toBe(Invalid.BAD_REQUEST);
    expect(checked.errors).toEqual(errors);
  });

  it("failure requires at least one error", () => {
    expect(() => Checked.failure(Invalid.BAD_REQUEST, [])).toThrowError(/at least one Err/);
  });

  it("isValid is true for success, false for failure", () => {
    expect(Checked.success().isValid).toBe(true);
    expect(Checked.failure(Invalid.BAD_REQUEST, [Err.of("bad field")]).isValid).toBe(false);
  });

  it("satisfies HasErrors/HasStatus structurally", () => {
    const checked = Checked.failure(Invalid.BAD_REQUEST, [Err.of("bad field")]);
    const hasErrors: HasErrors = checked;
    const hasStatus: HasStatus<Status> = checked;
    expect(hasErrors.errors).toEqual(checked.errors);
    expect(hasStatus.status).toBe(checked.status);
  });
});

describe("collect", () => {
  it("succeeds with no checks", () => {
    const result = collect();
    expect(result.status).toBe(Succeeded.SUCCESS);
    expect(result.errors).toHaveLength(0);
  });

  it("succeeds when every check passes", () => {
    const result = collect(Checked.success(), Checked.success(Succeeded.CREATED));
    expect(result.status).toBe(Succeeded.SUCCESS);
    expect(result.errors).toHaveLength(0);
  });

  it("pools one failure's errors", () => {
    const errors = [Err.of("bad field")];
    const result = collect(Checked.success(), Checked.failure(Invalid.BAD_REQUEST, errors));
    expect(result.status).toBe(Invalid.INVALID_VALUE);
    expect(result.errors).toEqual(errors);
  });

  it("pools multiple failures' errors in order", () => {
    const firstErrors = [Err.of("first")];
    const secondErrors = [Err.of("second"), Err.of("third")];
    const result = collect(
      Checked.failure(Invalid.BAD_REQUEST, firstErrors),
      Checked.success(),
      Checked.failure(Invalid.NOT_FOUND, secondErrors),
    );
    expect(result.status).toBe(Invalid.INVALID_VALUE);
    expect(result.errors).toEqual([...firstErrors, ...secondErrors]);
  });

  it("succeeds when validatePhone passes alongside other checks", () => {
    const result = collect(Checked.success(), validatePhone("5551234567"));
    expect(result.status).toBe(Succeeded.SUCCESS);
    expect(result.errors).toHaveLength(0);
  });

  it("pools validatePhone's errors with other failures", () => {
    const emailErrors = [Err.on("email", "", "must contain @")];
    const result = collect(Checked.failure(Invalid.INVALID_VALUE, emailErrors), validatePhone("123"));
    expect(result.status).toBe(Invalid.INVALID_VALUE);
    expect(result.errors).toEqual([...emailErrors, ...validatePhone("123").errors]);
  });

  it("behaves identically called with individual args or a spread array", () => {
    const checks = [
      Checked.failure(Invalid.BAD_REQUEST, [Err.of("first")]),
      Checked.success(),
      Checked.failure(Invalid.NOT_FOUND, [Err.of("second"), Err.of("third")]),
    ] as const;
    const fromSpread = collect(...checks);
    const fromArgs = collect(checks[0], checks[1], checks[2]);
    expect(fromSpread.status).toBe(fromArgs.status);
    expect(fromSpread.errors).toEqual(fromArgs.errors);
  });
});
