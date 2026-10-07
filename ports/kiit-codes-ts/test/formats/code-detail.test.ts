import { describe, expect, it } from "vitest";
import { toCodeDetail, toCodeDetailCustom } from "../../src/formats/code-detail.js";
import { ErrorDetail } from "../../src/formats/error-item.js";
import type { ErrorItem } from "../../src/formats/error-item.js";
import { Codes, CodesToHttp } from "../../src/codes.js";
import { Err, ErrorInfo, ErrorField, ErrorList } from "../../src/err.js";
import { Succeeded, Restricted, Invalid, StatusConstants } from "../../src/groups.js";
import { statusCode } from "../../src/status.js";

// Ported from formats/CodeDetailTest.kt.

describe("toCodeDetail", () => {
  it("code/success/title come from the status", () => {
    const status = Restricted.DENIED;
    const detail = toCodeDetail(status);
    expect(detail.code).toBe(`${status.origin}:${status.scope}:${statusCode(status)}`);
    expect(detail.success).toBe(status.success);
    expect(detail.title).toBe(status.title);
  });

  it("code includes origin and scope and ends with the status code", () => {
    const status = { ...Restricted.DENIED, origin: "stripe.com", scope: "payments.cards" };
    const detail = toCodeDetail(status);
    expect(detail.code).toBe("stripe.com:payments.cards:Failed:Restricted:DENIED");
    expect(detail.code.endsWith(statusCode(status))).toBe(true);
  });

  it("a built-in has the codes scope", () => {
    expect(toCodeDetail(Restricted.DENIED).code).toBe("kiit.dev:codes:Failed:Restricted:DENIED");
  });

  it("every built-in has the codes scope and five values", () => {
    for (const status of Codes.all) {
      const code = toCodeDetail(status).code;
      expect(status.scope, status.name).toBe(StatusConstants.CODES);
      expect(code, status.name).toBe(`kiit.dev:codes:${statusCode(status)}`);
      expect(code.split(":"), status.name).toHaveLength(5);
    }
  });

  it("every built-in with an empty scope keeps its slot", () => {
    for (const status of Codes.all) {
      const code = toCodeDetail({ ...status, scope: "" }).code;
      expect(code, status.name).toBe(`kiit.dev::${statusCode(status)}`);
      expect(code.split(":"), status.name).toHaveLength(5);
    }
  });

  it("an empty scope keeps its slot, so code always has five values", () => {
    const code = toCodeDetail({ ...Restricted.DENIED, scope: "" }).code;
    expect(code).toBe("kiit.dev::Failed:Restricted:DENIED");
    expect(code.split(":")).toHaveLength(5);
  });

  it("success is true for Passed and false for Failed", () => {
    expect(toCodeDetail(Succeeded.SUCCESS).success).toBe(true);
    expect(toCodeDetail(Restricted.DENIED).success).toBe(false);
  });

  it("no err leaves detail/instance/errors undefined", () => {
    const detail = toCodeDetail(Restricted.DENIED);
    expect(detail.detail).toBeUndefined();
    expect(detail.instance).toBeUndefined();
    expect(detail.errors).toBeUndefined();
  });

  it("status is undefined without a mapping", () => {
    expect(toCodeDetail(Restricted.DENIED).status).toBeUndefined();
  });

  it("status is populated when a mapping is supplied", () => {
    const detail = toCodeDetail(Restricted.DENIED, undefined, CodesToHttp());
    expect(detail.status).toBe(401);
  });

  it("toCodeDetailCustom also populates status when a mapping is supplied", () => {
    interface RichError extends ErrorItem {
      readonly field?: string;
      readonly message: string;
    }
    const detail = toCodeDetailCustom<RichError>(
      Restricted.DENIED,
      undefined,
      (e) => ({ field: undefined, message: e.message }),
      CodesToHttp(),
    );
    expect(detail.status).toBe(401);
  });

  it("a single ErrorInfo populates detail, instance and one error entry", () => {
    const err = ErrorInfo("Balance too low", undefined, "job-456");
    const detail = toCodeDetail(Restricted.DENIED, err);
    expect(detail.detail).toBe("Balance too low");
    expect(detail.instance).toBe("job-456");
    expect(detail.errors).toEqual([ErrorDetail(undefined, "Balance too low")]);
  });

  it("a single ErrorField keeps its field in errors", () => {
    const detail = toCodeDetail(Invalid.INVALID_VALUE, Err.onField("firstname", "Missing"));
    expect(detail.detail).toBe("Missing");
    expect(detail.errors).toEqual([ErrorDetail("firstname", "Missing")]);
  });

  it("nested error lists are flattened into errors", () => {
    const err = ErrorList(
      [ErrorField("a", "", "A is bad"), ErrorList([ErrorField("b", "", "B is bad"), ErrorInfo("C is bad")], "Inner")],
      "Validation failed",
    );
    const detail = toCodeDetail(Invalid.INVALID_VALUE, err);
    expect(detail.detail).toBe("Validation failed");
    expect(detail.errors).toEqual([
      ErrorDetail("a", "A is bad"),
      ErrorDetail("b", "B is bad"),
      ErrorDetail(undefined, "C is bad"),
    ]);
  });

  it("a blank list message falls back to the first error message", () => {
    const err = ErrorList([ErrorField("a", "", "A is bad"), ErrorInfo("C is bad")], " ");
    expect(toCodeDetail(Invalid.INVALID_VALUE, err).detail).toBe("A is bad");
  });

  it("an empty error list has no errors and keeps its message", () => {
    const detail = toCodeDetail(Invalid.INVALID_VALUE, ErrorList([], "Validation failed"));
    expect(detail.detail).toBe("Validation failed");
    expect(detail.errors).toBeUndefined();
    expect(detail.instance).toBeUndefined();
  });

  it("an error list has no instance even with a ref", () => {
    const err = ErrorList([ErrorInfo("x")], "Failed", undefined, "req-1");
    expect(toCodeDetail(Invalid.INVALID_VALUE, err).instance).toBeUndefined();
  });

  it("a custom mapper sees every leaf and never a list", () => {
    const seen: string[] = [];
    const err = ErrorList([ErrorField("a", "", "A"), ErrorList([ErrorInfo("B")], "Inner")], "Outer");
    toCodeDetailCustom(Invalid.INVALID_VALUE, err, (e) => {
      seen.push(e.kind);
      return ErrorDetail(undefined, e.message);
    });
    expect(seen).toEqual(["ErrorField", "ErrorInfo"]);
  });

  it("an ErrorList populates default ErrorDetail entries", () => {
    const err = ErrorList(
      [ErrorField("email", "not-an-email", "Invalid email"), ErrorInfo("Something else went wrong")],
      "Validation failed",
    );
    const detail = toCodeDetail(Invalid.INVALID_VALUE, err);
    expect(detail.detail).toBe("Validation failed");
    expect(detail.errors).toEqual([ErrorDetail("email", "Invalid email"), ErrorDetail(undefined, "Something else went wrong")]);
  });

  it("toCodeDetailCustom maps through a custom ErrorItem", () => {
    interface RichError extends ErrorItem {
      readonly field?: string;
      readonly message: string;
      readonly ref?: unknown;
    }
    const err = ErrorList([ErrorField("email", "not-an-email", "Invalid email", undefined, "req-42")], "Validation failed");
    const detail = toCodeDetailCustom<RichError>(Invalid.INVALID_VALUE, err, (e) => ({
      field: e.kind === "ErrorField" ? e.field : undefined,
      message: e.message,
      ref: e.ref,
    }));
    expect(detail.errors?.[0]?.ref).toBe("req-42");
  });
});

