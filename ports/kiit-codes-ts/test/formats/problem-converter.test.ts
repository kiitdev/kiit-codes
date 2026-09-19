import { describe, expect, it } from "vitest";
import { ProblemConverter, problemFor } from "../../src/formats/problem-converter.js";
import { ErrorDetail } from "../../src/formats/error-item.js";
import type { ErrorItem } from "../../src/formats/error-item.js";
import { CodesToHttp } from "../../src/codes.js";
import { ErrorField, ErrorList } from "../../src/err.js";
import { Rejected, Restricted, Invalid, StatusConstants } from "../../src/groups.js";

// Ported from formats/ProblemConverterTest.kt.

describe("ProblemConverter", () => {
  const converter = ProblemConverter();

  it("baseUrl defaults for the KIIT origin, with code as a query param", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", StatusConstants.KIIT);
    expect(converter.convert(status).type).toBe(
      "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:PAYMENT_REQUIRES_3DS#taxonomy",
    );
  });

  it("a domain origin with no entry builds type from the origin", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com", "payments.cards");
    expect(converter.convert(status).type).toBe(
      "https://stripe.com/problems/payments.cards/restricted/payment-requires-3ds",
    );
  });

  it("a plain id origin with no entry does not throw and is used as is", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
    expect(converter.convert(status).type).toBe("https://myapp1/problems/rejected/out-of-stock");
  });

  it("the origin is lowercased in the derived baseUrl", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "MyApp1");
    expect(converter.convert(status).type).toBe("https://myapp1/problems/rejected/out-of-stock");
  });

  it("an empty scope is skipped and a set scope is the first segment", () => {
    const noScope = Rejected("OUT_OF_STOCK", "Out of stock", "stripe.com");
    const scoped = { ...noScope, scope: "payments.cards" };
    expect(converter.convert(noScope).type).toBe("https://stripe.com/problems/rejected/out-of-stock");
    expect(converter.convert(scoped).type).toBe("https://stripe.com/problems/payments.cards/rejected/out-of-stock");
  });

  it("convert uses the registered baseUrl and the full type path", () => {
    const problems = ProblemConverter({ "stripe.com": "https://stripe.com/errors" });
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com", "payments.cards");
    expect(problems.convert(status).type).toBe(
      "https://stripe.com/errors/payments.cards/restricted/payment-requires-3ds",
    );
  });

  it("a registered entry wins over the origin-derived baseUrl", () => {
    const problems = ProblemConverter({ "stripe.com": "https://docs.stripe.com/errors" });
    const status = Rejected("DUPLICATE_CHARGE", "Already processed", "stripe.com", "payments.cards");
    expect(problems.convert(status).type).toBe(
      "https://docs.stripe.com/errors/payments.cards/rejected/duplicate-charge",
    );
  });

  it("registered keys are lowercased and a trailing slash is trimmed", () => {
    const problems = ProblemConverter({ "Stripe.com": "https://docs.stripe.com/errors/" });
    const status = Rejected("DUPLICATE_CHARGE", "Already processed", "stripe.com");
    expect(problems.convert(status).type).toBe("https://docs.stripe.com/errors/rejected/duplicate-charge");
  });

  it("the KIIT origin is fixed and cannot be overridden", () => {
    const problems = ProblemConverter({ [StatusConstants.KIIT]: "https://example.com/problems" });
    expect(problems.convert(Restricted.DENIED).type).toBe(
      "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:DENIED#taxonomy",
    );
  });

  it("a custom typeBuilder suffix is appended to the origin-derived baseUrl", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Already processed", "stripe.com");
    const problem = converter.convert(status, undefined, (s) => `errors/${s.name.toLowerCase()}`);
    expect(problem.type).toBe("https://stripe.com/problems/errors/duplicate_charge");
  });

  it("accepts an explicit mapping with no baseUrls", () => {
    expect(ProblemConverter({}, CodesToHttp()).convert(Restricted.FORBIDDEN).status).toBe(403);
  });

  it("convertWithUrl uses the explicit baseUrl regardless of baseUrls", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com");
    const problem = converter.convertWithUrl(status, undefined, "https://example.com/probs");
    expect(problem.type).toBe("https://example.com/probs/restricted/payment-requires-3ds");
  });

  it("convertWithUrl trims a trailing slash", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com");
    const problem = converter.convertWithUrl(status, undefined, "https://example.com/probs/");
    expect(problem.type).toBe("https://example.com/probs/restricted/payment-requires-3ds");
  });

  it("convertCustomWithUrl maps errors and uses the explicit baseUrl", () => {
    const status = Invalid("BAD_EMAIL", "Bad email", "stripe.com");
    const err = ErrorList([ErrorField("email", "x", "Invalid email")], "Validation failed");
    const problem = converter.convertCustomWithUrl(status, err, "https://example.com/probs", (e) =>
      ErrorDetail("mapped", e.message),
    );
    expect(problem.type).toBe("https://example.com/probs/invalid/bad-email");
    expect(problem.errors?.[0]).toEqual(ErrorDetail("mapped", "Invalid email"));
  });

  it("title is the status message, status is the HTTP code", () => {
    const problem = converter.convert(Restricted.FORBIDDEN);
    expect(problem.title).toBe(Restricted.FORBIDDEN.message);
    expect(problem.status).toBe(403);
  });

  it("no err leaves detail/instance/errors undefined", () => {
    const problem = converter.convert(Restricted.DENIED);
    expect(problem.detail).toBeUndefined();
    expect(problem.instance).toBeUndefined();
    expect(problem.errors).toBeUndefined();
  });

  it("an ErrorList populates default ErrorDetail entries", () => {
    const err = ErrorList([ErrorField("email", "not-an-email", "Invalid email")], "Validation failed");
    const problem = converter.convert(Invalid.INVALID_VALUE, err);
    expect(problem.detail).toBe("Validation failed");
    expect(problem.errors?.[0]).toEqual(ErrorDetail("email", "Invalid email"));
  });

  it("convertCustom maps through a custom ErrorItem", () => {
    interface RichError extends ErrorItem {
      readonly field?: string;
      readonly message: string;
      readonly ref?: unknown;
    }
    const err = ErrorList([ErrorField("email", "not-an-email", "Invalid email", undefined, "req-42")], "Validation failed");
    const problem = converter.convertCustom<RichError>(Invalid.INVALID_VALUE, err, (e) => ({
      field: e.kind === "ErrorField" ? e.field : undefined,
      message: e.message,
      ref: e.ref,
    }));
    expect(problem.errors?.[0]?.ref).toBe("req-42");
  });

  it("problemFor matches convert with the default ErrorDetail", () => {
    const status = Restricted.FORBIDDEN;
    expect(problemFor(status)).toEqual(converter.convert(status));
  });
});
