import { describe, expect, it } from "vitest";
import { ProblemConverter } from "../../src/formats/problem-converter.js";
import { ErrorDetail } from "../../src/formats/error-item.js";
import type { ErrorItem } from "../../src/formats/error-item.js";
import { CodesToHttp } from "../../src/codes.js";
import { ErrorField, ErrorInfo, ErrorList } from "../../src/err.js";
import { Err } from "../../src/err.js";
import { Rejected, Restricted, Invalid, Succeeded, StatusConstants } from "../../src/groups.js";
import { toCodeDetail } from "../../src/formats/code-detail.js";

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
      "https://stripe.com/docs/codes/payments/cards/failed/restricted/payment-requires-3ds",
    );
  });

  it("a plain id origin with no entry gives a relative type", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
    expect(converter.convert(status).type).toBe("/docs/codes/failed/rejected/out-of-stock");
  });

  it("the origin is lowercased in the derived baseUrl", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "Stripe.com");
    expect(converter.convert(status).type).toBe("https://stripe.com/docs/codes/failed/rejected/out-of-stock");
  });

  it("an empty scope is skipped and a set scope is the first segment", () => {
    const noScope = Rejected("OUT_OF_STOCK", "Out of stock", "stripe.com");
    const scoped = { ...noScope, scope: "payments.cards" };
    expect(converter.convert(noScope).type).toBe("https://stripe.com/docs/codes/failed/rejected/out-of-stock");
    expect(converter.convert(scoped).type).toBe(
      "https://stripe.com/docs/codes/payments/cards/failed/rejected/out-of-stock",
    );
  });

  it("convert uses the registered baseUrl and the full type path", () => {
    const problems = ProblemConverter({ "stripe.com": "https://stripe.com/errors" });
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com", "payments.cards");
    expect(problems.convert(status).type).toBe(
      "https://stripe.com/errors/payments/cards/failed/restricted/payment-requires-3ds",
    );
  });

  it("a registered entry wins over the origin-derived baseUrl", () => {
    const problems = ProblemConverter({ "stripe.com": "https://docs.stripe.com/errors" });
    const status = Rejected("DUPLICATE_CHARGE", "Already processed", "stripe.com", "payments.cards");
    expect(problems.convert(status).type).toBe(
      "https://docs.stripe.com/errors/payments/cards/failed/rejected/duplicate-charge",
    );
  });

  it("registered keys are lowercased and a trailing slash is trimmed", () => {
    const problems = ProblemConverter({ "Stripe.com": "https://docs.stripe.com/errors/" });
    const status = Rejected("DUPLICATE_CHARGE", "Already processed", "stripe.com");
    expect(problems.convert(status).type).toBe("https://docs.stripe.com/errors/failed/rejected/duplicate-charge");
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
    expect(problem.type).toBe("https://stripe.com/docs/codes/errors/duplicate_charge");
  });

  it("accepts an explicit mapping with no baseUrls", () => {
    expect(ProblemConverter({}, CodesToHttp()).convert(Restricted.FORBIDDEN).status).toBe(403);
  });

  it("convertWithUrl uses the explicit baseUrl regardless of baseUrls", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com");
    const problem = converter.convertWithUrl(status, undefined, "https://example.com/probs");
    expect(problem.type).toBe("https://example.com/probs/failed/restricted/payment-requires-3ds");
  });

  it("convertWithUrl trims a trailing slash", () => {
    const status = Restricted("PAYMENT_REQUIRES_3DS", "3DS required", "stripe.com");
    const problem = converter.convertWithUrl(status, undefined, "https://example.com/probs/");
    expect(problem.type).toBe("https://example.com/probs/failed/restricted/payment-requires-3ds");
  });

  it("convertCustomWithUrl maps errors and uses the explicit baseUrl", () => {
    const status = Invalid("BAD_EMAIL", "Bad email", "stripe.com");
    const err = ErrorList([ErrorField("email", "x", "Invalid email")], "Validation failed");
    const problem = converter.convertCustomWithUrl(status, err, "https://example.com/probs", (e) =>
      ErrorDetail("mapped", e.message),
    );
    expect(problem.type).toBe("https://example.com/probs/failed/invalid/bad-email");
    expect(problem.errors?.[0]).toEqual(ErrorDetail("mapped", "Invalid email"));
  });

  it("a dotted scope becomes slash separated segments", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "payments.cards");
    expect(converter.convert(status).type).toBe(
      "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge",
    );
  });

  it("a deep scope keeps the last three segments as status, group, name", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "a.b.c.d");
    expect(converter.convert(status).type).toBe(
      "https://stripe.com/docs/codes/a/b/c/d/failed/rejected/duplicate-charge",
    );
  });

  it("scope segments are lowercased and underscores become dashes", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "Pay_ments.Cards");
    expect(converter.convert(status).type).toBe(
      "https://stripe.com/docs/codes/pay-ments/cards/failed/rejected/duplicate-charge",
    );
  });

  it("a passed status uses the passed segment", () => {
    const status = Succeeded("ORDER_CREATED", "Order created", "stripe.com");
    expect(converter.convert(status).type).toBe("https://stripe.com/docs/codes/passed/succeeded/order-created");
  });

  it("domain origins with hyphens and many labels are absolute", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "api.my-shop.example.com");
    expect(converter.convert(status).type).toBe(
      "https://api.my-shop.example.com/docs/codes/failed/rejected/out-of-stock",
    );
  });

  it("origins that are not domains give a relative type", () => {
    for (const origin of ["localhost", "localhost:8080", "my_app", "-bad.com", "bad-.com", "stripe.com.", "a..b", "my app.com"]) {
      const status = Rejected("OUT_OF_STOCK", "Out of stock", origin);
      expect(converter.convert(status).type, origin).toBe("/docs/codes/failed/rejected/out-of-stock");
    }
  });

  it("a registered entry for a non-domain origin is absolute with the new suffix", () => {
    const problems = ProblemConverter({ myapp1: "https://docs.example.com/myapp1/errors" });
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1", "shop.cart");
    expect(problems.convert(status).type).toBe(
      "https://docs.example.com/myapp1/errors/shop/cart/failed/rejected/out-of-stock",
    );
  });

  it("a custom typeBuilder with a relative base", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
    const problem = converter.convert(status, undefined, (s) => `stock/${s.name.toLowerCase()}`);
    expect(problem.type).toBe("/docs/codes/stock/out_of_stock");
  });

  it("an empty typeBuilder suffix leaves just the base url", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "stripe.com");
    const problem = converter.convertWithUrl(status, undefined, "https://example.com/probs/out-of-credit", () => "");
    expect(problem.type).toBe("https://example.com/probs/out-of-credit");
  });

  it("type can be replaced by spreading the problem for any url", () => {
    const problem = { ...converter.convert(Restricted.FORBIDDEN), type: "https://other.example.org/probs/forbidden" };
    expect(problem.type).toBe("https://other.example.org/probs/forbidden");
    expect(problem.status).toBe(403);
  });

  it("code is the exact origin, scope and status code", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "payments.cards");
    expect(converter.convert(status).code).toBe("stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE");
  });

  it("code keeps an empty scope slot", () => {
    const status = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
    expect(converter.convert(status).code).toBe("myapp1::Failed:Rejected:OUT_OF_STOCK");
  });

  it("code for a built-in status", () => {
    expect(converter.convert(Restricted.FORBIDDEN).code).toBe("kiit.dev:codes:Failed:Restricted:FORBIDDEN");
  });

  it("code is set with an error list and with a custom type url", () => {
    const err = ErrorList([ErrorField("email", "x", "Invalid email")], "Validation failed");
    const status = Invalid("BAD_EMAIL", "Bad email", "stripe.com");
    expect(converter.convert(status, err).code).toBe("stripe.com::Failed:Invalid:BAD_EMAIL");
    expect(converter.convertWithUrl(status, err, "https://example.com/probs").code).toBe("stripe.com::Failed:Invalid:BAD_EMAIL");
  });

  it("code matches the CodeDetail code for the same status", () => {
    const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "payments.cards");
    expect(converter.convert(status).code).toBe(toCodeDetail(status).code);
    expect(converter.convert(Restricted.FORBIDDEN).code).toBe(toCodeDetail(Restricted.FORBIDDEN).code);
  });

  it("code survives replacing the type", () => {
    const problem = { ...converter.convert(Restricted.FORBIDDEN), type: "https://other.example.org/probs/forbidden" };
    expect(problem.code).toBe("kiit.dev:codes:Failed:Restricted:FORBIDDEN");
  });

  it("a single ErrorField keeps its field in errors", () => {
    const problem = converter.convert(Invalid.INVALID_VALUE, Err.onField("firstname", "Missing"));
    expect(problem.detail).toBe("Missing");
    expect(problem.errors).toEqual([ErrorDetail("firstname", "Missing")]);
  });

  it("an ErrorInfo gives one error entry and instance from ref", () => {
    const problem = converter.convert(Invalid.INVALID_VALUE, ErrorInfo("Balance too low", undefined, "job-456"));
    expect(problem.detail).toBe("Balance too low");
    expect(problem.instance).toBe("job-456");
    expect(problem.errors).toEqual([ErrorDetail(undefined, "Balance too low")]);
  });

  it("nested error lists are flattened and a blank list message falls back to the first error", () => {
    const err = ErrorList(
      [ErrorField("a", "", "A is bad"), ErrorList([ErrorField("b", "", "B is bad")], "Inner")],
      "",
    );
    const problem = converter.convert(Invalid.INVALID_VALUE, err);
    expect(problem.detail).toBe("A is bad");
    expect(problem.errors).toEqual([ErrorDetail("a", "A is bad"), ErrorDetail("b", "B is bad")]);
  });

  it("an empty error list has no errors", () => {
    const problem = converter.convert(Invalid.INVALID_VALUE, ErrorList([], "Validation failed"));
    expect(problem.detail).toBe("Validation failed");
    expect(problem.errors).toBeUndefined();
  });

  it("a custom mapper sees every leaf and never a list", () => {
    const seen: string[] = [];
    const err = ErrorList([ErrorField("a", "", "A"), ErrorList([ErrorInfo("B")], "Inner")], "Outer");
    converter.convertCustom(Invalid.INVALID_VALUE, err, (e) => {
      seen.push(e.kind);
      return ErrorDetail(undefined, e.message);
    });
    expect(seen).toEqual(["ErrorField", "ErrorInfo"]);
  });

  it("title is the status title, status is the HTTP code", () => {
    const problem = converter.convert(Restricted.FORBIDDEN);
    expect(problem.title).toBe(Restricted.FORBIDDEN.title);
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
});
