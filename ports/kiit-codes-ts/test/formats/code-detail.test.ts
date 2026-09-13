import { describe, expect, it } from "vitest";
import { toCodeDetail, toCodeDetailCustom } from "../../src/formats/code-detail.js";
import { ErrorDetail } from "../../src/formats/error-item.js";
import type { ErrorItem } from "../../src/formats/error-item.js";
import { CodesToHttp } from "../../src/codes.js";
import { ErrorInfo, ErrorField, ErrorList } from "../../src/err.js";
import { Succeeded, Restricted, Invalid } from "../../src/groups.js";
import { statusPath, statusCode } from "../../src/status.js";

// Ported from formats/CodeDetailTest.kt.

describe("toCodeDetail", () => {
  it("path/code/success/message come straight from the status", () => {
    const status = Restricted.DENIED;
    const detail = toCodeDetail(status);
    expect(detail.path).toBe(statusPath(status));
    expect(detail.code).toBe(statusCode(status));
    expect(detail.success).toBe(status.success);
    expect(detail.message).toBe(status.message);
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

  it("a single ErrorInfo populates detail and instance from ref", () => {
    const err = ErrorInfo("Balance too low", undefined, "job-456");
    const detail = toCodeDetail(Restricted.DENIED, err);
    expect(detail.detail).toBe("Balance too low");
    expect(detail.instance).toBe("job-456");
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

