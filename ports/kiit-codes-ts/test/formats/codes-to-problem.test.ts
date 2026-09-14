import { describe, expect, it } from "vitest";
import { Catalog } from "../../src/formats/catalog.js";
import { CodesToProblem, problemFor } from "../../src/formats/codes-to-problem.js";
import { ErrorDetail } from "../../src/formats/error-item.js";
import type { ErrorItem } from "../../src/formats/error-item.js";
import { CodesToHttp } from "../../src/codes.js";
import { ErrorField, ErrorList } from "../../src/err.js";
import { Restricted, Invalid, StatusConstants } from "../../src/groups.js";

// Ported from formats/CodesToProblemTest.kt.

describe("CodesToProblem", () => {
  const catalog = Catalog.of();
  const codesToProblem = CodesToProblem(catalog, CodesToHttp());

  it("baseUrl defaults for the KIIT origin, with code as a query param", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", StatusConstants.KIIT);
    const problem = codesToProblem.build(status);
    expect(problem.type).toBe("https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:PAYMENT_REQUIRES_3DS#taxonomy");
  });

  it("build throws for an unregistered origin", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "com.stripe");
    expect(() => codesToProblem.build(status)).toThrowError(/No baseUrl registered/);
  });

  it("build uses the registered baseUrl and the full type path", () => {
    const stripeCatalog = Catalog.of({ "com.stripe": "https://stripe.com/problems" });
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "com.stripe", "payments.cards");
    const problem = CodesToProblem(stripeCatalog, CodesToHttp()).build(status);
    expect(problem.type).toBe("https://stripe.com/problems/payments.cards/restricted/payment-requires-3ds");
  });

  it("convert uses the explicit baseUrl regardless of the catalog", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "com.stripe");
    const problem = codesToProblem.convert(status, undefined, "https://example.com/probs");
    expect(problem.type).toBe("https://example.com/probs/restricted/payment-requires-3ds");
  });

  it("title is the status message, status is the HTTP code", () => {
    const problem = codesToProblem.build(Restricted.FORBIDDEN);
    expect(problem.title).toBe(Restricted.FORBIDDEN.message);
    expect(problem.status).toBe(403);
  });

  it("no err leaves detail/instance/errors undefined", () => {
    const problem = codesToProblem.build(Restricted.DENIED);
    expect(problem.detail).toBeUndefined();
    expect(problem.instance).toBeUndefined();
    expect(problem.errors).toBeUndefined();
  });

  it("an ErrorList populates default ErrorDetail entries", () => {
    const err = ErrorList([ErrorField("email", "not-an-email", "Invalid email")], "Validation failed");
    const problem = codesToProblem.build(Invalid.INVALID_VALUE, err);
    expect(problem.detail).toBe("Validation failed");
    expect(problem.errors?.[0]).toEqual(ErrorDetail("email", "Invalid email"));
  });

  it("buildCustom maps through a custom ErrorItem", () => {
    interface RichError extends ErrorItem {
      readonly field?: string;
      readonly message: string;
      readonly ref?: unknown;
    }
    const err = ErrorList([ErrorField("email", "not-an-email", "Invalid email", undefined, "req-42")], "Validation failed");
    const problem = codesToProblem.buildCustom<RichError>(Invalid.INVALID_VALUE, err, (e) => ({
      field: e.kind === "ErrorField" ? e.field : undefined,
      message: e.message,
      ref: e.ref,
    }));
    expect(problem.errors?.[0]?.ref).toBe("req-42");
  });

  it("problemFor matches build with the default ErrorDetail", () => {
    const status = Restricted.FORBIDDEN;
    expect(problemFor(catalog, CodesToHttp(), status)).toEqual(codesToProblem.build(status));
  });
});
